package io.github.immaghzbad.aetherst

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import io.github.immaghzbad.aetherst.desktop.AppPaths
import io.github.immaghzbad.aetherst.desktop.AetherTray
import io.github.immaghzbad.aetherst.desktop.TrayActions
import io.github.immaghzbad.aetherst.ui.AppNavigation
import io.github.immaghzbad.aetherst.ui.AetherViewModel
import io.github.immaghzbad.aetherst.ui.screens.AppTitleBar
import io.github.immaghzbad.aetherst.ui.screens.MainScreen
import io.github.immaghzbad.aetherst.ui.theme.MyApplicationTheme
import java.awt.Toolkit
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

private const val DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2: Long = -4L

private fun enablePerMonitorDpiAwareness() {
    runCatching {
        val user32 = NativeLibrary.getInstance("user32")
        val fn = user32.getFunction("SetProcessDpiAwarenessContext")
        fn.invokeInt(arrayOf<Any>(Pointer(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2)))
    }
}

private fun sweepChildProcesses() {
    runCatching {
        ProcessBuilder("taskkill", "/F", "/T", "/IM", "aether.exe")
            .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS)
    }
    runCatching {
        ProcessBuilder("taskkill", "/F", "/T", "/IM", "hev-socks5-tunnel.exe")
            .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS)
    }
}

private var instanceLock: FileChannel? = null

private fun focusExistingWindow() {
    runCatching {
        val user32 = NativeLibrary.getInstance("user32")
        val hwnd = user32.getFunction("FindWindowW")
            .invokePointer(arrayOf<Any?>(null, WString("AetherST Tunnel")))
        if (Pointer.nativeValue(hwnd) != 0L) {
            user32.getFunction("ShowWindow").invokeInt(arrayOf<Any>(hwnd, 9))
            user32.getFunction("SetForegroundWindow").invokeInt(arrayOf<Any>(hwnd))
        }
    }
}

private fun acquireSingleInstanceLock(): Boolean {
    return runCatching {
        val base = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "AetherST")
        base.mkdirs()
        val channel = FileChannel.open(
            File(base, "instance.lock").toPath(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE
        )
        val lock = channel.tryLock()
        if (lock == null) {
            channel.close()
            focusExistingWindow()
            false
        } else {
            instanceLock = channel
            true
        }
    }.getOrDefault(true)
}

fun main() {
    enablePerMonitorDpiAwareness()

    if (!acquireSingleInstanceLock()) {
        println("AetherST is already running.")
        return
    }

    val iconBytes = runCatching {
        AetherViewModel::class.java.classLoader?.getResourceAsStream("icon.png")?.use { it.readBytes() }
    }.getOrNull()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            sweepChildProcesses()
        }
    )

    application {
        AppPaths.initialize()

    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        runCatching {
            File(AppPaths.cacheDir, "last_crash.log").writeText(
                "${e.javaClass.name}: ${e.localizedMessage}\n${sw.toString()}"
            )
        }
    }

    val viewModel = AetherViewModel()

    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        size = DpSize(432.dp, 784.dp)
    )
    var appWindow by remember { mutableStateOf<ComposeWindow?>(null) }

    fun showWindow() {
        runCatching {
            windowState.isMinimized = false
            appWindow?.isVisible = true
            appWindow?.toFront()
            appWindow?.requestFocus()
        }
    }

    AetherTray.install(
        TrayActions(
            onShowWindow = { showWindow() },
            onToggleConnection = { viewModel.toggleConnection() },
            onOpenSettings = {
                showWindow()
                viewModel.requestNavigation(AppNavigation.SETTINGS)
            },
            onOpenRouting = {
                showWindow()
                viewModel.requestNavigation(AppNavigation.ROUTING)
            },
            onExit = {
                viewModel.shutdown()
                exitApplication()
            }
        )
    )

val onCloseRequest: () -> Unit = {
        runCatching { appWindow?.isVisible = false }
    }

Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = "AetherST Tunnel",
        resizable = false,
        undecorated = true
    ) {
        appWindow = window as? ComposeWindow
        appWindow?.setIconImage(iconBytes?.let { Toolkit.getDefaultToolkit().createImage(it) })
        val density = LocalDensity.current
        LaunchedEffect(windowState) {
            val screenWidthPx = Toolkit.getDefaultToolkit().screenSize.width
            val screenHeightPx = Toolkit.getDefaultToolkit().screenSize.height
            snapshotFlow { windowState.position }.collect { pos ->
                val screenW = with(density) { screenWidthPx.toDp() }
                val screenH = with(density) { screenHeightPx.toDp() }
                val winW = windowState.size.width
                val winH = windowState.size.height
                val x = pos.x.coerceIn(-winW + 80.dp, screenW - 80.dp)
                val y = pos.y.coerceIn(0.dp, screenH - 80.dp)
                if (x != pos.x || y != pos.y) {
                    windowState.position = WindowPosition(x, y)
                }
            }
        }
        MyApplicationTheme {
            Column {
                AppTitleBar(
                    onMinimize = { windowState.isMinimized = true },
                    onClose = onCloseRequest
                )
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
    }
}