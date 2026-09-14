package io.github.immaghzbad.aetherst.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.graphics.toColorInt
import android.widget.RemoteViews
import io.github.immaghzbad.aetherst.R
import io.github.immaghzbad.aetherst.core.ConnectionController
import io.github.immaghzbad.aetherst.model.ConnectionStatus

class AetherWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "io.github.immaghzbad.aetherst.WIDGET_TOGGLE"
        const val ACTION_CHANGE_PROTOCOL = "io.github.immaghzbad.aetherst.WIDGET_CHANGE_PROTOCOL"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AetherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val status = ConnectionController.status.value
            val config = io.github.immaghzbad.aetherst.data.AetherConfigRepository.getInstance(context).config.value
            
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, status, config.protocol)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            status: ConnectionStatus,
            currentProtocol: io.github.immaghzbad.aetherst.model.AetherProtocol
        ) {
            val views = RemoteViews(context.packageName, R.layout.aether_widget)

            val statusText = when (status) {
                ConnectionStatus.RUNNING -> "Connected"
                ConnectionStatus.STARTING, ConnectionStatus.VALIDATING -> "Connecting..."
                ConnectionStatus.RECONNECTING -> "Reconnecting..."
                ConnectionStatus.STOPPING -> "Disconnecting..."
                ConnectionStatus.ERROR -> "Error"
                else -> "Disconnected"
            }

            val statusColor = when (status) {
                ConnectionStatus.RUNNING -> "#34C759".toColorInt()
                ConnectionStatus.STARTING, ConnectionStatus.VALIDATING, ConnectionStatus.RECONNECTING -> "#FF9500".toColorInt()
                ConnectionStatus.ERROR -> "#FF3B30".toColorInt()
                else -> "#8E8E93".toColorInt()
            }

            val buttonRes = when (status) {
                ConnectionStatus.RUNNING -> R.drawable.widget_button_green
                ConnectionStatus.STARTING, ConnectionStatus.VALIDATING, ConnectionStatus.RECONNECTING -> R.drawable.widget_button_orange
                else -> R.drawable.widget_button_blue
            }

            views.setTextViewText(R.id.widget_status, statusText)
            views.setTextColor(R.id.widget_status, statusColor)
            views.setImageViewResource(R.id.widget_button, android.R.drawable.ic_lock_power_off)
            views.setInt(R.id.widget_button, "setBackgroundResource", buttonRes)

            // Protocols selection UI
            setupProtocolButton(context, views, R.id.proto_masque, io.github.immaghzbad.aetherst.model.AetherProtocol.MASQUE, currentProtocol)
            setupProtocolButton(context, views, R.id.proto_wire, io.github.immaghzbad.aetherst.model.AetherProtocol.WG, currentProtocol)
            setupProtocolButton(context, views, R.id.proto_gool, io.github.immaghzbad.aetherst.model.AetherProtocol.GOOL, currentProtocol)

            val toggleIntent = Intent(context, AetherWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val togglePending = PendingIntent.getBroadcast(
                context, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_button_container, togglePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun setupProtocolButton(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            protocol: io.github.immaghzbad.aetherst.model.AetherProtocol,
            currentProtocol: io.github.immaghzbad.aetherst.model.AetherProtocol
        ) {
            val isActive = protocol == currentProtocol
            val bgRes = if (isActive) R.drawable.widget_protocol_active_bg else R.drawable.widget_protocol_inactive_bg
            val textColor = if (isActive) "#007AFF".toColorInt() else "#8E8E93".toColorInt()
            
            views.setInt(viewId, "setBackgroundResource", bgRes)
            views.setTextColor(viewId, textColor)

            val intent = Intent(context, AetherWidgetProvider::class.java).apply {
                action = ACTION_CHANGE_PROTOCOL
                putExtra("protocol", protocol.name)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, protocol.ordinal + 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val status = ConnectionController.status.value
        val config = io.github.immaghzbad.aetherst.data.AetherConfigRepository.getInstance(context).config.value
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, status, config.protocol)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val repository = io.github.immaghzbad.aetherst.data.AetherConfigRepository.getInstance(context)
        
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (!repository.isOnboardingComplete.value) {
                    val launchIntent = Intent(context, io.github.immaghzbad.aetherst.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(launchIntent)
                    return
                }

                val status = ConnectionController.status.value
                if (status == ConnectionStatus.RUNNING || status == ConnectionStatus.STARTING || status == ConnectionStatus.VALIDATING) {
                    AetherVpnService.stopVpn(context)
                } else {
                    AetherVpnService.startVpn(context)
                }
            }
            ACTION_CHANGE_PROTOCOL -> {
                val protocolName = intent.getStringExtra("protocol") ?: return
                val nextProtocol = io.github.immaghzbad.aetherst.model.AetherProtocol.valueOf(protocolName)
                val currentConfig = repository.config.value
                
                if (currentConfig.protocol != nextProtocol) {
                    repository.updateConfig(currentConfig.copy(protocol = nextProtocol))
                    updateAllWidgets(context)
                }
            }
        }
    }
}
