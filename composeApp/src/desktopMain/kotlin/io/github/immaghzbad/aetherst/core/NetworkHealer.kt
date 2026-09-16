package io.github.immaghzbad.aetherst.shared.core

import io.github.immaghzbad.aetherst.shared.data.LogRepository

object NetworkHealer {

    private const val TAG = "Heal"
    private const val HYBRID_MARKER = "openvpn-hybrid"
    private val virtualGatewayPrefixes = listOf("198.18.", "10.98.")

    fun heal() {
        // 1.7.1 (T4 root cause): an End-task/crash leaves aether.exe holding
        // 127.0.0.1:1819, so the next core dies with 10048 in a hot retry loop.
        // These binary names are unique to us — safe to sweep on every startup.
        runCatching { killOurCoreProcesses() }
        // Best-effort and surgical: only targets OUR Hybrid openvpn (cmdline carries
        // the wrapper config name), never a foreign client like OpenVPN Connect.
        val hybridKilled = runCatching { killOurHybridOpenVpn() }.getOrDefault(0)
        if (hybridKilled == 0 && isOurHybridRunning()) {
            LogRepository.w("[$TAG] orphan Hybrid openvpn.exe is still alive (likely elevated) — restart the app as administrator once, or disconnect it properly; new tunnels may time out")
        }
        // 1.7.1: an End-task can leave the HKCU proxy pointing at our dead relay
        // (127.0.0.1:10809) — that breaks browsers AND localhost clients even though
        // no route is wrong. HKCU needs no elevation, so repair it every startup.
        runCatching { repairStuckSystemProxy() }
        // Detection only (DNS restore needs elevation): warn loudly instead of
        // leaving the user with mysterious timeouts.
        runCatching { warnIfDnsStuck() }
        if (!Elevation.isElevated()) return
        runCatching { killOrphanHelpers() }
        runCatching { removeStaleVirtualRoutes() }.onFailure {
            LogRepository.w("[$TAG] route sweep failed: ${it.localizedMessage}")
        }
        runCatching { removeOrphanHybridRedirectRoutes() }.onFailure {
            LogRepository.w("[$TAG] hybrid redirect sweep failed: ${it.localizedMessage}")
        }
        runCatching { removeDeadSlashOneRoutes() }.onFailure {
            LogRepository.w("[$TAG] dead /1 sweep failed: ${it.localizedMessage}")
        }
        runCatching { removeTrackedHybridRoutes() }.onFailure {
            LogRepository.w("[$TAG] tracked Hybrid sweep failed: ${it.localizedMessage}")
        }
        runCatching { stripOrphanVirtualAdapterIps() }
        runCatching { resetAdapterDns("AetherST") }
        runCatching {
            ProcessBuilder("ipconfig", "/flushdns").redirectErrorStream(true).start().waitFor()
        }
    }

    fun removeStaleVirtualRoutes(): Int {
        val proc = ProcessBuilder("route", "print", "-4").redirectErrorStream(true).start()
        val lines = proc.inputStream.bufferedReader().readLines()
        proc.waitFor()

        var removed = 0
        for (line in lines) {
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size < 3) continue
            val destination = cols[0]
            val netmask = cols[1]
            val gateway = cols[2]
            val isStale =
                ((destination == "0.0.0.0" || destination == "128.0.0.0") && netmask == "128.0.0.0") ||
                    (destination == "0.0.0.0" && netmask == "0.0.0.0")
            if (!isStale) continue
            if (virtualGatewayPrefixes.none { gateway.startsWith(it) }) continue
            runCatching {
                ProcessBuilder("route", "delete", destination, "mask", netmask, gateway)
                    .redirectErrorStream(true).start().waitFor()
                removed++
                LogRepository.i("[$TAG] removed stale route $destination mask $netmask via $gateway")
            }
        }
        return removed
    }

    private fun stripOrphanVirtualAdapterIps() {
        val script = "Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | " +
            "Where-Object { (\$_.InterfaceDescription -like '*TAP-*' -or \$_.InterfaceDescription -like '*Wintun*') -and " +
            "(\$_.IPAddress -like '10.98.*' -or \$_.IPAddress -like '198.18.*') } | " +
            "Remove-NetIPAddress -Confirm:\$false -ErrorAction SilentlyContinue"
        val proc = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
            .redirectErrorStream(true).start()
        proc.waitFor()
    }

    private fun killOrphanHelpers() {
        val script = "\$targets = Get-CimInstance Win32_Process -Filter \"Name='pwsh.exe' OR Name='powershell.exe'\" | " +
            "Where-Object { \$_.CommandLine -like '*tun-helper.ps1*' }; " +
            "foreach (\$x in \$targets) { Stop-Process -Id \$x.ProcessId -Force -ErrorAction SilentlyContinue }"
        ProcessBuilder("powershell", "-NoProfile", "-Command", script)
            .redirectErrorStream(true).start().waitFor()
    }

    /**
     * 1.7.1: kills only OUR Hybrid openvpn (its cmdline carries the wrapper config
     * name `openvpn-hybrid.ovpn`). Foreign clients (e.g. OpenVPN Connect) are left alone.
     * Needs no elevation for same-user processes, so it also runs on unelevated startup.
     */    private fun killOurHybridOpenVpn(): Int {
        return try {
            val script = "\$targets = Get-CimInstance Win32_Process -Filter \"Name='openvpn.exe'\" | " +
                "Where-Object { \$_.CommandLine -like '*$HYBRID_MARKER*' }; " +
                "\$n = 0; foreach (\$x in \$targets) { Stop-Process -Id \$x.ProcessId -Force -ErrorAction SilentlyContinue; \$n++ }; \$n"
            val proc = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            (out.toIntOrNull() ?: 0).also {
                if (it > 0) LogRepository.i("[$TAG] killed $it orphan Hybrid openvpn.exe")
            }
        } catch (_: Exception) { 0 }
    }

    /**
     * 1.7.1: sweeps stale core processes from a previous (End-tasked/crashed) session.
     * Binary names are unique to this app; a graceful exit leaves none behind, so
     * anything found here is an orphan holding ports (1819/1820/3080) hostage.
     */
    private fun killOurCoreProcesses(): Int {
        return try {
            val script = "\$n = 0; foreach (\$nm in @('aether','hev-socks5-tunnel','psiphon-helper')) { " +
                "foreach (\$p in (Get-Process -Name \$nm -ErrorAction SilentlyContinue)) { " +
                "try { Stop-Process -Id \$p.Id -Force -ErrorAction Stop; \$n++ } catch {} } }; \$n"
            val proc = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            (out.toIntOrNull() ?: 0).also {
                if (it > 0) LogRepository.i("[$TAG] killed $it orphan core process(es)")
            }
        } catch (_: Exception) { 0 }
    }

    private fun isOurHybridRunning(): Boolean {
        return try {
            val script = "Get-CimInstance Win32_Process -Filter \"Name='openvpn.exe'\" | " +
                "Where-Object { \$_.CommandLine -like '*$HYBRID_MARKER*' } | Select-Object -First 1"
            val proc = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.isNotBlank()
        } catch (_: Exception) { false }
    }

    /**
     * 1.7.1: repairs an HKCU proxy stuck at our dead relay. Only touches it when
     * (a) the proxy is enabled, (b) it points at OUR ports, and (c) nothing
     * listens there anymore (a live session is never disturbed).
     */
    private fun repairStuckSystemProxy() {
        try {
            val base = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
            val enabledOut = runRegQuery(base, "ProxyEnable")
            if (!Regex("""ProxyEnable\s+REG_DWORD\s+0x1""").containsMatchIn(enabledOut)) return
            val serverOut = runRegQuery(base, "ProxyServer")
            if (!serverOut.contains("127.0.0.1:10809") && !serverOut.contains("127.0.0.1:10808")) return
            if (isLoopbackPortListening(10808) || isLoopbackPortListening(10809)) return
            ProcessBuilder("reg", "add", base, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "0", "/f")
                .redirectErrorStream(true).start().waitFor()
            refreshWinInet()
            LogRepository.i("[$TAG] repaired stuck system proxy (was pointing at dead 127.0.0.1 relay)")
        } catch (_: Exception) {
        }
    }

    private fun runRegQuery(key: String, value: String): String {
        return try {
            val proc = ProcessBuilder("reg", "query", key, "/v", value)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out
        } catch (_: Exception) { "" }
    }

    private fun isLoopbackPortListening(port: Int): Boolean {
        return try {
            java.net.Socket("127.0.0.1", port).use { true }
        } catch (_: Exception) { false }
    }

    private fun refreshWinInet() {
        runCatching {
            val ps = "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class WinInet { [DllImport(\"wininet.dll\")] public static extern bool InternetSetOption(IntPtr hInternet, int dwOption, IntPtr lpBuffer, int dwBufferLength); }'; [WinInet]::InternetSetOption([IntPtr]::Zero, 39, [IntPtr]::Zero, 0) | Out-Null; [WinInet]::InternetSetOption([IntPtr]::Zero, 37, [IntPtr]::Zero, 0) | Out-Null"
            ProcessBuilder("powershell", "-WindowStyle", "Hidden", "-Command", ps)
                .start().waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    /**
     * 1.7.1: detection only — restoring adapter DNS needs elevation (done in the
     * elevated part of heal() via clearSystemDns on disconnect), but a stuck
     * 127.0.0.1 here explains total breakage, so warn loudly in the log.
     */
    private fun warnIfDnsStuck() {
        try {
            val proc = ProcessBuilder("powershell", "-NoProfile", "-Command",
                "Get-DnsClientServerAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { \$_.ServerAddresses -contains '127.0.0.1' } | Select-Object -ExpandProperty InterfaceAlias"
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (out.isNotEmpty()) {
                LogRepository.w("[$TAG] system DNS still points at dead local relay on ${out.lineSequence().firstOrNull()}; run the app as administrator once to restore it")
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 1.7.1 (crash path): consumes the snapshot/remote tracking files left by an
     * End-tasked Hybrid session. Same surgical rule as the connector: only
     * session-added /1 on community TAP adapters, plus tracked server /32s.
     * Skipped entirely while OUR tunnel is alive.
     */
    fun removeTrackedHybridRoutes(): Int {
        if (isOurHybridRunning()) return 0
        var removed = 0
        val slashFile = java.io.File(appDataDir(), "openvpn-slashone.txt")
        if (slashFile.exists()) {
            val before = slashFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            for (entry in querySlashOne().toSet() - before) {
                val parts = entry.split("|")
                if (parts.size < 4) continue
                if (!isCommunityTapDesc(parts[3])) continue
                val spec = when (parts[0].trim()) {
                    "0.0.0.0/1" -> arrayOf("0.0.0.0", "mask", "128.0.0.0")
                    "128.0.0.0/1" -> arrayOf("128.0.0.0", "mask", "128.0.0.0")
                    else -> continue
                }
                runCatching {
                    ProcessBuilder("route", "delete", spec[0], spec[1], spec[2], parts[1])
                        .redirectErrorStream(true).start().waitFor()
                    removed++
                }
            }
            val left = querySlashOne().toSet() - before
            val leftOurs = left.filter { it.split("|").getOrNull(3)?.let { d -> isCommunityTapDesc(d) } == true }
            if (leftOurs.isEmpty()) slashFile.delete()
            if (removed > 0) LogRepository.i("[$TAG] removed $removed tracked Hybrid /1 route(s)")
        }
        val remoteF = java.io.File(appDataDir(), "openvpn-remote.txt")
        if (remoteF.exists()) {
            val ipPattern = Regex("""\d+\.\d+\.\d+\.\d+""")
            for (ip in remoteF.readLines().map { it.trim() }.filter { it.matches(ipPattern) }.toSet()) {
                runCatching {
                    ProcessBuilder("route", "delete", ip, "mask", "255.255.255.255")
                        .redirectErrorStream(true).start().waitFor()
                    removed++
                }
            }
            remoteF.delete()
        }
        return removed
    }

    private fun querySlashOne(): List<String> {
        // File-based (not inline -Command): nested quoting in inline scripts breaks
        // silently and error text would poison tracking. Output validator included.
        return try {
            val dir = appDataDir()
            if (!dir.exists()) dir.mkdirs()
            val scriptFile = java.io.File(dir, "slashone-query.ps1")
            scriptFile.writeText(
                "\$r = Get-NetRoute -DestinationPrefix '0.0.0.0/1','128.0.0.0/1' -ErrorAction SilentlyContinue | " +
                    "Select-Object DestinationPrefix,NextHop,InterfaceIndex\n" +
                    "foreach (\$x in \$r) {\n" +
                    "  \$d = ''\n" +
                    "  try { \$d = (Get-NetAdapter -InterfaceIndex \$x.InterfaceIndex -ErrorAction Stop).InterfaceDescription } catch {}\n" +
                    "  '{0}|{1}|{2}|{3}' -f \$x.DestinationPrefix, \$x.NextHop, \$x.InterfaceIndex, \$d\n" +
                    "}\n"
            )
            val proc = ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", scriptFile.absolutePath
            ).redirectErrorStream(false).start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val valid = Regex("""^\S+\|\d+\.\d+\.\d+\.\d+\|\d+\|""")
            text.lineSequence().map { it.trim() }.filter { valid.containsMatchIn(it) }.toList()
        } catch (_: Exception) { emptyList() }
    }

    private fun isCommunityTapDesc(desc: String): Boolean {
        val d = desc.trim()
        return d.equals("TAP-Windows Adapter V9", ignoreCase = true) ||
            d.startsWith("TAP-Windows Adapter V9 #", ignoreCase = true)
    }

    private fun appDataDir(): java.io.File {
        val base = System.getenv("APPDATA") ?: System.getProperty("user.home")
        return java.io.File(base, "AetherST-Tunnel")
    }

    /**
     * 1.7.1 (crash path): if the app died without running stop(), the recorded
     * tunnel gateways survive in `openvpn-gateways.txt`. On next (elevated) startup,
     * delete the hijack routes via exactly those gateways — but only when OUR tunnel
     * is not alive, so a live session is never touched.
     */
    fun removeOrphanHybridRedirectRoutes(): Int {
        if (isOurHybridRunning()) return 0
        val file = java.io.File(appDataDir(), "openvpn-gateways.txt")
        if (!file.exists()) return 0
        val ipPattern = Regex("""\d+\.\d+\.\d+\.\d+""")
        val gateways = file.readLines().map { it.trim() }.filter { it.matches(ipPattern) }.toSet()
        var removed = 0
        for (gw in gateways) {
            for (spec in listOf("0.0.0.0 mask 128.0.0.0", "128.0.0.0 mask 128.0.0.0")) {
                val parts = spec.split(" ")
                runCatching {
                    ProcessBuilder("route", "delete", parts[0], parts[1], parts[2], gw)
                        .redirectErrorStream(true).start().waitFor()
                    removed++
                }
            }
            LogRepository.i("[$TAG] removed orphan Hybrid redirect routes via $gw")
        }
        file.delete()
        return removed
    }

    /**
     * 1.7.1: universally safe backstop — a `/1` hijack route whose interface is gone
     * or down can never carry traffic, regardless of which VPN left it. Live tunnels
     * (interface Up) are never touched.
     */
    fun removeDeadSlashOneRoutes(): Int {
        val script = "\$dead = 0; " +
            "foreach (\$prefix in @('0.0.0.0/1','128.0.0.0/1')) { " +
            "foreach (\$r in (Get-NetRoute -DestinationPrefix \$prefix -ErrorAction SilentlyContinue)) { " +
            "\$ad = Get-NetAdapter -InterfaceIndex \$r.InterfaceIndex -ErrorAction SilentlyContinue; " +
            "if (-not \$ad -or \$ad.Status -ne 'Up') { " +
            "Remove-NetRoute -DestinationPrefix \$r.DestinationPrefix -InterfaceIndex \$r.InterfaceIndex -NextHop \$r.NextHop -Confirm:\$false -ErrorAction SilentlyContinue; " +
            "\$dead++ } } }; \$dead"
        return try {
            val proc = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            (out.toIntOrNull() ?: 0).also {
                if (it > 0) LogRepository.i("[$TAG] removed $it dead-interface /1 routes")
            }
        } catch (_: Exception) { 0 }
    }

    private fun resetAdapterDns(alias: String) {
        val script = "if (Get-NetAdapter -Name '$alias' -ErrorAction SilentlyContinue) { " +
            "Set-DnsClientServerAddress -InterfaceAlias '$alias' -ResetServerAddresses }"
        ProcessBuilder("powershell", "-NoProfile", "-Command", script)
            .redirectErrorStream(true).start().waitFor()
    }
}
