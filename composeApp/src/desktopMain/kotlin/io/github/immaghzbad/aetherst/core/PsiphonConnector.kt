package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.platform.PlatformContext
import io.github.immaghzbad.aetherst.platform.getSystemUtils
import io.github.immaghzbad.aetherst.shared.data.LogRepository
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Windows-only Psiphon chain connector (original work of this Windows port).
 *
 * Runs the GPL-3.0 `psiphon-helper.exe` (built from the psiphon-tunnel-core
 * v2.0.41 sources in `psiphon-helper/`, same version as the Android app) as a
 * separate local proxy, mirroring the Android PsiphonController semantics:
 *
 * - core-first: helper dials through the Aether core SOCKS (`--upstream`),
 *   apps/TUN use the helper exit (non-Iran IP).
 * - psiphon-only: helper dials direct, no core involved.
 *
 * Legal note: the helper is a separate process reached over local SOCKS/HTTP
 * (same pattern as OpenVPN Hybrid). Its full source ships in
 * `psiphon-helper/`; see also LICENSE and README.
 */
class PsiphonConnector(
    private val context: PlatformContext,
    private val socksPort: Int,
    private val httpPort: Int,
    private val egressRegion: String,
    private val upstream: String?,
    private val onRegions: (List<String>) -> Unit = {}
) {
    @Volatile private var process: Process? = null
    @Volatile private var running = false
    @Volatile private var connected = false
    @Volatile private var actualSocksPort = socksPort
    @Volatile private var actualHttpPort = httpPort
    private val lastEventTime = AtomicLong(0L)
    private var capturedGateway: String? = null

    private fun filesDir(): File = File(getSystemUtils(context).getFilesDir()).also { if (!it.exists()) it.mkdirs() }
    private fun psiphonDir(): File = File(filesDir(), "psiphon").also { if (!it.exists()) it.mkdirs() }

    fun start(): Boolean {
        return try {
            val exe = resolveHelperExe() ?: run {
                LogRepository.e("[Psiphon] psiphon-helper.exe not found", "Psiphon")
                return false
            }
            val entriesFile = ensureServerEntries() ?: run {
                LogRepository.e("[Psiphon] server_entries.txt unavailable", "Psiphon")
                return false
            }
            capturedGateway = getDefaultGateway()

            val cmd = mutableListOf(
                exe.absolutePath,
                "--socks-port", socksPort.toString(),
                "--http-port", httpPort.toString(),
                "--data-dir", psiphonDir().absolutePath,
                "--server-entries", entriesFile.absolutePath
            )
            if (egressRegion.isNotBlank()) {
                cmd.add("--egress-region")
                cmd.add(egressRegion.trim().uppercase())
            }
            if (!upstream.isNullOrBlank()) {
                cmd.add("--upstream")
                cmd.add(upstream.trim())
            }

            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(false)
            val proc = pb.start()
            process = proc
            running = true
            connected = false
            lastEventTime.set(System.currentTimeMillis())
            startOutputReader(proc)
            LogRepository.i("[Psiphon] helper starting (socks=$socksPort http=$httpPort upstream=${upstream ?: "direct"})")
            true
        } catch (e: Exception) {
            LogRepository.e("[Psiphon] Failed to start: ${e.localizedMessage}", "Psiphon")
            running = false
            false
        }
    }

    fun stop() {
        val proc = process
        process = null
        running = false
        connected = false
        runCatching { proc?.destroyForcibly() }
        runCatching { proc?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) }
        removeRouteBypass()
        LogRepository.i("[Psiphon] stopped", "Psiphon")
    }

    fun isRunning(): Boolean = running && (process?.isAlive == true)

    fun isConnected(): Boolean = connected && isRunning()

    fun stableFor(graceMs: Long): Boolean = isConnected() &&
        (System.currentTimeMillis() - lastEventTime.get()) >= graceMs

    fun getUpstreamProxy(): String = "socks5://127.0.0.1:$actualSocksPort"

    fun activeSocksPort(): Int = actualSocksPort

    fun activeHttpPort(): Int = actualHttpPort

    /** Bypass TUN routes for Psiphon server IPs (psiphon-only mode, helper dials direct). */
    fun addRouteBypassForDirectMode() {
        val defaultGw = capturedGateway ?: getDefaultGateway()?.also { capturedGateway = it } ?: return
        val endpointIps = collectServerIps()
        if (endpointIps.isEmpty()) return
        for (ip in endpointIps) {
            runCatching {
                ProcessBuilder("route", "add", ip, "mask", "255.255.255.255", defaultGw)
                    .redirectErrorStream(true).start().waitFor()
                LogRepository.d("[Psiphon] Route bypass added: $ip via $defaultGw", "Psiphon")
            }
        }
    }

    private fun removeRouteBypass() {
        val endpointIps = collectServerIps()
        for (ip in endpointIps) {
            runCatching {
                ProcessBuilder("route", "delete", ip, "mask", "255.255.255.255")
                    .redirectErrorStream(true).start().waitFor()
            }
        }
    }

    private fun startOutputReader(proc: Process) {
        Thread {
            runCatching {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    handleEventLine(line.trim())
                }
            }
            if (running) {
                connected = false
                LogRepository.w("[Psiphon] helper output closed unexpectedly", "Psiphon")
            }
        }.apply { isDaemon = true }.start()
        Thread {
            runCatching {
                proc.errorStream.bufferedReader().forEachLine { line ->
                    val l = line.trim()
                    if (l.isNotEmpty()) LogRepository.d("[Psiphon] $l", "Psiphon")
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun handleEventLine(line: String) {
        if (!line.startsWith("PSIPHON_EVENT")) return
        try {
            val parts = line.removePrefix("PSIPHON_EVENT").trim().split(Regex("\\s+"))
            val kv = parts.mapNotNull {
                val eq = it.indexOf('=')
                if (eq > 0) it.substring(0, eq) to it.substring(eq + 1) else null
            }.toMap()
            when (kv["noticeType"]) {
                "ListeningSocksProxyPort" -> {
                    kv["port"]?.toIntOrNull()?.let {
                        actualSocksPort = it
                        lastEventTime.set(System.currentTimeMillis())
                        LogRepository.i("[Psiphon] SOCKS listening on 127.0.0.1:$it", "Psiphon")
                    }
                }
                "ListeningHttpProxyPort" -> {
                    kv["port"]?.toIntOrNull()?.let {
                        actualHttpPort = it
                        LogRepository.i("[Psiphon] HTTP listening on 127.0.0.1:$it", "Psiphon")
                    }
                }
                "Tunnels" -> {
                    val count = kv["count"]?.toIntOrNull() ?: 0
                    if (count > 0 && !connected) {
                        connected = true
                        LogRepository.i("[Psiphon] connected (tunnels=$count)", "Psiphon")
                    } else if (count <= 0) {
                        connected = false
                    }
                    lastEventTime.set(System.currentTimeMillis())
                }
                "AvailableEgressRegions" -> {
                    val regions = kv["regions"]?.split(",")?.map { it.trim().uppercase() }
                        ?.filter { it.matches(Regex("^[A-Z]{2}$")) }?.distinct().orEmpty()
                    if (regions.isNotEmpty()) {
                        LogRepository.i("[Psiphon] egress regions: ${regions.joinToString()}", "Psiphon")
                        runCatching { onRegions(regions) }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun resolveHelperExe(): File? {
        // 1. Installed data dir (BinaryManager extraction target layout).
        val dataBin = File(filesDir(), "bin/psiphon-helper.exe")
        if (dataBin.exists()) return dataBin
        // 2. Dev/build tree + packaged resources.
        val candidates = listOf(
            File("composeApp/src/desktopMain/resources/bin/psiphon-helper.exe"),
            File(System.getProperty("compose.application.resources.dir") ?: "", "bin/psiphon-helper.exe")
        )
        candidates.firstOrNull { it.exists() }?.let { return it }
        // 3. Extract from packaged resources via BinaryManager layout.
        return try {
            val path = getBinaryManager(context).prepareBinary("psiphon-helper.exe")
            File(path).takeIf { it.exists() }
        } catch (_: Exception) { null }
    }

    private fun ensureServerEntries(): File? {
        return try {
            val target = File(psiphonDir(), "server_entries.txt")
            if (target.exists() && target.length() > 0) return target
            val stream = javaClass.getResourceAsStream("/psiphon/server_entries.txt")
                ?: javaClass.classLoader.getResourceAsStream("psiphon/server_entries.txt")
            if (stream != null) {
                stream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                return target.takeIf { it.length() > 0 }
            }
            // Dev-tree fallback.
            val dev = File("composeApp/src/desktopMain/resources/psiphon/server_entries.txt")
            if (dev.exists()) {
                dev.copyTo(target, overwrite = true)
                return target
            }
            null
        } catch (_: Exception) { null }
    }

    private fun collectServerIps(): List<String> {
        val result = LinkedHashSet<String>()
        val files = listOf(
            File(psiphonDir(), "server_entries.txt"),
            File("composeApp/src/desktopMain/resources/psiphon/server_entries.txt")
        )
        val pattern = Regex("""\b(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\b""")
        for (file in files) {
            if (!file.isFile) continue
            runCatching {
                pattern.findAll(file.readText()).forEach {
                    val ip = it.groupValues[1]
                    if (ip.split(".").all { (it.toIntOrNull() ?: 256) in 0..255 }) result.add(ip)
                }
            }
        }
        return result.toList()
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
}
