package io.github.immaghzbad.aetherst.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.immaghzbad.aetherst.MainActivity
import io.github.immaghzbad.aetherst.R
import io.github.immaghzbad.aetherst.core.ConnectionController
import io.github.immaghzbad.aetherst.core.DnsMap
import io.github.immaghzbad.aetherst.core.HevTun2SocksEngine
import io.github.immaghzbad.aetherst.core.HevTun2SocksNative
import io.github.immaghzbad.aetherst.core.LocalSocksProxyServer
import io.github.immaghzbad.aetherst.core.RoutingEngine
import io.github.immaghzbad.aetherst.core.SocksTunBridge
import io.github.immaghzbad.aetherst.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.model.ConnectionStatus
import io.github.immaghzbad.aetherst.model.TunnelEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

@Suppress("VpnServicePolicy")
class AetherVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var hevEngine: HevTun2SocksEngine? = null
    private var localBridge: LocalSocksProxyServer? = null
    private var socksBridge: SocksTunBridge? = null
    private var routingEngine: RoutingEngine? = null
    private var activeTunnelEngine: TunnelEngine? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()
    private val activeAttemptId = AtomicLong(0)
    private val commandCounter = AtomicLong(0)
    private var startupJob: Job? = null
    private var statsJob: Job? = null

    companion object {
        const val ACTION_START = "io.github.immaghzbad.aetherst.ACTION_START"
        const val ACTION_STOP = "io.github.immaghzbad.aetherst.ACTION_STOP"
        const val CHANNEL_ID = "aether_vpn_status_v2"
        const val NOTIFICATION_ID = 1001

        fun startVpn(context: Context): Boolean = runCatching {
            val intent = Intent(context, AetherVpnService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
            true
        }.getOrElse {
            LogRepository.e("[VpnService] Start failed: ${it.localizedMessage}")
            false
        }

        fun stopVpn(context: Context): Boolean = runCatching {
            val intent = Intent(context, AetherVpnService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
            true
        }.getOrElse {
            LogRepository.e("[VpnService] Stop failed: ${it.localizedMessage}")
            false
        }

        val serviceState: StateFlow<ConnectionStatus> get() = ConnectionController.status
    }

    private fun getController() = ConnectionController.getInstance(this)

    override fun onCreate() {
        super.onCreate()
        LogRepository.initialize(this)
        createNotificationChannel()
        
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AetherST:VpnWakeLock")

        scope.launch {
            ConnectionController.status.collect { updateNotification() }
        }

        scope.launch {
            AetherConfigRepository.getInstance(this@AetherVpnService).config.collect {
                routingEngine?.clearCache()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAttempt(commandCounter.incrementAndGet())
            }
            ACTION_STOP -> {
                stopVpnService(commandCounter.incrementAndGet())
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        LogRepository.w("[VpnService] VPN revoked by system or other app")
        stopVpnService(commandCounter.incrementAndGet())
        super.onRevoke()
    }

    private fun startAttempt(commandId: Long) {
        startupJob = scope.launch {
            if (commandCounter.get() != commandId) return@launch

            val attemptId = stateMutex.withLock {
                if (commandCounter.get() != commandId) return@launch
                val current = ConnectionController.status.value
                if (current == ConnectionStatus.RUNNING || current == ConnectionStatus.VALIDATING) return@launch
                
                val id = System.currentTimeMillis()
                activeAttemptId.set(id)
                id
            }

            showInitialNotification()
            runCatching { wakeLock?.acquire(24 * 60 * 60 * 1000L) }

            try {
                getController().start()
                
                if (ConnectionController.status.value != ConnectionStatus.RUNNING) {
                    throw IllegalStateException("Core failed to start")
                }

                ensureCurrentAttempt(attemptId)
                val config = AetherConfigRepository.getInstance(this@AetherVpnService).config.value

                LogRepository.i("[VpnService] Core ready, establishing TUN")
                routingEngine = RoutingEngine(config.routingRules)

                if (!establishVpnTun(attemptId)) throw IllegalStateException("TUN establishment failed")
                ensureCurrentAttempt(attemptId)

                val descriptor = vpnInterface ?: throw IllegalStateException("TUN descriptor unavailable")

                val effectiveEngine = if (
                    !config.tunnelAllApps &&
                    config.tunnelEngine == TunnelEngine.HEV_TUN2SOCKS &&
                    (config.blockedPackages.isNotEmpty() || config.routingRules.any { it.mode != io.github.immaghzbad.aetherst.model.RoutingMode.TUNNEL })
                ) {
                    TunnelEngine.SOCKS_TUN_BRIDGE
                } else {
                    config.tunnelEngine
                }
                activeTunnelEngine = effectiveEngine

                if (effectiveEngine == TunnelEngine.HEV_TUN2SOCKS) {
                    if (!HevTun2SocksNative.isAvailable) throw IllegalStateException("HEV Native library not available")
                    
                    hevEngine = HevTun2SocksEngine()
                    localBridge = LocalSocksProxyServer(
                        vpnService = this@AetherVpnService,
                        listenHost = "127.0.0.1",
                        listenPort = 10808,
                        targetHost = config.socksHost,
                        targetPort = config.socksPort.toIntOrNull() ?: 1819,
                        routingEngine = routingEngine!!
                    ).apply { start() }

                    val ok = hevEngine?.start(
                        tunPfd = descriptor,
                        socksAddress = "127.0.0.1",
                        socksPort = 10808,
                        mtu = 1280,
                        attemptId = attemptId
                    ) == true
                    if (!ok) throw IllegalStateException("HEV engine failed to start")
                } else {
                    socksBridge = SocksTunBridge(
                        vpnService = this@AetherVpnService,
                        tunDescriptor = descriptor,
                        socksHost = config.socksHost,
                        socksPort = config.socksPort.toIntOrNull() ?: 1819,
                        mtu = 1280,
                        blockedPackagesProvider = { if (config.tunnelAllApps) emptySet() else config.blockedPackages },
                        routingEngine = routingEngine!!
                    ).apply { start() }
                }

                LogRepository.i("[VpnService] VPN tunnel active")
                startStatsJob()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (activeAttemptId.get() == attemptId && commandCounter.get() == commandId) {
                    rollback(attemptId, throwable.localizedMessage ?: "Startup failed")
                }
            }
        }
    }

    private fun ensureCurrentAttempt(attemptId: Long) {
        if (activeAttemptId.get() != attemptId) throw IllegalStateException("Connection attempt invalidated")
    }

    private fun establishVpnTun(attemptId: Long): Boolean = runCatching {
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val builder = Builder()
            .addAddress("198.18.0.1", 30)
            .addAddress("fd00::1", 128)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addDnsServer("2606:4700:4700::1111")
            .addDnsServer("2001:4860:4860::8888")
            .setMtu(1280)
            .setSession("AetherST Tunnel")
            .setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), pendingFlags))

        val config = AetherConfigRepository.getInstance(this).config.value
        if (config.ipv6Leak && Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
            runCatching { builder.addRoute("::", 0) }
        }

        builder.addDisallowedApplication(packageName)
        if (!config.tunnelAllApps) {
            config.excludedPackages
                .asSequence()
                .filterNot { it == packageName }
                .forEach { excludedPackage ->
                    try {
                        builder.addDisallowedApplication(excludedPackage)
                    } catch (_: PackageManager.NameNotFoundException) {
                        LogRepository.w("[Tun] Ignoring uninstalled package: $excludedPackage")
                    }
                }
        }

        vpnInterface = builder.establish() ?: return false
        LogRepository.i("[Tun] [attempt=$attemptId] Established")
        true
    }.getOrElse {
        LogRepository.e("[Tun] [attempt=$attemptId] Failed: ${it.localizedMessage}")
        false
    }

    private suspend fun rollback(attemptId: Long, reason: String) {
        LogRepository.e("[VpnService] Rollback: $reason")
        stopStatsJob()
        cleanupResources(attemptId)
        getController().stop()
    }

    private fun stopVpnService(commandId: Long) {
        scope.launch {
            val attemptId = activeAttemptId.getAndSet(0)
            startupJob?.cancelAndJoin()
            stopStatsJob()
            
            cleanupResources(attemptId)
            getController().stop()
            
            if (commandCounter.get() == commandId) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun cleanupResources(attemptId: Long) {
        stateMutex.withLock {
            hevEngine?.requestStop()
            hevEngine = null
            localBridge?.stop()
            localBridge = null
            socksBridge?.stop()
            socksBridge = null
            closeVpnInterface(attemptId)
            activeTunnelEngine = null
            DnsMap.clear()
            runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        }
    }

    private fun closeVpnInterface(attemptId: Long) {
        vpnInterface?.let {
            runCatching { it.close() }
            vpnInterface = null
            LogRepository.i("[Tun] [attempt=$attemptId] Closed")
        }
    }

    private fun startStatsJob() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(1000.milliseconds)
                updateTraffic()
            }
        }
    }

    private fun stopStatsJob() {
        statsJob?.cancel()
        statsJob = null
    }

    private fun updateTraffic() {
        if (activeTunnelEngine == TunnelEngine.HEV_TUN2SOCKS) {
            hevEngine?.stats?.value?.let { getController().setTraffic(it.txBytes, it.rxBytes) }
        } else {
            socksBridge?.getStats()?.let { getController().setTraffic(it.txBytes, it.rxBytes) }
        }
    }

    private fun showInitialNotification() {
        val notification = buildNotification("Connecting...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val status = ConnectionController.status.value
        if (status == ConnectionStatus.STOPPED) return
        val text = when (status) {
            ConnectionStatus.RUNNING -> "VPN connected"
            ConnectionStatus.STARTING, ConnectionStatus.VALIDATING -> "Connecting..."
            ConnectionStatus.RECONNECTING -> "Reconnecting..."
            ConnectionStatus.STOPPING -> "Disconnecting..."
            ConnectionStatus.ERROR -> "Connection error"
            else -> ""
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(statusText: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, AetherVpnService::class.java).apply { action = ACTION_STOP }, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AetherST Tunnel")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_stat_aether)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "AetherST Tunnel", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
