package io.github.immaghzbad.aetherst.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.immaghzbad.aetherst.MainActivity
import io.github.immaghzbad.aetherst.R
import io.github.immaghzbad.aetherst.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AetherTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = AetherVpnService.serviceState
            .onEach { updateTile(it) }
            .launchIn(scope)
    }

    override fun onStopListening() {
        job?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        val repo = AetherConfigRepository.getInstance(this)
        if (!repo.isOnboardingComplete.value) {
            startApp()
            return
        }

        when (AetherVpnService.serviceState.value) {
            ConnectionStatus.RUNNING -> AetherVpnService.stopVpn(this)
            ConnectionStatus.STOPPED, ConnectionStatus.ERROR -> AetherVpnService.startVpn(this)
            else -> Unit
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(state: ConnectionStatus) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_aether)
        
        when (state) {
            ConnectionStatus.RUNNING -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Connected"
                }
            }
            ConnectionStatus.STOPPED, ConnectionStatus.ERROR -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Disconnected"
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Connecting..."
                }
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
}
