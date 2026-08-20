package io.github.immaghzbad.aetherst.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.immaghzbad.aetherst.MainActivity
import io.github.immaghzbad.aetherst.R
import io.github.immaghzbad.aetherst.core.ConnectionController
import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.model.ConnectionStatus
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

class AetherProxyService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandCounter = AtomicLong(0)

    companion object {
        const val ACTION_START = "io.github.immaghzbad.aetherst.PROXY_START"
        const val ACTION_STOP = "io.github.immaghzbad.aetherst.PROXY_STOP"
        const val CHANNEL_ID = "aether_proxy_status"
        const val NOTIFICATION_ID = 1002

        fun startProxy(context: Context): Boolean = runCatching {
            val intent = Intent(context, AetherProxyService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
            true
        }.getOrElse {
            LogRepository.e("[ProxyService] Start failed: ${it.localizedMessage}")
            false
        }

        fun stopProxy(context: Context): Boolean = runCatching {
            val intent = Intent(context, AetherProxyService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
            true
        }.getOrElse {
            LogRepository.e("[ProxyService] Stop failed: ${it.localizedMessage}")
            false
        }

    }

    private fun getController() = ConnectionController.getInstance(this)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AetherST:ProxyWakeLock")

        scope.launch {
            ConnectionController.status.collect { updateNotification() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAttempt(commandCounter.incrementAndGet())
            ACTION_STOP -> stopProxyService(commandCounter.incrementAndGet())
        }
        return START_STICKY
    }

    private fun startAttempt(commandId: Long) {
        scope.launch {
            if (commandCounter.get() != commandId) return@launch
            
            showInitialNotification()
            runCatching { wakeLock?.acquire(24 * 60 * 60 * 1000L) }

            getController().start()
        }
    }

    private fun stopProxyService(commandId: Long) {
        scope.launch {
            getController().stop()
            runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
            
            if (commandCounter.get() == commandId) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateNotification() {
        val status = ConnectionController.status.value
        if (status == ConnectionStatus.STOPPED) return
        val text = when (status) {
            ConnectionStatus.RUNNING -> "Proxy active"
            ConnectionStatus.STARTING, ConnectionStatus.VALIDATING -> "Starting proxy..."
            ConnectionStatus.RECONNECTING -> "Reconnecting..."
            ConnectionStatus.STOPPING -> "Stopping proxy..."
            ConnectionStatus.ERROR -> "Proxy error"
            else -> ""
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showInitialNotification() {
        val notification = buildNotification("Starting proxy...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, AetherProxyService::class.java).apply { action = ACTION_STOP }, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AetherST Proxy")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_stat_aether)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Proxy", stopIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "AetherST Proxy", NotificationManager.IMPORTANCE_DEFAULT).apply {
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
