package io.github.immaghzbad.aetherst.core

import android.content.Context
import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.model.AetherConfig
import io.github.immaghzbad.aetherst.model.AetherProtocol
import io.github.immaghzbad.aetherst.model.ConnectionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class AetherProcessRunner(private val context: Context) {

    private val lock = Any()
    private var process: Process? = null
    private var runnerJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val currentAttemptId = AtomicLong(0)
    private var goolOuterValidated = false

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.STOPPED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    fun start(config: AetherConfig, bindAddress: String, onCodeRequired: () -> Unit = {}, inputProvider: suspend () -> String = { "" }) {
        synchronized(lock) {
            if (runnerJob?.isActive == true) return

            val attemptId = currentAttemptId.incrementAndGet()
            runnerJob = scope.launch {
                var retryCount = 0

                while (isActive && (currentAttemptId.get() == attemptId)) {
                    if (config.smartReconnect && (retryCount >= config.reconnectRetryLimit)) {
                        LogRepository.e("Smart Reconnect limit reached ($retryCount). Stopping...")
                        updateState(ConnectionStatus.ERROR, attemptId)
                        break
                    }

                    if (retryCount > 0) {
                        val waitTime = (retryCount * 1000L).coerceAtMost(10000L)
                        LogRepository.i("Recovering connection (Retry $retryCount)...")
                        updateState(ConnectionStatus.RECONNECTING, attemptId)
                        delay(waitTime.milliseconds)
                    } else {
                        LogRepository.i("Starting system core...")
                        updateState(ConnectionStatus.STARTING, attemptId)
                    }

                    if (currentAttemptId.get() != attemptId) break

                    try {
                        val result = runBinary(config, attemptId, bindAddress, onCodeRequired, inputProvider)
                        if (currentAttemptId.get() != attemptId) break
                        
                        if (!result) {
                            LogRepository.e("Stability check failed. Retrying...")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LogRepository.e("Execution cycle critical error: ${e.localizedMessage}")
                    }

                    retryCount++
                    if (connectionStatus.value == ConnectionStatus.RUNNING) {
                        retryCount = 1
                    }
                }
            }
        }
    }

    private suspend fun runBinary(config: AetherConfig, attemptId: Long, bindAddress: String, onCodeRequired: () -> Unit, inputProvider: suspend () -> String): Boolean = coroutineScope {
        var proc: Process? = null
        try {
            val binaryFile = BinaryManager.prepareBinary(context)
            if (currentAttemptId.get() != attemptId) return@coroutineScope true

            val commandList = mutableListOf<String>()
            commandList.add(binaryFile.absolutePath)
            commandList.add("--bind")
            commandList.add(bindAddress)

            val routingFile = writeRoutingFile(config)
            if (routingFile != null) {
                commandList.add("--routes")
                commandList.add(routingFile.absolutePath)
            }

            if (config.protocol == AetherProtocol.ZERO_TRUST) {
                commandList.add("--team")
                commandList.add(config.teamName.ifEmpty { "unnamed" })
            }

            commandList.add(
                when (config.ipMode) {
                    io.github.immaghzbad.aetherst.model.AetherIpMode.IPV4 -> "-4"
                    io.github.immaghzbad.aetherst.model.AetherIpMode.IPV6 -> "-6"
                    io.github.immaghzbad.aetherst.model.AetherIpMode.DUAL -> "--dual"
                }
            )

            if (config.h2Mode) commandList.add("--h2")
            if (config.h2Fragment) {
                commandList.add("--fragment")
                commandList.add("--fragment-size")
                commandList.add(config.fragmentSize)
                commandList.add("--fragment-delay")
                commandList.add(config.fragmentDelay)
            }
            if (config.noDataCheck) commandList.add("--no-data-check")
            if (config.quickReconnect) commandList.add("--quick-reconnect") else commandList.add("--no-quick-reconnect")

            if (config.peer.isNotEmpty()) {
                commandList.add("--peer")
                commandList.add(config.peer)
            }

            if ((config.protocol == AetherProtocol.WG) || (config.protocol == AetherProtocol.GOOL)) {
                commandList.add("--keepalive")
                commandList.add(config.keepalive.toString())
            }

            if (config.tlsGroups.isNotEmpty()) {
                commandList.add("--tls-groups")
                commandList.add(config.tlsGroups)
            }

            commandList.add("--validate-secs")
            commandList.add(config.validateSecs.toString())
            
            commandList.add("--reconnect-secs")
            commandList.add(config.reconnectSecs.toString())

            if (config.noProfileRetry) commandList.add("--no-profile-retry")

            if (config.teamName.isNotEmpty() && config.protocol != AetherProtocol.ZERO_TRUST) {
                commandList.add("--team")
                commandList.add(config.teamName)
            }
            if (config.accessEmail.isNotEmpty()) {
                commandList.add("--access-email")
                commandList.add(config.accessEmail)
            }
            if (config.accessId.isNotEmpty()) {
                commandList.add("--access-id")
                commandList.add(config.accessId)
            }
            if (config.accessSecret.isNotEmpty()) {
                commandList.add("--access-secret")
                commandList.add(config.accessSecret)
            }
            if (config.accessToken.isNotEmpty()) {
                commandList.add("--access-token")
                commandList.add(config.accessToken)
            }
            if (config.useGateway) {
                commandList.add("--gateway")
            }

            if (config.dnsList.isNotEmpty()) {
                commandList.add("--dns")
                commandList.add(config.dnsList)
            }

            val pb = ProcessBuilder(commandList)
            pb.directory(context.filesDir)

            val env = pb.environment()
            env["AETHER_PROTOCOL"] = config.protocol.rawValue
            env["AETHER_NOIZE"] = config.noise.rawValue
            env["AETHER_SCAN"] = config.scanMode.rawValue
            env["AETHER_IP"] = config.ipMode.rawValue
            env["AETHER_SOCKS"] = bindAddress

            if (routingFile != null) {
                env["AETHER_ROUTES_FILE"] = routingFile.absolutePath
            }

            if (config.h2Mode) env["AETHER_MASQUE_HTTP2"] = "1"
            if (config.h2Fragment) {
                env["AETHER_MASQUE_H2_FRAGMENT"] = "1"
                env["AETHER_MASQUE_H2_FRAGMENT_SIZE"] = config.fragmentSize
                env["AETHER_MASQUE_H2_FRAGMENT_DELAY"] = config.fragmentDelay
            }

            if (config.noDataCheck) {
                env["AETHER_MASQUE_NO_DATA_CHECK"] = "1"
                env["AETHER_WG_NO_DATA_CHECK"] = "1"
            }

            if (config.quickReconnect) env["AETHER_QUICK_RECONNECT"] = "1" else env["AETHER_QUICK_RECONNECT"] = "0"

            if (config.peer.isNotEmpty()) {
                env["AETHER_PEER"] = config.peer
                env["AETHER_WG_PEER"] = config.peer
            }

            env["AETHER_WG_KEEPALIVE"] = config.keepalive.toString()
            env["AETHER_MASQUE_VALIDATE_SECS"] = config.validateSecs.toString()
            env["AETHER_MASQUE_RECONNECT_SECS"] = config.reconnectSecs.toString()
            env["AETHER_WG_RECONNECT_SECS"] = config.reconnectSecs.toString()

            if (config.noProfileRetry) env["AETHER_WG_NO_PROFILE_RETRY"] = "1"
            if (config.tlsGroups.isNotEmpty()) env["AETHER_TLS_GROUPS"] = config.tlsGroups

            if (config.teamName.isNotEmpty()) env["AETHER_TEAM"] = config.teamName
            if (config.accessEmail.isNotEmpty()) env["AETHER_ACCESS_EMAIL"] = config.accessEmail
            if (config.accessId.isNotEmpty()) env["AETHER_ACCESS_ID"] = config.accessId
            if (config.accessSecret.isNotEmpty()) env["AETHER_ACCESS_SECRET"] = config.accessSecret
            if (config.accessToken.isNotEmpty()) env["AETHER_ACCESS_TOKEN"] = config.accessToken
            if (config.useGateway) env["AETHER_GATEWAY"] = "1"
            if (config.dnsList.isNotEmpty()) env["AETHER_DNS"] = config.dnsList

            pb.redirectErrorStream(true)

            proc = withContext(Dispatchers.IO) { pb.start() }

            synchronized(lock) {
                if (currentAttemptId.get() != attemptId) {
                    proc?.destroyForcibly()
                    return@coroutineScope true
                }
                process = proc
            }

            val inputJob = launch {
                val writer = BufferedWriter(OutputStreamWriter(proc!!.outputStream))
                try {
                    while (isActive) {
                        val text = inputProvider()
                        if (text.isNotEmpty()) {
                            writer.write(text)
                            writer.newLine()
                            writer.flush()
                            LogRepository.d("Sent input to binary")
                        }
                    }
                } catch (_: CancellationException) {
                } catch (exception: Exception) {
                    if (currentCoroutineContext().isActive && currentAttemptId.get() == attemptId) {
                        LogRepository.w("Process input pipe closed: ${exception.localizedMessage}")
                    }
                }
            }

            BufferedReader(InputStreamReader(proc!!.inputStream)).use { reader ->
                var line: String?
                while (currentCoroutineContext().isActive && (currentAttemptId.get() == attemptId)) {
                    line = try {
                        reader.readLine()
                    } catch (e: java.io.IOException) {
                        if (currentAttemptId.get() != attemptId) null else throw e
                    } ?: break

                    parseOutputLine(line, attemptId, config.protocol, onCodeRequired)
                }
            }

            inputJob.cancel()
            val exitCode = try { withContext(Dispatchers.IO) { proc.waitFor() } } catch (_: Exception) { -1 }
            if (currentAttemptId.get() == attemptId) {
                LogRepository.i("Core process terminated (Exit code: $exitCode)")
            }
            exitCode == 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (currentAttemptId.get() == attemptId) {
                LogRepository.e("Binary runtime error: ${e.localizedMessage}")
                return@coroutineScope false
            }
            true
        } finally {
            synchronized(lock) {
                if (process === proc) process = null
            }
            try { proc?.destroyForcibly() } catch (_: Exception) {}
        }
    }

    private fun parseOutputLine(line: String, attemptId: Long, protocol: AetherProtocol, onCodeRequired: () -> Unit) {
        if (currentAttemptId.get() != attemptId) return

        val lower = line.lowercase()
        when {
            lower.contains(" error ") || lower.contains("[error]") -> LogRepository.e(line, "AetherCore")
            lower.contains(" warn ") || lower.contains("[warn]") -> LogRepository.w(line, "AetherCore")
            else -> LogRepository.i(line, "AetherCore")
        }

        if (lower.contains("enter code:") || lower.contains("login code required")) {
            onCodeRequired()
            return
        }

        val isCriticalError = (lower.contains("fatal") || lower.contains("panic")) &&
                !lower.contains("socksbridge") &&
                !lower.contains("connection failed")
        
        val isLost = lower.contains("tunnel lost") || 
                     lower.contains("handshake timeout") || 
                     lower.contains("handshake failed") ||
                     lower.contains("connection refused") ||
                     lower.contains("all gateways failed")

        when {
            lower.contains("scanning") -> {
                goolOuterValidated = false
                updateState(ConnectionStatus.STARTING, attemptId)
            }
            lower.contains("validating") -> updateState(ConnectionStatus.VALIDATING, attemptId)

            protocol == AetherProtocol.MASQUE && (
                lower.contains("tunnel validated") ||
                lower.contains("data-plane verification passed") ||
                lower.contains("data plane verification passed") ||
                lower.contains("connect-ip status: 200") ||
                lower.contains("connect-ip established") ||
                (lower.contains("socks") && lower.contains("listening"))
            ) -> {
                updateState(ConnectionStatus.RUNNING, attemptId)
            }

            protocol == AetherProtocol.WG && (
                lower.contains("wireguard tunnel validated") ||
                lower.contains("wireguard handshake complete") ||
                (lower.contains("socks") && lower.contains("listening"))
            ) -> {
                updateState(ConnectionStatus.RUNNING, attemptId)
            }

            protocol == AetherProtocol.GOOL -> {
                if (lower.contains("outer") && lower.contains("tunnel validated")) {
                    goolOuterValidated = true
                }
                if (lower.contains("inner") && lower.contains("tunnel validated") && goolOuterValidated) {
                    updateState(ConnectionStatus.RUNNING, attemptId)
                }
            }

            protocol != AetherProtocol.MASQUE && protocol != AetherProtocol.WG && protocol != AetherProtocol.GOOL &&
            (lower.contains("tunnel validated") || lower.contains("connect-ip status: 200")) -> {
                updateState(ConnectionStatus.RUNNING, attemptId)
            }

            lower.contains("reconnecting") || isLost -> {
                goolOuterValidated = false
                updateState(ConnectionStatus.RECONNECTING, attemptId)
            }
            isCriticalError -> {
                val current = _connectionStatus.value
                if (current != ConnectionStatus.RUNNING && current != ConnectionStatus.RECONNECTING) {
                    updateState(ConnectionStatus.ERROR, attemptId)
                }
            }
        }
    }

    private fun updateState(state: ConnectionStatus, attemptId: Long = currentAttemptId.get()) {
        if (currentAttemptId.get() == attemptId) {
            _connectionStatus.value = state
        }
    }

    private fun writeRoutingFile(config: AetherConfig): java.io.File? {
        val rules = config.routingRules
        val block = rules.filter { it.mode == io.github.immaghzbad.aetherst.model.RoutingMode.BLOCK }

        if (block.isEmpty()) return null

        return try {
            val file = java.io.File(context.filesDir, "routing.ast")
            val content = StringBuilder()

            if (block.isNotEmpty()) {
                content.append("[block]\n")
                block.forEach { content.append(formatRoutingPattern(it.pattern)).append("\n") }
                content.append("\n")
            }

            file.writeText(content.toString())
            file
        } catch (e: Exception) {
            LogRepository.e("Failed to write routing file: ${e.localizedMessage}")
            null
        }
    }

    private fun formatRoutingPattern(pattern: String): String {
        val trimmed = pattern.trim()
        if (trimmed.startsWith("domain:") || trimmed.startsWith("ip:") || 
            trimmed.startsWith("keyword:") || trimmed.startsWith("regexp:") ||
            trimmed == "private") {
            return trimmed
        }

        val isIp = trimmed.all { it.isDigit() || it == '.' || it == ':' || it == '/' || (it.lowercaseChar() in 'a'..'f') } &&
                (trimmed.contains('.') || trimmed.contains(':'))
        
        return if (isIp) "ip:$trimmed" else "domain:$trimmed"
    }

    fun stop() {
        currentAttemptId.incrementAndGet()
        _connectionStatus.value = ConnectionStatus.STOPPED

        var jobToCancel: Job? = null
        var procToDestroy: Process? = null

        synchronized(lock) {
            jobToCancel = runnerJob
            procToDestroy = process
            runnerJob = null
            process = null
        }

        jobToCancel?.cancel()
        try {
            procToDestroy?.destroyForcibly()
        } catch (_: Exception) {}

        LogRepository.i("System core shutdown initiated.")
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
