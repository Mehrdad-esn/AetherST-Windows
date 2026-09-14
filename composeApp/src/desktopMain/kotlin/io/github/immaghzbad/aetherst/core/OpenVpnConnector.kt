package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSystemUtils
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import java.io.File

/**
 * Windows-only OpenVPN Hybrid connector (original work of this Windows port).
 *
 * Chain: apps -> OpenVPN TUN -> local SOCKS (127.0.0.1:socksPort, served by the
 * Aether core running in WG proxy mode) -> Aether transport -> Internet.
 *
 * Legal note: OpenVPN Community binaries are NOT bundled with this repository
 * (GPL). On first use the official Community MSI is downloaded at runtime from
 * swupdate.openvpn.org and extracted locally. Upstream AetherST / PowerSigma
 * attribution is preserved; see LICENSE and README.
 */
class OpenVpnConnector(
    private val context: PlatformContext,
    private val configPath: String,
    private val proxyHost: String,
    private val socksPort: Int,
    private val httpPort: Int,
    private val username: String = "",
    private val password: String = ""
) {
    private var process: Process? = null
    private var elevatedPid: Long? = null
    private var managementPort: Int? = null
    private var capturedGateway: String? = null

    private fun filesDir(): File = File(getSystemUtils(context).getFilesDir())
    private fun openVpnDir(): File = File(filesDir(), "openvpn")
    private fun dataDir(): File = filesDir().also { if (!it.exists()) it.mkdirs() }

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
            val logFile = File(dataDir(), "openvpn.log")
            logFile.delete()

            capturedGateway = getDefaultGateway()
            managementPort = allocateManagementPort()

            val cmd = mutableListOf(
                exe.absolutePath,
                "--config", wrapper.absolutePath,
                "--auth-nocache",
                "--log", logFile.absolutePath,
                "--verb", "3",
                "--management", "127.0.0.1", "$managementPort"
            )

            if (requiresInteractiveAuth(configFile)) {
                if (username.isBlank()) {
                    LogRepository.e("[OpenVPN] Config requires username/password but none are set - enter them in Settings -> OpenVPN Credentials", "OpenVPN")
                    return false
                }
                val authFile = File(dataDir(), "openvpn-auth.txt")
                authFile.writeText("$username\n$password")
                cmd.addAll(listOf("--auth-user-pass", authFile.absolutePath))
                LogRepository.i("[OpenVPN] Config requires login - using saved credentials for $username", "OpenVPN")
            }

            if (Elevation.isElevated()) {
                val pb = ProcessBuilder(cmd)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                process = proc
                startLogTailer(proc)
            } else {
                val launched = launchElevated(cmd, logFile)
                if (!launched) {
                    LogRepository.e("[OpenVPN] Elevated launch failed (UAC declined or blocked) - aborting", "OpenVPN")
                    stop()
                    return false
                }
            }

            LogRepository.i("[OpenVPN] Hybrid tunnel starting via $exe (proxy $proxyHost:$socksPort / http:$httpPort)")

            val connected = waitForConnection(logFile, timeoutMs = 90_000)
            if (!connected) {
                LogRepository.e("[OpenVPN] Connection timed out or failed — check OpenVPN logs", "OpenVPN")
                stop()
                return false
            }

            addRouteBypass()

            LogRepository.i("[OpenVPN] Hybrid tunnel CONNECTED — system is now tunneled through OpenVPN over Aether proxy")
            true
        } catch (e: Exception) {
            LogRepository.e("[OpenVPN] Failed to start: ${e.localizedMessage}", "OpenVPN")
            false
        }
    }

    fun stop() {
        val directPid = process?.pid()
        val pid = elevatedPid ?: directPid

        if (pid != null && signalGracefulStop()) {
            waitForExit(pid, timeoutMs = 8000)
        }

        runCatching { process?.destroyForcibly() }
        runCatching {
            pid?.let {
                if (ProcessHandle.of(it).map { h -> h.isAlive }.orElse(false)) {
                    ProcessBuilder("taskkill", "/F", "/PID", "$it").start().waitFor()
                }
            }
        }

        process = null
        elevatedPid = null
        managementPort = null

        removeRouteBypass()
    }

    private fun signalGracefulStop(): Boolean {
        val port = managementPort ?: return false
        return runCatching {
            java.net.Socket("127.0.0.1", port).use { socket ->
                socket.getOutputStream().write("signal SIGTERM\n".toByteArray())
                socket.getOutputStream().flush()
            }
            true
        }.getOrDefault(false)
    }

    private fun waitForExit(pid: Long, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val alive = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
            if (!alive) return
            Thread.sleep(250)
        }
    }

    private fun allocateManagementPort(): Int {
        for (port in 7505..7559) {
            runCatching {
                java.net.ServerSocket(port).use { return port }
            }
        }
        return 7505
    }

    private fun launchElevated(cmd: List<String>, logFile: File): Boolean {
        val exePath = cmd[0]
        val args = cmd.drop(1).joinToString("','") { it.replace("'", "''") }

        val pidFile = File(dataDir(), "openvpn-pid.txt")
        pidFile.delete()

        val ps1 = File(dataDir(), "openvpn-launch.ps1")
        ps1.writeText(
            """
            |try {
            |  ${'$'}proc = Start-Process -FilePath '$exePath' -ArgumentList '$args' -Verb RunAs -PassThru -WindowStyle Hidden -ErrorAction Stop
            |  ${'$'}proc.Id | Out-File -FilePath '${pidFile.absolutePath.replace('\\', '/')}' -Encoding ascii
            |} catch { exit 1 }
            """.trimMargin()
        )

        val pb = ProcessBuilder(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
            "-File", ps1.absolutePath
        )
        pb.redirectErrorStream(true)
        val launcher = pb.start()
        if (launcher.waitFor() != 0 || !pidFile.exists()) {
            return false
        }

        val pidStr = pidFile.readText().trim()
        elevatedPid = pidStr.toLongOrNull()
        LogRepository.i("[OpenVPN] Elevated process started, PID: $pidStr", "OpenVPN")

        startLogFileTailer(logFile)
        return true
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
            lower.contains("initialization sequence completed") -> LogRepository.i("✓ $line", "OpenVPN")
            lower.contains("fatal") || lower.contains(" error ") || lower.contains("exiting") ||
                lower.contains("auth-failure") ->
                LogRepository.w(line, "OpenVPN")
            lower.contains("socks") || lower.contains("tls") || lower.contains("adapt") ||
                lower.contains("tcp") || lower.contains("proxy") ->
                LogRepository.d(line, "OpenVPN")
            lower.contains("connected") || lower.contains("tun") || lower.contains("route") ->
                LogRepository.i(line, "OpenVPN")
        }
    }

    private fun waitForConnection(logFile: File, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val proc = process
            if (proc != null && !proc.isAlive) {
                LogRepository.e("[OpenVPN] Process exited prematurely", "OpenVPN")
                return false
            }

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
        val wrapper = File(dataDir(), "openvpn-hybrid.ovpn")
        val userLines = userConfig.readLines()

        val hasUdp = userLines.any { line ->
            val trimmed = line.trim().lowercase()
            trimmed.startsWith("proto") && trimmed.contains("udp")
        }

        val hasProxy = userLines.any { line ->
            val trimmed = line.trim().lowercase()
            trimmed.startsWith("socks-proxy") || trimmed.startsWith("http-proxy")
        }

        val sb = StringBuilder()
        sb.appendLine("config ${userConfig.absolutePath.replace('\\', '/')}")

        if (hasUdp) {
            sb.appendLine("proto tcp")
            LogRepository.w("[OpenVPN] Config uses UDP - forcing proto tcp for proxy compatibility. If this server has no TCP listener, use a TCP config (e.g. *.tcp.ovpn)", "OpenVPN")
        }

        if (!hasProxy) {
            if (hasUdp) {
                sb.appendLine("http-proxy $proxyHost $httpPort")
                LogRepository.i("[OpenVPN] Using HTTP proxy $proxyHost:$httpPort", "OpenVPN")
            } else {
                sb.appendLine("socks-proxy $proxyHost $socksPort")
                LogRepository.i("[OpenVPN] Using SOCKS proxy $proxyHost:$socksPort", "OpenVPN")
            }
        }

        sb.appendLine("auth-nocache")
        wrapper.writeText(sb.toString())
        return wrapper
    }

    private fun addRouteBypass() {
        val defaultGw = capturedGateway ?: return
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
            val isIp = output.isNotEmpty() && output.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))
            val isVirtual = output.startsWith("198.18.") || output.startsWith("10.98.")
            if (isIp && !isVirtual) output else null
        } catch (_: Exception) { null }
    }

    private fun collectEndpointIps(): List<String> {
        val result = LinkedHashSet<String>()
        val candidates = mutableListOf<File>()
        filesDir().listFiles()?.forEach { candidates.add(it) }
        openVpnDir().listFiles()?.forEach { candidates.add(it) }
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
        val extracted = File(openVpnDir(), "extracted")
        if (extracted.exists()) {
            candidates.addAll(extracted.walkTopDown().filter { it.name.equals("openvpn.exe", true) })
        }
        candidates.add(File(openVpnDir(), "openvpn.exe"))
        candidates.add(File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "OpenVPN\\bin\\openvpn.exe"))
        candidates.add(File("C:\\Program Files\\OpenVPN\\bin\\openvpn.exe"))
        return candidates.firstOrNull { it.exists() }
    }

    private fun downloadAndExtract(): File? {
        return try {
            openVpnDir().mkdirs()
            val msi = File(openVpnDir(), "openvpn.msi")
            LogRepository.i("[OpenVPN] Downloading OpenVPN community build...", "OpenVPN")
            java.net.URL(msiUrl).openStream().use { input ->
                msi.outputStream().use { output -> input.copyTo(output) }
            }
            val extractDir = File(openVpnDir(), "extracted")
            extractDir.mkdirs()
            val proc = ProcessBuilder(
                "msiexec", "/a", msi.absolutePath, "/qn", "REBOOT=ReallySuppress",
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
                line.trim().equals("auth-user-pass", ignoreCase = true)
            }
        }.getOrDefault(false)
    }
}
