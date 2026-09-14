package io.github.immaghzbad.aetherst.desktop

import java.io.File
import java.util.Locale

const val APP_VERSION = "1.4.2"

object AppPaths {
    lateinit var dataDir: File
        private set
    lateinit var filesDir: File
        private set
    lateinit var cacheDir: File
        private set
    lateinit var binDir: File
        private set

    fun initialize() {
        val base = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "AetherST")
        dataDir = File(base, "data")
        filesDir = base
        cacheDir = File(base, "cache")
        binDir = File(filesDir, "bin")
        dataDir.mkdirs()
        cacheDir.mkdirs()
        binDir.mkdirs()
    }

    fun configFile(): File = File(dataDir, "aether_config.properties")

    fun logsFile(): File = File(dataDir, "aether_logs.json")

    fun isWindows(): Boolean = System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")

    fun isAdmin(): Boolean = runCatching {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        if (!os.contains("win")) return@runCatching true
        val command = arrayOf(
            "powershell", "-NoProfile", "-Command",
            "([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)"
        )
        val proc = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        output == "True"
    }.getOrDefault(false)

    fun openBrowser(url: String) {
        runCatching {
            val os = System.getProperty("os.name").lowercase(Locale.ROOT)
            if (os.contains("win")) {
                ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
            } else {
                ProcessBuilder("xdg-open", url).start()
            }
        }
    }
}