package io.github.immaghzbad.aetherst.core

import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.desktop.AppPaths
import java.io.File

class OpenVpnConnector(
    private val configPath: String,
    private val proxyHost: String,
    private val proxyPort: Int,
    private val username: String = "",
    private val password: String = ""
) {
    private var process: Process? = null
    private val openVpnDir = File(AppPaths.binDir, "openvpn")
    private val msiUrl = "https://swupdate.openvpn.org/community/releases/OpenVPN-2.6.22-I001-amd64.msi"

    fun start(): Boolean {
        return try {
            val exe = resolveOpenVpnExe() ?: downloadAndExtract() ?: run {
                LogRepository.e("[OpenVPN] Could not obtain openvpn.exe", "OpenVPN")
                return false
            }
            val configFile = File(configPath)
            if (!configFile.exists()) {
                LogRepository.e("[OpenVPN] Config file not found: $configPath", "OpenVPN")
                return false
            }

            val wrapper = createWrapperConfig(configFile)
            val logFile = File(AppPaths.dataDir, "openvpn.log")
            val cmd = mutableListOf(
                exe.absolutePath,
                "--config", wrapper.absolutePath,
                "--auth-nocache",
                "--log", logFile.absolutePath,
                "--verb", "3"
            )

            if (requiresInteractiveAuth(configFile)) {
                if (username.isBlank()) {
                    LogRepository.e("[OpenVPN] Config requires username/password but none are set - enter them in Settings -> OpenVPN Credentials", "OpenVPN")
                    return false
                }
                val authFile = File(AppPaths.dataDir, "openvpn-auth.txt")
                authFile.writeText("$username\n$password")
                cmd.addAll(listOf("--auth-user-pass", authFile.absolutePath))
                LogRepository.i("[OpenVPN] Config requires login - using saved credentials for $username", "OpenVPN")
            }

            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            process = proc

            Thread {
                runCatching {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        val lower = line.lowercase()
                        when {
                            lower.contains("initialization sequence completed") -> LogRepository.i(line, "OpenVPN")
                            lower.contains("fatal") || lower.contains(" error ") || lower.contains("exiting") ->
                                LogRepository.w(line, "OpenVPN")
                            lower.contains("socks") || lower.contains("tls") || lower.contains("adapt") ->
                                LogRepository.d(line, "OpenVPN")
                        }
                    }
                }
            }.start()

            LogRepository.i("[OpenVPN] Hybrid tunnel started via $exe (proxy $proxyHost:$proxyPort)")
            true
        } catch (e: Exception) {
            LogRepository.e("[OpenVPN] Failed to start: ${e.localizedMessage}", "OpenVPN")
            false
        }
    }

    fun stop() {
        runCatching { process?.destroyForcibly() }
        process = null
    }

    private fun resolveOpenVpnExe(): File? {
        val candidates = mutableListOf<File>()
        val extracted = File(openVpnDir, "extracted")
        if (extracted.exists()) {
            candidates.addAll(extracted.walkTopDown().filter { it.name.equals("openvpn.exe", true) })
        }
        candidates.add(File(openVpnDir, "openvpn.exe"))
        candidates.add(File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "OpenVPN\\bin\\openvpn.exe"))
        candidates.add(File("C:\\Program Files\\OpenVPN\\bin\\openvpn.exe"))
        return candidates.firstOrNull { it.exists() }
    }

    private fun downloadAndExtract(): File? {
        return try {
            openVpnDir.mkdirs()
            val msi = File(openVpnDir, "openvpn.msi")
            LogRepository.i("[OpenVPN] Downloading OpenVPN community build (5.9 MB)...", "OpenVPN")
            java.net.URL(msiUrl).openStream().use { input ->
                msi.outputStream().use { output -> input.copyTo(output) }
            }
            val extractDir = File(openVpnDir, "extracted")
            extractDir.mkdirs()
            val proc = ProcessBuilder(
                "msiexec", "/a", msi.absolutePath, "/qn",
                "TARGETDIR=${extractDir.absolutePath}"
            ).redirectErrorStream(true).start()
            proc.waitFor()
            extractDir.walkTopDown().firstOrNull { it.name.equals("openvpn.exe", true) }
        } catch (e: Exception) {
            LogRepository.e("[OpenVPN] Download/extract failed: ${e.localizedMessage}", "OpenVPN")
            null
        }
    }

    private fun createWrapperConfig(userConfig: File): File {
        val wrapper = File(AppPaths.dataDir, "openvpn-hybrid.ovpn")
        wrapper.writeText(
            "config ${userConfig.absolutePath.replace('\\', '/')}\n" +
                "socks-proxy $proxyHost $proxyPort\n" +
                "auth-nocache\n"
        )
        return wrapper
    }

    private fun requiresInteractiveAuth(configFile: File): Boolean {
        return runCatching {
            configFile.readLines().any { line ->
                val trimmed = line.trim()
                trimmed.equals("auth-user-pass", ignoreCase = true)
            }
        }.getOrDefault(false)
    }
}