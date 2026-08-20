package io.github.immaghzbad.aetherst.ui

import io.github.immaghzbad.aetherst.core.ConnectionController
import io.github.immaghzbad.aetherst.core.NetworkClient
import io.github.immaghzbad.aetherst.core.TunHelper
import io.github.immaghzbad.aetherst.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.data.IpInfo
import io.github.immaghzbad.aetherst.data.IpInfoRepository
import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.data.PingRepository
import io.github.immaghzbad.aetherst.data.PingState
import io.github.immaghzbad.aetherst.desktop.APP_VERSION
import io.github.immaghzbad.aetherst.desktop.AppPaths
import io.github.immaghzbad.aetherst.desktop.AetherTray
import io.github.immaghzbad.aetherst.model.AetherConfig
import io.github.immaghzbad.aetherst.model.AetherProtocol
import io.github.immaghzbad.aetherst.model.ConnectionMode
import io.github.immaghzbad.aetherst.model.ConnectionStatus
import io.github.immaghzbad.aetherst.model.LogEntry
import io.github.immaghzbad.aetherst.model.RoutingMode
import io.github.immaghzbad.aetherst.model.RoutingRule
import io.github.immaghzbad.aetherst.model.UpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JFileChooser
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class AppNavigation {
    SETTINGS,
    ROUTING
}

class AetherViewModel {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository = AetherConfigRepository.getInstance()
    private val lastToggleAt = AtomicLong(0)

    private val _navigationRequest = MutableStateFlow<AppNavigation?>(null)
    val navigationRequest: StateFlow<AppNavigation?> = _navigationRequest.asStateFlow()

    fun requestNavigation(destination: AppNavigation) {
        _navigationRequest.value = destination
    }

    fun clearNavigation() {
        _navigationRequest.value = null
    }

    val config: StateFlow<AetherConfig> = repository.config
    val isOnboardingComplete: StateFlow<Boolean> = repository.isOnboardingComplete
    val connectionStatus: StateFlow<ConnectionStatus> = ConnectionController.status
    val elapsedSeconds: StateFlow<Long> = ConnectionController.elapsedSeconds
    val sessionTraffic = ConnectionController.sessionTraffic
    val isWaitingForLoginCode = ConnectionController.getInstance().isWaitingForCode
    val logs: StateFlow<List<LogEntry>> = LogRepository.logs
    val ipInfo: StateFlow<IpInfo> = IpInfoRepository.ipInfo
    val pingState: StateFlow<PingState> = PingRepository.pingState

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _importConflictRules = MutableStateFlow<List<RoutingRule>?>(null)
    val importConflictRules: StateFlow<List<RoutingRule>?> = _importConflictRules.asStateFlow()

    private val _importErrorMessage = MutableStateFlow<String?>(null)
    val importErrorMessage: StateFlow<String?> = _importErrorMessage.asStateFlow()

    private val _scrollToZeroTrust = MutableStateFlow(false)
    val scrollToZeroTrust: StateFlow<Boolean> = _scrollToZeroTrust.asStateFlow()

    data class ToastState(val message: String, val isError: Boolean = false)
    private val _toastState = MutableStateFlow<ToastState?>(null)
    val toastState: StateFlow<ToastState?> = _toastState.asStateFlow()

    private val _isOptimizingMtu = MutableStateFlow(false)
    val isOptimizingMtu: StateFlow<Boolean> = _isOptimizingMtu.asStateFlow()

    private val _crashLog = MutableStateFlow<String?>(null)
    val crashLog: StateFlow<String?> = _crashLog.asStateFlow()

    init {
        LogRepository.initialize()
        checkForUpdates()
        observeConnectionStatus()
        checkLastCrash()
    }

    private fun checkLastCrash() {
        viewModelScope.launch {
            val file = File(AppPaths.cacheDir, "last_crash.log")
            if (file.exists()) {
                val log = file.readText()
                if (log.isNotEmpty()) {
                    _crashLog.value = log
                }
            }
        }
    }

    fun clearCrashLog() {
        val file = File(AppPaths.cacheDir, "last_crash.log")
        if (file.exists()) file.delete()
        _crashLog.value = null
    }

    fun toggleConnection() {
        val now = System.currentTimeMillis()

        while (true) {
            val previous = lastToggleAt.get()
            if ((now - previous) < 450L) return
            if (lastToggleAt.compareAndSet(previous, now)) break
        }

        val config = repository.config.value
        if (config.protocol == AetherProtocol.ZERO_TRUST) {
            if (config.teamName.isEmpty() || config.accessEmail.isEmpty()) {
                showToast("Please complete Zero Trust settings", true)
                _scrollToZeroTrust.value = true
                return
            }
        }

        val currentState = connectionStatus.value
        if (currentState == ConnectionStatus.STOPPING) return

        try {
            if ((currentState == ConnectionStatus.STOPPED) || (currentState == ConnectionStatus.ERROR)) {
                val controller = ConnectionController.getInstance()
                viewModelScope.launch { controller.start() }
            } else {
                val controller = ConnectionController.getInstance()
                viewModelScope.launch { controller.stop() }
            }
        } catch (exception: Exception) {
            LogRepository.e("[UI] Connection toggle failed: ${exception.localizedMessage}")
        }
    }

    fun shutdown() {
        runBlocking {
            withContext(Dispatchers.Default) {
                try {
                    withTimeoutOrNull(4000.milliseconds) {
                        ConnectionController.getInstance().stop()
                    }
                } catch (_: Exception) {
                }
                TunHelper.stop()
                sweepChildProcesses()
            }
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
        runCatching {
            ProcessBuilder("taskkill", "/F", "/T", "/IM", "AetherST.exe")
                .redirectErrorStream(true).start().waitFor(3, TimeUnit.SECONDS)
        }
    }

    fun updateConfig(newConfig: AetherConfig) {
        val oldConfig = repository.config.value
        repository.updateConfig(newConfig)

        if (oldConfig.connectionMode != newConfig.connectionMode) {
            switchMode(oldConfig.connectionMode, newConfig.connectionMode)
        }
    }

    private fun switchMode(oldMode: ConnectionMode, newMode: ConnectionMode) {
        val state = connectionStatus.value
        if (state == ConnectionStatus.STOPPED || state == ConnectionStatus.ERROR || state == ConnectionStatus.STOPPING) return

        viewModelScope.launch {
            ConnectionController.getInstance().stop()

            withTimeoutOrNull(5.seconds) {
                connectionStatus.first { it == ConnectionStatus.STOPPED || it == ConnectionStatus.ERROR }
                true
            }

            delay(500.milliseconds)
            ConnectionController.getInstance().start()
        }
    }

    fun addRoutingRule(pattern: String, mode: RoutingMode) {
        val current = config.value
        if (current.routingRules.any { it.pattern == pattern }) return

        val newList = current.routingRules + RoutingRule(pattern, mode)
        LogRepository.i("Routing rule added: $pattern ($mode)")
        updateConfig(current.copy(routingRules = newList))
        restartConnectionIfActive()
    }

    fun removeRoutingRule(pattern: String) {
        val current = config.value
        val newList = current.routingRules.filter { it.pattern != pattern }
        if (newList.size == current.routingRules.size) return

        LogRepository.i("Routing rule removed: $pattern")
        updateConfig(current.copy(routingRules = newList))
        restartConnectionIfActive()
    }

    fun updateRoutingRuleMode(pattern: String, mode: RoutingMode) {
        val current = config.value
        val newList = current.routingRules.map {
            if (it.pattern == pattern) it.copy(mode = mode) else it
        }
        if (newList == current.routingRules) return

        LogRepository.i("Routing rule updated: $pattern -> $mode")
        updateConfig(current.copy(routingRules = newList))
        restartConnectionIfActive()
    }

    fun clearAllRoutingRules() {
        val current = config.value
        if (current.routingRules.isEmpty()) return
        LogRepository.i("All routing rules cleared")
        updateConfig(current.copy(routingRules = emptyList()))
        restartConnectionIfActive()
    }

    fun resetAllSettings() {
        repository.resetToDefaults()
        restartConnectionIfActive()
    }

    fun optimizeMtu() {
        if (_isOptimizingMtu.value) return
        _isOptimizingMtu.value = true

        viewModelScope.launch {
            showToast("Probing network for optimal MTU...")

            val result = withContext(Dispatchers.IO) {
                try {
                    val overhead = when (config.value.protocol) {
                        AetherProtocol.WG, AetherProtocol.GOOL -> 80
                        AetherProtocol.MASQUE -> 60
                        else -> 40
                    }

                    fun testMtu(size: Int): Boolean {
                        val payloadSize = size - 28
                        val targets = listOf("1.1.1.1", "8.8.8.8")
                        for (target in targets) {
                            try {
                                val proc = Runtime.getRuntime().exec("ping -n 2 -l $payloadSize -w 1000 $target")
                                if (proc.waitFor() == 0) return true
                            } catch (_: Exception) {}
                        }
                        return false
                    }

                    var low = 1280
                    var high = 1500
                    var bestUnderlyingMtu = 1280

                    while (low <= high) {
                        val mid = (low + high) / 2
                        if (testMtu(mid)) {
                            bestUnderlyingMtu = mid
                            low = mid + 1
                        } else {
                            high = mid - 1
                        }
                        delay(50.milliseconds)
                    }

                    (bestUnderlyingMtu - overhead).coerceIn(1100, 1420)
                } catch (_: Exception) {
                    null
                } finally {
                    _isOptimizingMtu.value = false
                }
            }

            if (result != null) {
                val current = config.value
                if (current.mtu == result) {
                    showToast("Current MTU is already optimal ($result)")
                } else {
                    updateConfig(current.copy(mtu = result))
                    showToast("Optimal MTU discovered and applied: $result")
                    restartConnectionIfActive()
                }
            } else {
                showToast("MTU probe failed, using safe default", true)
                updateConfig(config.value.copy(mtu = 1280))
            }
        }
    }

    fun clearImportError() {
        _importErrorMessage.value = null
    }

    fun onZeroTrustScrolled() {
        _scrollToZeroTrust.value = false
    }

    fun submitLoginCode(code: String) {
        ConnectionController.getInstance().submitLoginCode(code)
    }

    private var toastJob: Job? = null

    fun showToast(message: String, isError: Boolean = false) {
        toastJob?.cancel()
        _toastState.value = ToastState(message, isError)
        toastJob = viewModelScope.launch {
            delay(5000.milliseconds)
            _toastState.value = null
        }
    }

    fun cleanRoutingPattern(input: String): String {
        var pattern = input.trim()
        if (pattern.startsWith("http://", ignoreCase = true)) pattern = pattern.substring(7)
        if (pattern.startsWith("https://", ignoreCase = true)) pattern = pattern.substring(8)
        while (pattern.endsWith("/")) pattern = pattern.dropLast(1)
        return pattern
    }

    fun isValidRoutingPattern(pattern: String): Boolean {
        if (pattern.isEmpty()) return false
        if (pattern.startsWith("regexp:")) return true
        val regex = Regex("^[a-zA-Z0-9.*:\\-/]+$")
        return regex.matches(pattern)
    }

    private fun normalizeImportedRoutingPattern(input: String): String {
        val cleaned = cleanRoutingPattern(input)
        return if (cleaned.startsWith("regexp:", ignoreCase = true)) {
            "regexp:${cleaned.substringAfter(':')}"
        } else {
            cleaned.lowercase(Locale.ROOT)
        }
    }

    private fun isValidImportedRoutingPattern(pattern: String): Boolean {
        if (!isValidRoutingPattern(pattern)) return false
        if (!pattern.startsWith("regexp:", ignoreCase = true)) return true
        val expression = pattern.substringAfter(':')
        return expression.isNotEmpty() && runCatching { Regex(expression) }.isSuccess
    }

    private fun routingPatternKey(pattern: String): String {
        val cleaned = cleanRoutingPattern(pattern)
        return if (cleaned.startsWith("regexp:", ignoreCase = true)) {
            "regexp:${cleaned.substringAfter(':')}"
        } else {
            cleaned.lowercase(Locale.ROOT)
        }
    }

    fun exportRoutingRules() {
        viewModelScope.launch {
            val rules = config.value.routingRules
            if (rules.isEmpty()) {
                showToast("No routing rules to export", true)
                return@launch
            }

            val content = StringBuilder()
            rules.forEach { rule ->
                content.append(rule.pattern).append("\n")
                content.append("-").append(rule.mode.name.lowercase()).append("\n")
            }

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val chooser = JFileChooser()
            chooser.selectedFile = File("backup-routing-$timestamp.astb")
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                if (!file.name.lowercase().endsWith(".astb")) {
                    File(file.parentFile, file.name + ".astb").writeText(content.toString())
                } else {
                    file.writeText(content.toString())
                }
                showToast("Routing rules exported")
            }
        }
    }

    fun importRoutingRules() {
        viewModelScope.launch {
            try {
                val chooser = JFileChooser()
                chooser.fileFilter = object : javax.swing.filechooser.FileFilter() {
                    override fun accept(f: File): Boolean = f.isDirectory || f.name.lowercase().endsWith(".astb")
                    override fun getDescription(): String = "AetherST Routing Backup (*.astb)"
                }
                if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@launch

                val fileName = chooser.selectedFile.name

                if (!fileName.lowercase().endsWith(".astb")) {
                    _importErrorMessage.value = "Invalid file type. Please use .astb"
                    return@launch
                }

                val content = chooser.selectedFile.readText()
                val newRules = parseRoutingBackup(content) ?: return@launch
                val currentRules = config.value.routingRules
                val currentPatternKeys = currentRules.mapTo(mutableSetOf()) { routingPatternKey(it.pattern) }
                val hasConflict = newRules.any { routingPatternKey(it.pattern) in currentPatternKeys }

                if (hasConflict) {
                    _importConflictRules.value = newRules
                } else {
                    applyImport(newRules, merge = true)
                }
            } catch (e: Exception) {
                LogRepository.e("Import failed: ${e.localizedMessage}")
                _importErrorMessage.value = "Import failed: Check file content"
            }
        }
    }

    fun importInternalRules(resourceName: String) {
        viewModelScope.launch {
            try {
                val stream = AetherViewModel::class.java.classLoader?.getResourceAsStream("rules/$resourceName")
                    ?: run {
                        _importErrorMessage.value = "Internal rules file not found"
                        return@launch
                    }
                val content = stream.bufferedReader().use { it.readText() }
                val newRules = parseRoutingBackup(content) ?: return@launch

                val currentRules = config.value.routingRules
                val currentPatternKeys = currentRules.mapTo(mutableSetOf()) { routingPatternKey(it.pattern) }
                val hasConflict = newRules.any { routingPatternKey(it.pattern) in currentPatternKeys }

                if (hasConflict) {
                    _importConflictRules.value = newRules
                } else {
                    applyImport(newRules, merge = true)
                }
                showToast("Rules imported from internal storage")
            } catch (e: Exception) {
                LogRepository.e("Internal rules import failed: ${e.localizedMessage}")
                _importErrorMessage.value = "Import failed: Check file content"
            }
        }
    }

    private fun parseRoutingBackup(content: String): List<RoutingRule>? {
        val lines = content.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

        if (lines.size % 2 != 0) {
            _importErrorMessage.value = "Invalid format: Incomplete pairs"
            return null
        }

        val importedRulesByPattern = linkedMapOf<String, RoutingRule>()
        for (i in lines.indices step 2) {
            val rawPattern = lines[i]
            val modeLine = lines[i + 1]

            if (rawPattern.startsWith("-")) {
                _importErrorMessage.value = "Invalid format: Pattern expected at line ${i + 1}"
                return null
            }
            val pattern = normalizeImportedRoutingPattern(rawPattern)
            if (!isValidImportedRoutingPattern(pattern)) {
                _importErrorMessage.value = "Invalid pattern at line ${i + 1}"
                return null
            }
            if (!modeLine.startsWith("-")) {
                _importErrorMessage.value = "Invalid format: Mode prefix (-) missing at line ${i + 2}"
                return null
            }

            val mode = when (val modeStr = modeLine.substring(1).lowercase()) {
                "direct" -> RoutingMode.DIRECT
                "block" -> RoutingMode.BLOCK
                "tunnel" -> RoutingMode.TUNNEL
                else -> {
                    _importErrorMessage.value = "Invalid format: Unknown mode '$modeStr'"
                    return null
                }
            }
            importedRulesByPattern[routingPatternKey(pattern)] = RoutingRule(pattern, mode)
        }

        val newRules = importedRulesByPattern.values.toList()
        if (newRules.isEmpty()) {
            _importErrorMessage.value = "Backup file is empty"
            return null
        }
        return newRules
    }

    fun exportFullBackup() {
        viewModelScope.launch {
            val json = repository.getFullConfigJson()
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val chooser = JFileChooser()
            chooser.selectedFile = File("full-backup-$timestamp.astf")
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                val target = if (!file.name.lowercase().endsWith(".astf")) {
                    File(file.parentFile, file.name + ".astf")
                } else {
                    file
                }
                target.writeText(json)
                showToast("Full backup saved")
            }
        }
    }

    fun importFullBackup() {
        viewModelScope.launch {
            try {
                val chooser = JFileChooser()
                chooser.fileFilter = object : javax.swing.filechooser.FileFilter() {
                    override fun accept(f: File): Boolean = f.isDirectory || f.name.lowercase().endsWith(".astf")
                    override fun getDescription(): String = "AetherST Full Backup (*.astf)"
                }
                if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@launch

                val json = chooser.selectedFile.readText()
                if (repository.restoreFullConfig(json)) {
                    showToast("Full backup restored successfully")
                    restartConnectionIfActive()
                } else {
                    showToast("Invalid backup file", true)
                }
            } catch (e: Exception) {
                showToast("Import failed: ${e.localizedMessage}", true)
            }
        }
    }

    fun resolveConflict(rules: List<RoutingRule>, replace: Boolean) {
        _importConflictRules.value = null
        applyImport(rules, merge = !replace)
    }

    fun cancelImport() {
        _importConflictRules.value = null
    }

    private fun applyImport(newRules: List<RoutingRule>, merge: Boolean) {
        val current = config.value
        val finalRules = if (merge) {
            val existingPatterns = current.routingRules.mapTo(mutableSetOf()) { routingPatternKey(it.pattern) }
            current.routingRules + newRules.filter { routingPatternKey(it.pattern) !in existingPatterns }
        } else {
            newRules
        }
        updateConfig(current.copy(routingRules = finalRules))
        restartConnectionIfActive()
    }

    fun applyPreset(presetId: String) {
        repository.applyPreset(presetId)
    }

    fun refreshIpInfo() {
        viewModelScope.launch {
            val state = connectionStatus.value
            if (state == ConnectionStatus.RUNNING) {
                val cfg = config.value
                IpInfoRepository.fetchIpInfo(cfg.socksHost, cfg.socksPort.toIntOrNull() ?: 1819, useProxy = true)
            } else {
                IpInfoRepository.fetchIpInfo(useProxy = false)
            }
        }
    }

    fun refreshPing() {
        viewModelScope.launch {
            val state = connectionStatus.value
            if (state == ConnectionStatus.RUNNING) {
                val cfg = config.value
                PingRepository.runPing(cfg.socksHost, cfg.socksPort.toIntOrNull() ?: 1819, useProxy = true)
            } else {
                PingRepository.runPing(useProxy = false)
            }
        }
    }

    fun clearLogs() {
        LogRepository.clear()
    }

    fun copyLogs() {
        LogRepository.copyToClipboard()
    }

    private fun restartConnectionIfActive() {
        val state = connectionStatus.value
        if (state == ConnectionStatus.STOPPED || state == ConnectionStatus.ERROR || state == ConnectionStatus.STOPPING) return

        viewModelScope.launch {
            val controller = ConnectionController.getInstance()
            controller.stop()

            val stopped = withTimeoutOrNull(3500.milliseconds) {
                connectionStatus.first { it == ConnectionStatus.STOPPED || it == ConnectionStatus.ERROR }
                true
            } == true

            if (!stopped) return@launch

            delay(300.milliseconds)
            controller.start()
        }
    }

    private fun observeConnectionStatus() {
        viewModelScope.launch {
            connectionStatus.collect { state ->
                AetherTray.setConnectionState(state == ConnectionStatus.RUNNING)
                when (state) {
                    ConnectionStatus.RUNNING -> {
                        val cfg = config.value
                        val host = cfg.socksHost
                        val port = cfg.socksPort.toIntOrNull() ?: 1819
                        LogRepository.i("[Health] Fetching public IP via SOCKS5 ($host:$port)", "UI")
                        viewModelScope.launch { IpInfoRepository.fetchIpInfo(host, port, useProxy = true) }
                        viewModelScope.launch { PingRepository.runPing(host, port, useProxy = true) }
                    }

                    ConnectionStatus.STOPPED -> {
                        viewModelScope.launch { IpInfoRepository.fetchIpInfo(useProxy = false) }
                        viewModelScope.launch { PingRepository.runPing(useProxy = false) }
                    }

                    else -> {}
                }
            }
        }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val request = okhttp3.Request.Builder()
                        .url("https://raw.githubusercontent.com/Mehrdad-esn/AetherST-Windows/main/update.json")
                        .build()

                    NetworkClient.instance.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val jsonStr = response.body?.string() ?: return@withContext null
                            val json = JSONObject(jsonStr)

                            UpdateInfo(
                                version = json.getString("version"),
                                versionCode = json.getInt("version_code"),
                                isBeta = json.getBoolean("is_beta"),
                                changelog = json.getString("changelog"),
                                releaseUrl = json.getString("release_url")
                            )
                        } else {
                            null
                        }
                    }
                }

                if (info != null) {
                    val currentVersion = APP_VERSION

                    if (info.version != currentVersion) {
                        _updateInfo.value = info
                    }
                }
            } catch (e: Exception) {
                LogRepository.w("Update check failed: ${e.localizedMessage}")
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }
}