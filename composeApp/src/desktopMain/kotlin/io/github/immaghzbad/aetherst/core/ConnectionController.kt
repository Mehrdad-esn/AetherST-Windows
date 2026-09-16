package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.ActiveProxyProvider
import io.github.immaghzbad.aetherst.shared.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import io.github.immaghzbad.aetherst.shared.model.*
import io.github.immaghzbad.aetherst.core.CloakController
import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSettings
import io.github.immaghzbad.aetherst.platform.getTrafficProvider
import io.github.immaghzbad.aetherst.platform.getSystemUtils
import io.github.immaghzbad.aetherst.shared.platform.Bridge
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

actual object ConnectionController {
    private val _status = MutableStateFlow(ConnectionStatus.STOPPED)
    actual val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    actual val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _sessionTraffic = MutableStateFlow(SessionTraffic())
    actual val sessionTraffic: StateFlow<SessionTraffic> = _sessionTraffic.asStateFlow()

    actual fun markStatus(status: ConnectionStatus) {
        _status.value = status
    }

    @Volatile
    private var INSTANCE: ControllerImpl? = null

    actual fun getInstance(context: PlatformContext) {
        if (INSTANCE == null) {
            synchronized(this) {
                if (INSTANCE == null) INSTANCE = ControllerImpl(context)
            }
        }
    }

    fun getImpl(context: PlatformContext): ControllerImpl {
        getInstance(context)
        return INSTANCE!!
    }

    class ControllerImpl(private val context: PlatformContext) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val runner = AetherProcessRunner(context)
        private val trafficProvider = getTrafficProvider(context)

        private var timerJob: Job? = null
        private var baseTx = 0L
        private var baseRx = 0L
        private var prevTx = 0L
        private var prevRx = 0L
        @Volatile private var socksProxy: LocalSocksProxyServer? = null
        @Volatile private var httpProxy: LocalHttpProxyServer? = null
        @Volatile private var dnsServer: LocalDnsServer? = null
        @Volatile private var tunnelModeStarted = false
        private var routingEngine: RoutingEngine? = null
        private var statusJob: Job? = null
        private var modeJob: Job? = null
        @Volatile private var openVpnConnector: OpenVpnConnector? = null
        private var openVpnJob: Job? = null
        @Volatile private var psiphonConnector: PsiphonConnector? = null
        private var psiphonJob: Job? = null
        @Volatile private var psiphonReady = false
        /** While true, a runner RUNNING is held as VALIDATING: UI stays non-green until the Psiphon chain is fully tunneled. */
        @Volatile private var psiphonWait = false
        /** 1.7.1: same gate for OpenVPN Hybrid — green UI only after openvpn itself handshakes, not when the underlying WG proxy turns RUNNING. */
        @Volatile private var openVpnWait = false
        @Volatile private var openVpnReady = false
        /** 1.7.1: cumulative counters from openvpn management (`status`); apps use OpenVPN's own TUN here. */
        @Volatile private var ovpnRx = -1L
        @Volatile private var ovpnTx = -1L
        private var prevOvpnRx = 0L
        private var prevOvpnTx = 0L
        private var ovpnTrafficSeen = false

        init {
            scope.launch {
                Bridge.statusOverride.collect { s ->
                    if (s != null) {
                        _status.value = s
                        if (s == ConnectionStatus.STOPPED || s == ConnectionStatus.ERROR) {
                            stopTimer()
                        }
                    }
                }
            }
            scope.launch {
                Bridge.trafficOverride.collect {
                    if (it != null) _sessionTraffic.value = it
                }
            }
            scope.launch {
                Bridge.elapsedOverride.collect {
                    if (it != null) _elapsedSeconds.value = it
                }
            }
        }

        fun start() {
            LogRepository.i("Initializing connection process...", "AetherSystem")
            if (statusJob == null) {
                statusJob = scope.launch {
                    runner.connectionStatus.collect {
                        // Gate: while a chain is pending, never surface RUNNING
                        // (green/connected UI) — hold at VALIDATING until the chain
                        // (Psiphon helper or OpenVPN handshake) is fully tunneled.
                        val chainPending = (psiphonWait && !psiphonReady) || (openVpnWait && !openVpnReady)
                        _status.value = if (it == ConnectionStatus.RUNNING && chainPending) ConnectionStatus.VALIDATING else it
                    }
                }
            }
            val config = AetherConfigRepository.getInstance(getSettings(context)).config.value
            var effectiveConfig = config
            try {
                if (CloakController.isSupported(config)) {
                    val started = try { CloakController.start(context, config) } catch (_: Throwable) { false }
                    if (started && CloakController.isRunning()) {
                        effectiveConfig = config.copy(peer = CloakController.getEffectivePeer(config))
                        LogRepository.i("[Controller] Cloak active, routing MASQUE via ${effectiveConfig.peer}", "Cloak")
                    } else if (started) {
                        LogRepository.w("[Controller] Cloak start reported success but not running, fallback to direct peer", "Cloak")
                    }
                }
            } catch (_: Throwable) {}
            baseTx = trafficProvider.getTxBytes()
            baseRx = trafficProvider.getRxBytes()

            getSystemUtils(context).clearSystemProxy()

            val coreSocksPort = effectiveConfig.socksPort.toIntOrNull() ?: 1819
            val coreHttpPort = effectiveConfig.httpPort.toIntOrNull() ?: 1820
            val psiphonActive = effectiveConfig.isPsiphonActive() && effectiveConfig.protocol != AetherProtocol.OPENVPN
            val psiphonOnly = psiphonActive && effectiveConfig.psiphonOnly
            // Psiphon-first order (engine v2 --upstream): helper dials direct first,
            // then the core starts through it. Otherwise the core starts now.
            val psiphonFirst = psiphonActive && !psiphonOnly &&
                effectiveConfig.psiphonMasqueOrder.lowercase().trim() == "psiphon_first"
            psiphonReady = false
            psiphonWait = psiphonActive
            // 1.7.1: OpenVPN Hybrid = WG proxy first, openvpn handshake second.
            // Hold the UI at VALIDATING until openvpn itself reports CONNECTED.
            val isOpenVpnHybrid = effectiveConfig.protocol == AetherProtocol.OPENVPN
            openVpnWait = isOpenVpnHybrid
            openVpnReady = false
            resetOpenVpnTraffic()

            if (psiphonOnly) {
                LogRepository.i("[Controller] Psiphon-only mode — Aether core not started")
            } else if (!psiphonFirst) {
                runner.start(
                    config = effectiveConfig,
                    bindAddress = "127.0.0.1:${effectiveConfig.socksPort}"
                )
            } else {
                LogRepository.i("[Controller] Psiphon-first order — core starts after helper is stable")
            }

            routingEngine = RoutingEngine(effectiveConfig.routingRules)
            startCountingRelays(coreSocksPort, coreSocksPort)
            if (isOpenVpnHybrid) {
                LogRepository.i("[Controller] OpenVPN Hybrid mode — skipping AetherST TUN/DNS/system-proxy (OpenVPN creates its own TUN)")
            }
            if (effectiveConfig.isPsiphonActive() && effectiveConfig.protocol == AetherProtocol.OPENVPN) {
                LogRepository.w("[Controller] Psiphon toggle is ON but the chain is skipped in OpenVPN Hybrid mode — tunnel exits via OpenVPN/core, not Psiphon")
            }
            if (psiphonOnly) {
                val dnsUpstream = (if (effectiveConfig.dnsEnabled) effectiveConfig.dnsList else "").ifEmpty { "1.1.1.1,1.0.0.1" }
                LogRepository.w("[Controller] Psiphon-only: no core DNS relay, using system DNS $dnsUpstream without relay")
                try { getSystemUtils(context).setSystemDns(dnsUpstream) } catch (_: Throwable) {}
            }
            if (!isOpenVpnHybrid && !psiphonOnly && (effectiveConfig.connectionMode == ConnectionMode.TUNNEL || effectiveConfig.connectionMode == ConnectionMode.SYSTEM_PROXY)) {
                val dnsUpstream = (if (effectiveConfig.dnsEnabled) effectiveConfig.dnsList else "").ifEmpty { "1.1.1.1,1.0.0.1" }
                val tmpDns = LocalDnsServer(listenHost = "127.0.0.1", listenPort = 53, socksHost = "127.0.0.1", socksPort = coreSocksPort, upstreamList = dnsUpstream)
                tmpDns.start()
                if (tmpDns.isRunning()) {
                    dnsServer = tmpDns
                    try { getSystemUtils(context).setSystemDns("127.0.0.1") } catch (_: Throwable) {}
                } else {
                    LogRepository.w("[Controller] DNS relay failed to bind 53 (needs admin), using system DNS $dnsUpstream without relay")
                    try { getSystemUtils(context).setSystemDns(dnsUpstream) } catch (_: Throwable) {}
                }
            }

            modeJob?.cancel()
            openVpnJob?.cancel()
            psiphonJob?.cancel()
            if (psiphonActive) {
                if (effectiveConfig.psiphonMasqueOrder.lowercase().trim() == "psiphon_first") {
                    LogRepository.i("[Controller] Psiphon order=psiphon-first: helper dials direct, core chains through it (--upstream)")
                } else {
                    LogRepository.i("[Controller] Psiphon order=${effectiveConfig.psiphonMasqueOrder}: core first, helper chains over it (no racing on desktop)")
                }
                psiphonJob = scope.launch(Dispatchers.IO) {
                    runPsiphonChain(effectiveConfig, coreSocksPort, coreHttpPort, psiphonOnly)
                }
            }
            if (isOpenVpnHybrid) {
                val cfgPath = effectiveConfig.openVpnConfigPath
                if (cfgPath.isBlank() || !File(cfgPath).exists()) {
                    LogRepository.e("[Controller] OpenVPN config not set (Settings -> OpenVPN Config)")
                    _status.value = ConnectionStatus.ERROR
                } else {
                    val httpPort = effectiveConfig.httpPort.toIntOrNull() ?: 1820
                    openVpnJob = scope.launch(Dispatchers.IO) {
                        // NOTE: observe the RAW runner status here, not the gated public `status`:
                        // the gate withholds RUNNING until this connector finishes
                        // (self-dependency = deadlock), same pattern as the Psiphon chain.
                        val coreOk = runner.connectionStatus.first {
                            it == ConnectionStatus.RUNNING || it == ConnectionStatus.ERROR || it == ConnectionStatus.STOPPED
                        } == ConnectionStatus.RUNNING
                        if (!coreOk) {
                            openVpnWait = false
                            return@launch
                        }
                        LogRepository.i("[Controller] Core proxy ready — starting OpenVPN connector")
                        val connector = OpenVpnConnector(
                            context, cfgPath, "127.0.0.1", coreSocksPort, httpPort,
                            effectiveConfig.openVpnUsername, effectiveConfig.openVpnPassword
                        )
                        openVpnConnector = connector
                        // 1.7.1: live volume/speed from openvpn management counters.
                        connector.onTraffic = { rx, tx -> onOpenVpnTraffic(rx, tx) }
                        val ok = connector.start()
                        // Stop() may have been pressed during the blocking handshake:
                        // never surface CONNECTED for a torn-down session.
                        if (!isActive || openVpnConnector !== connector) {
                            runCatching { connector.stop() }
                            return@launch
                        }
                        if (!ok) {
                            openVpnWait = false
                            LogRepository.e("[Controller] OpenVPN hybrid tunnel failed to start")
                            _status.value = ConnectionStatus.ERROR
                        } else {
                            // Release the gate: openvpn handshook itself, UI may go green.
                            openVpnReady = true
                            openVpnWait = false
                            _status.value = ConnectionStatus.RUNNING
                            LogRepository.i("[Controller] OpenVPN hybrid tunnel CONNECTED")
                        }
                    }
                }
            } else if (effectiveConfig.connectionMode == ConnectionMode.TUNNEL) {
                modeJob = scope.launch {
                    status.collect { s ->
                        if (s == ConnectionStatus.RUNNING && (!psiphonActive || psiphonReady)) {
                            delay(500.milliseconds)
                            startTunnelMode(effectiveConfig)
                        }
                    }
                }
            } else if (effectiveConfig.connectionMode == ConnectionMode.SYSTEM_PROXY) {
                modeJob = scope.launch {
                    status.collect { s ->
                        if (s == ConnectionStatus.RUNNING && (!psiphonActive || psiphonReady)) {
                            delay(500.milliseconds)
                            getSystemUtils(context).setSystemProxy("127.0.0.1", 10809)
                        }
                    }
                }
            }

            startTimer()
        }

        private fun startCountingRelays(socksTarget: Int, httpTarget: Int) {
            stopCountingRelays()
            if (routingEngine == null) return
            socksProxy = LocalSocksProxyServer(
                listenHost = "127.0.0.1",
                listenPort = 10808,
                targetHost = "127.0.0.1",
                targetPort = socksTarget,
                routingEngine = routingEngine!!
            ).apply { start() }
            httpProxy = LocalHttpProxyServer(
                listenHost = "127.0.0.1",
                listenPort = 10809,
                targetHost = "127.0.0.1",
                targetPort = httpTarget,
                routingEngine = routingEngine!!
            ).apply { start() }
            LogRepository.i("[Controller] Counting proxies started (socks=10808->$socksTarget, http=10809->$httpTarget)")
        }

        private fun stopCountingRelays() {
            socksProxy?.stop()
            socksProxy = null
            httpProxy?.stop()
            httpProxy = null
        }

        private fun findFreePort(): Int {
            return try {
                java.net.ServerSocket(0).use { it.localPort }
            } catch (_: Exception) { 3080 }
        }

        private suspend fun runPsiphonChain(config: AetherConfig, coreSocksPort: Int, coreHttpPort: Int, only: Boolean) {
            val requestedSocks = config.psiphonSocksPort.toIntOrNull() ?: 3080
            val socksPort = if (requestedSocks == coreSocksPort || requestedSocks == coreHttpPort) findFreePort() else requestedSocks
            var httpPort = socksPort + 1
            if (httpPort == coreSocksPort || httpPort == coreHttpPort || httpPort == socksPort) httpPort = findFreePort()
            // User's own upstream proxy (if configured) is used when the helper dials direct.
            val userUpstream = config.upstreamProxy.takeIf { config.upstreamProxyEnabled && it.isNotBlank() }

            if (only) {
                return runPsiphonDirect(config, socksPort, httpPort, upstream = userUpstream)
            }
            if (config.psiphonMasqueOrder.lowercase().trim() == "psiphon_first") {
                return runPsiphonFirst(config, coreSocksPort, socksPort, httpPort, userUpstream)
            }
            // NOTE: observe the RAW runner status here, not the gated public `status`:
            // the gate withholds RUNNING until this chain completes (self-dependency = deadlock).
            val coreOk = withTimeoutOrNull(180.seconds) {
                runner.connectionStatus.first { it == ConnectionStatus.RUNNING || it == ConnectionStatus.ERROR || it == ConnectionStatus.STOPPED } == ConnectionStatus.RUNNING
            } ?: false
            if (!coreOk) {
                if (config.psiphonChainMode == PsiphonChainMode.FALLBACK) {
                    LogRepository.w("[Controller] Core failed, FALLBACK -> psiphon-first chain")
                    runner.stop()
                    return runPsiphonFirst(config, coreSocksPort, socksPort, httpPort, userUpstream)
                }
                LogRepository.e("[Controller] Core failed before Psiphon chain could start - aborting")
                _status.value = ConnectionStatus.ERROR
                return
            }
            return runPsiphonDirect(config, socksPort, httpPort, upstream = "socks5://127.0.0.1:$coreSocksPort")
        }

        /** Psiphon-first: helper dials direct, then the core starts through it (--upstream). */
        private suspend fun runPsiphonFirst(config: AetherConfig, coreSocksPort: Int, socksPort: Int, httpPort: Int, userUpstream: String?) {
            val connector = startStableHelper(config, socksPort, httpPort, userUpstream) ?: return
            ActiveProxyProvider.psiphonProxyUrl = connector.getUpstreamProxy()
            connector.addRouteBypassForDirectMode()
            LogRepository.i("[Controller] Psiphon direct ready, starting core through ${connector.getUpstreamProxy()}")
            runner.start(
                config = config.copy(upstreamProxyEnabled = true, upstreamProxy = connector.getUpstreamProxy()),
                bindAddress = "127.0.0.1:$coreSocksPort"
            )
            // NOTE: raw runner status (see above) — the gated `status` would deadlock here.
            val coreOk = withTimeoutOrNull(180.seconds) {
                runner.connectionStatus.first { it == ConnectionStatus.RUNNING || it == ConnectionStatus.ERROR || it == ConnectionStatus.STOPPED } == ConnectionStatus.RUNNING
            } ?: false
            if (!coreOk) {
                LogRepository.e("[Controller] Core failed via Psiphon-first chain - aborting")
                connector.stop()
                psiphonConnector = null
                ActiveProxyProvider.psiphonProxyUrl = null
                _status.value = ConnectionStatus.ERROR
                return
            }
            // Relays already target the core, which now dials through the helper. Nothing to repoint.
            psiphonReady = true
            // Release the gate: chain is fully tunneled now, UI may go green.
            psiphonWait = false
            _status.value = ConnectionStatus.RUNNING
            LogRepository.i("[Controller] Psiphon-first chain READY (apps -> core -> helper -> internet)")
        }

        /** Starts the helper and waits until it is connected and stable; null on failure (status already ERROR). */
        private suspend fun startStableHelper(config: AetherConfig, socksPort: Int, httpPort: Int, upstream: String?): PsiphonConnector? {
            val connector = PsiphonConnector(
                context, socksPort, httpPort, config.psiphonEgressRegion, upstream
            ) { regions ->
                runCatching {
                    AetherConfigRepository.getInstance(getSettings(context)).cacheEgressRegions(regions)
                }
            }
            psiphonConnector = connector
            if (!connector.start()) {
                LogRepository.e("[Controller] Psiphon helper failed to start")
                psiphonConnector = null
                _status.value = ConnectionStatus.ERROR
                return null
            }
            var waited = 0
            while (waited < 30 && !connector.isConnected()) {
                delay(1000.milliseconds)
                waited++
            }
            if (!connector.isConnected()) {
                LogRepository.e("[Controller] Psiphon not connected in 30s - aborting chain")
                connector.stop()
                psiphonConnector = null
                _status.value = ConnectionStatus.ERROR
                return null
            }
            var stable = 0
            while (stable < 25 && !connector.stableFor(10000)) {
                delay(1000.milliseconds)
                stable++
            }
            if (!connector.stableFor(10000)) {
                LogRepository.e("[Controller] Psiphon not stable - aborting chain")
                connector.stop()
                psiphonConnector = null
                _status.value = ConnectionStatus.ERROR
                return null
            }
            return connector
        }

        /**
         * Moves the local DNS relay (port 53) onto the helper SOCKS so system DNS
         * exits via Psiphon too. No-op when no relay is running (e.g. psiphon-only).
         * Rolls back to the previous relay if rebinding fails.
         */
        private fun repointDnsToHelper(config: AetherConfig, helperSocksPort: Int) {
            val old = dnsServer ?: return
            val dnsUpstream = (if (config.dnsEnabled) config.dnsList else "").ifEmpty { "1.1.1.1,1.0.0.1" }
            old.stop()
            val relay = LocalDnsServer(
                listenHost = "127.0.0.1", listenPort = 53,
                socksHost = "127.0.0.1", socksPort = helperSocksPort,
                upstreamList = dnsUpstream
            )
            relay.start()
            if (relay.isRunning()) {
                dnsServer = relay
                LogRepository.i("[Controller] DNS relay repointed to Psiphon helper (socks=$helperSocksPort)")
            } else {
                LogRepository.w("[Controller] DNS repoint to helper failed, restoring previous relay")
                old.start()
                dnsServer = if (old.isRunning()) old else null
            }
        }

        private suspend fun runPsiphonDirect(config: AetherConfig, socksPort: Int, httpPort: Int, upstream: String?) {
            val connector = startStableHelper(config, socksPort, httpPort, upstream) ?: return
            ActiveProxyProvider.psiphonProxyUrl = connector.getUpstreamProxy()
            // 1.7.3: both counting relays chain via SOCKS; the HTTP relay speaks
            // SOCKS upstream, so it must target the helper SOCKS port (not HTTP).
            startCountingRelays(connector.activeSocksPort(), connector.activeSocksPort())
            if (upstream == null) {
                connector.addRouteBypassForDirectMode()
            }
            psiphonReady = true
            repointDnsToHelper(config, connector.activeSocksPort())
            // Release the gate: chain is fully tunneled now, UI may go green.
            psiphonWait = false
            if (_status.value != ConnectionStatus.RUNNING) _status.value = ConnectionStatus.RUNNING
            LogRepository.i("[Controller] Psiphon chain READY via ${connector.getUpstreamProxy()} (upstream=${upstream ?: "direct"})")
        }

        private fun startTunnelMode(config: AetherConfig) {
            if (tunnelModeStarted) return
            tunnelModeStarted = true

            if (socksProxy == null) {
                val coreSocksPort = config.socksPort.toIntOrNull() ?: 1819
                if (routingEngine == null) routingEngine = RoutingEngine(config.routingRules)
                socksProxy = LocalSocksProxyServer(
                    listenHost = "127.0.0.1",
                    listenPort = 10808,
                    targetHost = "127.0.0.1",
                    targetPort = coreSocksPort,
                    routingEngine = routingEngine!!
                ).apply { start() }
            }
            LogRepository.i("[Controller] Local SOCKS bridge listening on 127.0.0.1:10808")

            TunHelper.start(config.socksPort.toIntOrNull() ?: 1819, config.mtu)
            LogRepository.i("[Controller] TUN helper started mtu=${config.mtu}")
        }

        private val stopLock = Any()

        fun stop() {
            synchronized(stopLock) {
                openVpnJob?.cancel()
                openVpnJob = null
                runCatching { openVpnConnector?.stop() }
                openVpnConnector = null
                psiphonJob?.cancel()
                psiphonJob = null
                runCatching { psiphonConnector?.stop() }
                psiphonConnector = null
                psiphonReady = false
                psiphonWait = false
                openVpnWait = false
                openVpnReady = false
                resetOpenVpnTraffic()
                ActiveProxyProvider.psiphonProxyUrl = null
                runner.stop()
                stopTimer()
                modeJob?.cancel()
                modeJob = null
                getSystemUtils(context).clearSystemProxy()
                try { getSystemUtils(context).clearSystemDns() } catch (_: Throwable) {}
                dnsServer?.stop()
                dnsServer = null
                socksProxy?.stop()
                socksProxy = null
                httpProxy?.stop()
                httpProxy = null
                tunnelModeStarted = false
                TunHelper.stop()
                try { CloakController.stop() } catch (_: Throwable) {}
                routingEngine = null
                statusJob?.cancel()
                statusJob = null
            }
        }

        private fun startTimer() {
            timerJob?.cancel()
            _elapsedSeconds.value = 0
            prevTx = 0L
            prevRx = 0L
            timerJob = scope.launch {
                var seconds = 0L
                while (isActive) {
                    delay(1000.milliseconds)
                    seconds++
                    _elapsedSeconds.value = seconds
                    updateTraffic()
                }
            }
        }

        private fun stopTimer() {
            timerJob?.cancel()
            _elapsedSeconds.value = 0L
            _sessionTraffic.value = SessionTraffic()
        }

        private fun onOpenVpnTraffic(rx: Long, tx: Long) {
            ovpnRx = rx
            ovpnTx = tx
        }

        private fun resetOpenVpnTraffic() {
            ovpnRx = -1L
            ovpnTx = -1L
            prevOvpnRx = 0L
            prevOvpnTx = 0L
            ovpnTrafficSeen = false
        }

        private fun updateTraffic() {
            // 1.7.1 OpenVPN Hybrid: apps ride OpenVPN's own TUN, bypassing the counting
            // relays (which would sit at 0B). Drive volume/speed from the openvpn
            // management counters instead; speeds are per-second deltas like the rest.
            if (openVpnConnector != null && ovpnRx >= 0 && ovpnTx >= 0) {
                if (!ovpnTrafficSeen) {
                    ovpnTrafficSeen = true
                    prevOvpnRx = ovpnRx
                    prevOvpnTx = ovpnTx
                }
                val downloadSpeed = (ovpnRx - prevOvpnRx).coerceAtLeast(0L).toDouble()
                val uploadSpeed = (ovpnTx - prevOvpnTx).coerceAtLeast(0L).toDouble()
                prevOvpnRx = ovpnRx
                prevOvpnTx = ovpnTx
                _sessionTraffic.value = SessionTraffic(
                    uploadedBytes = ovpnTx,
                    downloadedBytes = ovpnRx,
                    uploadSpeedBps = uploadSpeed,
                    downloadSpeedBps = downloadSpeed
                )
                return
            }
            val socksStats = socksProxy?.getStats()
            val httpStats = httpProxy?.getStats()
            val hasCounting = socksStats != null || httpStats != null
            if (hasCounting) {
                val totalTx = (socksStats?.txBytes ?: 0L) + (httpStats?.txBytes ?: 0L)
                val totalRx = (socksStats?.rxBytes ?: 0L) + (httpStats?.rxBytes ?: 0L)
                val uploadSpeed = (totalTx - prevTx).coerceAtLeast(0L).toDouble()
                val downloadSpeed = (totalRx - prevRx).coerceAtLeast(0L).toDouble()
                prevTx = totalTx
                prevRx = totalRx
                _sessionTraffic.value = SessionTraffic(
                    uploadedBytes = totalTx,
                    downloadedBytes = totalRx,
                    uploadSpeedBps = uploadSpeed,
                    downloadSpeedBps = downloadSpeed
                )
                return
            }
            val currentTx = trafficProvider.getTxBytes()
            val currentRx = trafficProvider.getRxBytes()
            val totalTx = (currentTx - baseTx).coerceAtLeast(0L)
            val totalRx = (currentRx - baseRx).coerceAtLeast(0L)
            val uploadSpeed = (totalTx - prevTx).coerceAtLeast(0L).toDouble()
            val downloadSpeed = (totalRx - prevRx).coerceAtLeast(0L).toDouble()
            prevTx = totalTx
            prevRx = totalRx
            _sessionTraffic.value = SessionTraffic(
                uploadedBytes = totalTx,
                downloadedBytes = totalRx,
                uploadSpeedBps = uploadSpeed,
                downloadSpeedBps = downloadSpeed
            )
        }
    }
}
