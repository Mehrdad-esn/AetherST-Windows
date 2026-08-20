package io.github.immaghzbad.aetherst.core

import java.io.File
import kotlin.system.exitProcess

object Elevation {

    fun isElevated(): Boolean {
        return runCatching {
            val proc = ProcessBuilder("net", "session")
                .redirectErrorStream(true)
                .start()
            proc.waitFor() == 0
        }.getOrDefault(false)
    }

    fun relaunchElevatedAndExit(): Boolean {
        val launcher = System.getProperty("jpackage.app-path")
        if (launcher.isNullOrBlank() || !File(launcher).exists()) return false
        return runCatching {
            val command = arrayOf(
                "powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                "Start-Process -FilePath '$launcher' -Verb RunAs"
            )
            ProcessBuilder(*command).start()
            exitProcess(0)
            true
        }.getOrDefault(false)
    }
}