package io.github.immaghzbad.aetherst.desktop

import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage

class TrayActions(
    val onShowWindow: () -> Unit,
    val onToggleConnection: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenRouting: () -> Unit,
    val onExit: () -> Unit
)

object AetherTray {
    private var trayIcon: TrayIcon? = null
    private var toggleItem: MenuItem? = null

    fun install(actions: TrayActions) {
        if (!SystemTray.isSupported()) return
        runCatching {
            val tray = SystemTray.getSystemTray()

            val open = MenuItem("Open AetherST")
            val toggle = MenuItem("Connect")
            val settings = MenuItem("Settings")
            val routing = MenuItem("Routing Rules")
            val quit = MenuItem("Exit")
            toggleItem = toggle

            open.addActionListener { actions.onShowWindow() }
            toggle.addActionListener { actions.onToggleConnection() }
            settings.addActionListener { actions.onOpenSettings() }
            routing.addActionListener { actions.onOpenRouting() }
            quit.addActionListener { actions.onExit() }

            val popup = PopupMenu().apply {
                add(MenuItem("AetherST Tunnel").apply { isEnabled = false })
                addSeparator()
                add(open)
                add(toggle)
                addSeparator()
                add(settings)
                add(routing)
                addSeparator()
                add(quit)
            }

            val icon = TrayIcon(createIcon(), "AetherST Tunnel", popup).apply {
                isImageAutoSize = true
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.clickCount == 2) {
                            actions.onShowWindow()
                        }
                    }
                })
            }

            tray.add(icon)
            trayIcon = icon
        }
    }

    fun setConnectionState(connected: Boolean) {
        runCatching {
            toggleItem?.label = if (connected) "Disconnect" else "Connect"
            trayIcon?.toolTip = if (connected) "AetherST Tunnel - Connected" else "AetherST Tunnel"
        }
    }

    private fun createIcon(): Image {
        runCatching {
            val bytes = AetherTray::class.java.classLoader
                ?.getResourceAsStream("icon.png")
                ?.use { it.readBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                return Toolkit.getDefaultToolkit().createImage(bytes)
            }
        }
        val size = 16
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = java.awt.Color(0x007AFF)
        g.fillRoundRect(1, 1, size - 2, size - 2, 6, 6)
        g.color = java.awt.Color.WHITE
        g.drawString("A", 4, 12)
        g.dispose()
        return image
    }
}