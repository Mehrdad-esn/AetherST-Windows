package io.github.immaghzbad.aetherst.core

import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.desktop.AppPaths
import java.io.File

class OpenVpnConnector(
    private val configPath: String,
    private val proxyHost: String,
    private val socksPort: Int,
    private val httpPort: Int,
    private val username: String = "",
    private val password: String = ""
) {
    private var process: Process? = null
    private var elevatedPid: Long? = null
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
            // Clear old log so we don't read stale "initialization sequence completed"
            logFile.delete()

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

            // OpenVPN needs admin rights on Windows to create TUN/TAP adapter.
            // Try to detect if we're already elevated; if not, launch via elevation.
            if (Elevation.isElevated()) {
                // Already admin — launch directly
                val pb = ProcessBuilder(cmd)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                process = proc
                startLogTailer(proc)
            } else {
                // Launch elevated via PowerShell Start-Process -Verb RunAs
                launchElevated(cmd, logFile)
            }

            LogRepository.i("[OpenVPN] Hybrid tunnel starting via $exe (proxy $proxyHost:$socksPort / http:$httpPort)")

            // Wait for actual connection confirmation in the log file
            val connected = waitForConnection(logFile, timeoutMs = 90_000)
            if (!connected) {
                LogRepository.e("[OpenVPN] Connection timed out or failed — check OpenVPN logs", "OpenVPN")
                stop()
                return false
            }

            // Add route bypass for WireGuard endpoints so their traffic doesn't go through OpenVPN TUN
            addRouteBypass()

            LogRepository.i("[OpenVPN] Hybrid tunnel CONNECTED — system is now tunneled through OpenVPN over WireGuard")
            true
        } catch (e: Exception) {
            LogRepository.e("[OpenVPN] Failed to start: ${e.localizedMessage}", "OpenVPN")
            false
        }
    }

    fun stop() {
        // Try to kill the direct process
        runCatching { process?.destroyForcibly() }
        process = null

        // Also kill any elevated openvpn.exe processes we may have launched
        runCatching {
            val pid = elevatedPid
            if (pid != null) {
                ProcessBuilder("taskkill", "/F", "/PID", "$pid").start().waitFor()
            } else {
                // Fallback: kill all openvpn.exe (only our spawned ones ideally)
                ProcessBuilder("taskkill", "/F", "/IM", "openvpn.exe").start().waitFor()
            }
        }
        elevatedPid = null

        // Remove route bypasses
        removeRouteBypass()
    }

    private fun launchElevated(cmd: List<String>, logFile: File) {
        // Build a command string for PowerShell
        val exePath = cmd[0]
        val args = cmd.drop(1).joinToString("','") { it.replace("'", "''") }

        val ps1 = File(AppPaths.dataDir, "openvpn-launch.ps1")
        ps1.writeText(
            """
            |${'$'}proc = Start-Process -FilePath '$exePath' -ArgumentList '$args' -Verb RunAs -PassThru -WindowStyle Hidden
            |${'$'}proc.Id | Out-File -FilePath '${File(AppPaths.dataDir, "openvpn-pid.txt").absolutePath.replace('\\', '/')}' -Encoding ascii
            """.trimMargin()
        )

        val pb = ProcessBuilder(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
            "-File", ps1.absolutePath
        )
        pb.redirectErrorStream(true)
        val launcher = pb.start()
        launcher.waitFor()

        // Read PID of elevated process
        val pidFile = File(AppPaths.dataDir, "openvpn-pid.txt")
        if (pidFile.exists()) {
            val pidStr = pidFile.readText().trim()
            elevatedPid = pidStr.toLongOrNull()
            LogRepository.i("[OpenVPN] Elevated process started, PID: $pidStr", "OpenVPN")
        }

        // Start tailing the log file in background since we can't read stdout of elevated process
        startLogFileTailer(logFile)
    }

    private fun startLogTailer(proc: Process) {
        Thread {
            runCatching {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    processLogLine(line)
                }
            }
        }.start()
    }

    private fun startLogFileTailer(logFile: File) {
        Thread {
            var offset = 0L
            while (true) {
                try {
                    if (logFile.exists()) {
                        val len = logFile.length()
                        if (len > offset) {
                            java.io.RandomAccessFile(logFile, "r").use { raf ->
                                raf.seek(offset)
                                val bytes = ByteArray((len - offset).toInt())
                                raf.readFully(bytes)
                                offset = len
                                String(bytes).lineSequence().filter { it.isNotBlank() }.forEach {
                                    processLogLine(it)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                Thread.sleep(500)

                // Check if OpenVPN process is still alive
                val pid = elevatedPid
                if (pid != null) {
                    val alive = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
                    if (!alive) break
                }
                if (process?.isAlive == false) break
            }
        }.start()
    }

    private fun processLogLine(line: String) {
        val lower = line.lowercase()
        when {
            lower.contains("initialization sequence completed") -> LogRepository.i("[OpenVPN] ✓ $line", "OpenVPN")
            lower.contains("fatal") || lower.contains(" error ") || lower.contains("exiting") ||
                lower.contains("auth-failure") ->
                LogRepository.w("[OpenVPN] $line", "OpenVPN")
            lower.contains("socks") || lower.contains("tls") || lower.contains("adapt") ||
                lower.contains("tcp") || lower.contains("proxy") ->
                LogRepository.d("[OpenVPN] $line", "OpenVPN")
            lower.contains("connected") || lower.contains("tun") || lower.contains("route") ->
                LogRepository.i("[OpenVPN] $line", "OpenVPN")
        }
    }

    private fun waitForConnection(logFile: File, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            // Check if direct process died
            val proc = process
            if (proc != null && !proc.isAlive) {
                LogRepository.e("[OpenVPN] Process exited prematurely", "OpenVPN")
                return false
            }

            // Check if elevated process died
            val pid = elevatedPid
            if (pid != null) {
                val alive = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
                if (!alive) {
                    LogRepository.e("[OpenVPN] Elevated process exited prematurely", "OpenVPN")
                    return false
                }
            }

            if (logFile.exists()) {
                val content = logFile.readText().lowercase()
                if (content.contains("initialization sequence completed")) {
                    return true
                }
                if (content.contains("auth-failure") || content.contains("process exiting") ||
                    content.contains("fatal")) {
                    LogRepository.e("[OpenVPN] Connection failed — see openvpn.log for details", "OpenVPN")
                    return false
                }
            }
            Thread.sleep(1000)
        }
        return false
    }

    private fun createWrapperConfig(userConfig: File): File {
        val wrapper = File(AppPaths.dataDir, "openvpn-hybrid.ovpn")
        val userLines = userConfig.readLines()

        // Detect if user config uses UDP
        val hasUdp = userLines.any { line ->
            val trimmed = line.trim().lowercase()
            trimmed.startsWith("proto") && trimmed.contains("udp")
        }

        // Detect if user config already specifies a proxy
        val hasProxy = userLines.any { line ->
            val trimmed = line.trim().lowercase()
            trimmed.startsWith("socks-proxy") || trimmed.startsWith("http-proxy")
        }

        val sb = StringBuilder()

        // Include the original config
        sb.appendLine("config ${userConfig.absolutePath.replace('\\', '/')}")

        if (hasUdp) {
            // UDP cannot go through SOCKS/HTTP proxy. Force TCP and use http-proxy.
            // Override proto to tcp
            sb.appendLine("proto tcp")
            LogRepository.i("[OpenVPN] Original config uses UDP — forcing TCP for proxy compatibility", "OpenVPN")
        }

        if (!hasProxy) {
            if (hasUdp) {
                // For configs that were UDP (now forced to TCP), use http-proxy
                sb.appendLine("http-proxy $proxyHost $httpPort")
                LogRepository.i("[OpenVPN] Using HTTP proxy $proxyHost:$httpPort", "OpenVPN")
            } else {
                // TCP config — socks-proxy works fine
                sb.appendLine("socks-proxy $proxyHost $socksPort")
                LogRepository.i("[OpenVPN] Using SOCKS proxy $proxyHost:$socksPort", "OpenVPN")
            }
        }

        sb.appendLine("auth-nocache")

        // Ensure we don't pull routes that would conflict before we're ready
        // OpenVPN should still set up its own TUN and default route
        wrapper.writeText(sb.toString())
        return wrapper
    }

    private fun addRouteBypass() {
        // Get the default gateway (before OpenVPN changes it)
        val defaultGw = getDefaultGateway() ?: return

        // Collect WireGuard endpoint IPs that must bypass OpenVPN's TUN
        val endpointIps = collectEndpointIps()
        if (endpointIps.isEmpty()) return

        for (ip in endpointIps) {
            runCatching {
                ProcessBuilder("route", "add", ip, "mask", "255.255.255.255", defaultGw)
                    .redirectErrorStream(true).start().waitFor()
                LogRepository.d("[OpenVPN] Route bypass added: $ip via $defaultGw", "OpenVPN")
            }
        }
    }

    private fun removeRouteBypass() {
        val endpointIps = collectEndpointIps()
        for (ip in endpointIps) {
            runCatching {
                ProcessBuilder("route", "delete", ip, "mask", "255.255.255.255")
                    .redirectErrorStream(true).start().waitFor()
            }
        }
    }

    private fun getDefaultGateway(): String? {
        return try {
            val proc = ProcessBuilder("powershell", "-NoProfile", "-Command",
                "(Get-NetRoute -DestinationPrefix '0.0.0.0/0' | Sort-Object RouteMetric | Select-Object -First 1).NextHop"
            ).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (output.isNotEmpty() && output.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))) output else null
        } catch (_: Exception) { null }
    }

    private fun collectEndpointIps(): List<String> {
        val result = LinkedHashSet<String>()
        val candidates = mutableListOf<File>()
        AppPaths.binDir.listFiles()?.forEach { candidates.add(it) }
        AppPaths.dataDir.listFiles()?.forEach { candidates.add(it) }
        // Also check project-level toml files
        val projectDir = File(System.getProperty("user.dir", "."))
        projectDir.listFiles()?.forEach { candidates.add(it) }

        val pattern = Regex("""(?i)(?:peer|assigned_endpoint|endpoint)\s*=\s*"?([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)""")
        for (file in candidates) {
            if (!file.isFile) continue
            if (!file.extension.equals("toml", ignoreCase = true) &&
                !file.extension.equals("conf", ignoreCase = true)) continue
            runCatching {
                val text = file.readText()
                pattern.findAll(text).forEach { result.add(it.groupValues[1]) }
            }
        }
        return result.toList()
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

    private fun requiresInteractiveAuth(configFile: File): Boolean {
        return runCatching {
            configFile.readLines().any { line ->
                val trimmed = line.trim()
                trimmed.equals("auth-user-pass", ignoreCase = true)
            }
        }.getOrDefault(false)
    }
}