package io.github.immaghzbad.aetherst.core

import io.github.immaghzbad.aetherst.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.model.AetherProtocol
import io.github.immaghzbad.aetherst.model.AetherScanMode
import io.github.immaghzbad.aetherst.model.ConnectionMode
import io.github.immaghzbad.aetherst.model.ConnectionStatus
import io.github.immaghzbad.aetherst.model.SessionTraffic
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConnectionController private constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runner = AetherProcessRunner()
    private val mutex = Mutex()
    private val activeAttemptId = AtomicLong(0)
    private val loginCodeChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    private val _isWaitingForCode = MutableStateFlow(false)
    val isWaitingForCode: StateFlow<Boolean> = _isWaitingForCode.asStateFlow()

    private var timerJob: Job? = null
    private var durationSeconds = 0L
    private var isManualTraffic = false
    private var lastManualTx = 0L
    private var lastManualRx = 0L
    private var socksProxy: LocalSocksProxyServer? = null
    private var httpProxy: LocalHttpProxyServer? = null
    private var routingEngine: RoutingEngine? = null
    private var openVpnConnector: OpenVpnConnector? = null

    companion object {
        @Volatile
        private var INSTANCE: ConnectionController? = null

        private val _status = MutableStateFlow(ConnectionStatus.STOPPED)
        val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

        private val _elapsedSeconds = MutableStateFlow(0L)
        val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

        private val _sessionTraffic = MutableStateFlow(SessionTraffic())
        val sessionTraffic: StateFlow<SessionTraffic> = _sessionTraffic.asStateFlow()

        fun getInstance(): ConnectionController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionController().also { INSTANCE = it }
            }
        }

        fun updateStatus(status: ConnectionStatus) {
            _status.value = status
        }
    }

    init {
        scope.launch {
            runner.connectionStatus.collect { coreStatus ->
                handleCoreStatus(coreStatus)
            }
        }
    }

    fun submitLoginCode(code: String) {
        _isWaitingForCode.value = false
        loginCodeChannel.trySend(code)
    }

    suspend fun start() = mutex.withLock {
        if (_status.value == ConnectionStatus.RUNNING || _status.value == ConnectionStatus.VALIDATING) {
            return@withLock
        }

        val attemptId = System.currentTimeMillis()
        activeAttemptId.set(attemptId)
        _status.value = ConnectionStatus.STARTING

        try {
            val config = AetherConfigRepository.getInstance().config.value
            val bindHost = if (config.shareHotspot) "0.0.0.0" else "127.0.0.1"
            val bindAddress = "$bindHost:${config.socksPort}"

            isManualTraffic = false

            LogRepository.i("[Controller] Starting core at $bindAddress")
            runner.start(config, bindAddress, onCodeRequired = {
                _isWaitingForCode.value = true
            }, inputProvider = {
                loginCodeChannel.receive()
            })

            val proxyPort = config.socksPort.toIntOrNull() ?: 1819
            val startupTimeoutSeconds = when (config.scanMode) {
                AetherScanMode.TURBO -> 90L
                AetherScanMode.BALANCED -> 120L
                AetherScanMode.THOROUGH -> 180L
                AetherScanMode.STEALTH -> 240L
                AetherScanMode.IRONCLAD -> 240L
            } + config.validateSecs.coerceAtLeast(0)

            val ready = withTimeoutOrNull(startupTimeoutSeconds.seconds) {
                while (currentCoroutineContext().isActive) {
                    if (runner.connectionStatus.value == ConnectionStatus.RUNNING) return@withTimeoutOrNull true
                    if (isPortListening("127.0.0.1", proxyPort)) return@withTimeoutOrNull true
                    delay(250.milliseconds)
                }
                false
            } ?: false

            if (!ready) {
                throw IllegalStateException("Core startup timed out after ${startupTimeoutSeconds}s")
            }

            if (!verifyPortListening("127.0.0.1", proxyPort)) {
                throw IllegalStateException("Proxy port is not listening")
            }

            _status.value = ConnectionStatus.RUNNING

            // When OpenVPN is selected, skip AetherST's own TUN/bridge setup.
            // OpenVPN creates its own TUN adapter and tunnels the system itself.
            // We only need the WireGuard core's SOCKS/HTTP proxy to be running.
            if (config.protocol == AetherProtocol.OPENVPN) {
                val httpPort = config.httpPort.toIntOrNull() ?: 1820
                LogRepository.i("[Controller] OpenVPN Hybrid mode — skipping AetherST TUN (OpenVPN will create its own)")
                LogRepository.i("[Controller] WireGuard proxy ready at SOCKS:$proxyPort / HTTP:$httpPort")

                startTimer()
                LogRepository.i("[Controller] Core is active — starting OpenVPN connector")

                val cfgPath = config.openVpnConfigPath
                if (cfgPath.isBlank() || !File(cfgPath).exists()) {
                    throw IllegalStateException("OpenVPN config not set (Settings -> OpenVPN Config)")
                }
                val connector = OpenVpnConnector(cfgPath, "127.0.0.1", proxyPort, httpPort, config.openVpnUsername, config.openVpnPassword)
                if (!connector.start()) {
                    throw IllegalStateException("Failed to start OpenVPN hybrid tunnel")
                }
                openVpnConnector = connector
            } else if (config.connectionMode == ConnectionMode.PROXY_ONLY) {
                val engine = RoutingEngine(config.routingRules)
                routingEngine = engine
                LogRepository.i("[Controller] HTTP proxy served natively by core (--http-proxy :${config.httpPort.toIntOrNull() ?: 1820})")
                val httpPort = config.httpPort.toIntOrNull() ?: 1820
                if (!verifyPortListening("127.0.0.1", httpPort)) {
                    LogRepository.w("[Controller] Core HTTP proxy port $httpPort is not listening yet")
                }

                startTimer()
                LogRepository.i("[Controller] Core is active and validated")
            } else if (config.connectionMode == ConnectionMode.TUNNEL) {
                val engine = RoutingEngine(config.routingRules)
                routingEngine = engine
                socksProxy = LocalSocksProxyServer(
                    listenHost = "127.0.0.1",
                    listenPort = 10808,
                    targetHost = "127.0.0.1",
                    targetPort = proxyPort,
                    routingEngine = engine
                ).apply { start() }
                LogRepository.i("[Controller] Local SOCKS bridge listening on 127.0.0.1:10808")

                TunHelper.start(proxyPort)
                val tunUp = withTimeoutOrNull(30.seconds) {
                    while (currentCoroutineContext().isActive) {
                        if (isPortListening("127.0.0.1", 10808)) return@withTimeoutOrNull true
                        delay(500.milliseconds)
                    }
                    false
                } ?: false
                LogRepository.i("[Controller] TUN bridge status: ${if (tunUp) "up" else "timeout waiting"}")
                if (!tunUp) {
                    LogRepository.w("[Controller] SOCKS bridge did not come up in time")
                }

                startTimer()
                LogRepository.i("[Controller] Core is active and validated")
            }
        } catch (e: Exception) {
            LogRepository.e("[Controller] Startup failed: ${e.localizedMessage}")
            cleanup(attemptId)
            _status.value = ConnectionStatus.ERROR
        }
    }

    suspend fun stop() = mutex.withLock {
        if (_status.value == ConnectionStatus.STOPPED) {
            return@withLock
        }

        val attemptId = activeAttemptId.get()
        _status.value = ConnectionStatus.STOPPING
        LogRepository.i("[Controller] Stopping core")

        stopTimer()
        cleanup(attemptId)

        _status.value = ConnectionStatus.STOPPED
        LogRepository.i("[Controller] Core stopped")
    }

    private suspend fun cleanup(attemptId: Long) {
        if (activeAttemptId.get() == attemptId) {
            activeAttemptId.set(0)
        }
        runner.stop()
        socksProxy?.stop()
        socksProxy = null
        httpProxy?.stop()
        httpProxy = null
        openVpnConnector?.stop()
        openVpnConnector = null
        TunHelper.stop()
        routingEngine = null
        _isWaitingForCode.value = false
        delay(500.milliseconds)
    }

    private fun handleCoreStatus(coreStatus: ConnectionStatus) {
        val current = _status.value
        if (current == ConnectionStatus.STOPPED || current == ConnectionStatus.STOPPING) return

        when (coreStatus) {
            ConnectionStatus.ERROR -> {
                LogRepository.e("[Controller] Core reported error")
                _status.value = ConnectionStatus.ERROR
                stopTimer()
            }
            ConnectionStatus.STOPPED -> {
                if (current == ConnectionStatus.RUNNING || current == ConnectionStatus.RECONNECTING) {
                    LogRepository.w("[Controller] Core stopped unexpectedly")
                    _status.value = ConnectionStatus.ERROR
                    stopTimer()
                }
            }
            ConnectionStatus.RECONNECTING -> {
                if (current == ConnectionStatus.RUNNING) {
                    _status.value = ConnectionStatus.RECONNECTING
                }
            }
            ConnectionStatus.RUNNING -> {
                if (current == ConnectionStatus.RECONNECTING || current == ConnectionStatus.STARTING || current == ConnectionStatus.VALIDATING) {
                    _status.value = ConnectionStatus.RUNNING
                    startTimer()
                }
            }
            else -> {}
        }
    }

    private suspend fun isPortListening(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 300)
                    true
                }
            }.getOrDefault(false)
        }
    }

    private suspend fun verifyPortListening(host: String, port: Int): Boolean {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (isPortListening(host, port)) return true
            delay(200.milliseconds)
        }
        return false
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        durationSeconds = 0
        _elapsedSeconds.value = 0
        timerJob = scope.launch {
            while (isActive) {
                delay(1000.milliseconds)
                durationSeconds++
                _elapsedSeconds.value = durationSeconds
                if (!isManualTraffic) {
                    val socks = socksProxy?.getStats()
                    val http = httpProxy?.getStats()
                    if (socks != null || http != null) {
                        val tx = (socks?.txBytes ?: 0) + (http?.txBytes ?: 0)
                        val rx = (socks?.rxBytes ?: 0) + (http?.rxBytes ?: 0)
                        _sessionTraffic.value = SessionTraffic(tx, rx)
                    }
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        durationSeconds = 0
        _elapsedSeconds.value = 0
        _sessionTraffic.value = SessionTraffic()
        isManualTraffic = false
        lastManualTx = 0
        lastManualRx = 0
    }

    fun setTraffic(tx: Long, rx: Long) {
        if (tx > lastManualTx || rx > lastManualRx || (tx == 0L && rx == 0L && !isManualTraffic)) {
            isManualTraffic = true
            lastManualTx = tx.coerceAtLeast(lastManualTx)
            lastManualRx = rx.coerceAtLeast(lastManualRx)
            _sessionTraffic.value = SessionTraffic(lastManualTx, lastManualRx)
        }
    }
}