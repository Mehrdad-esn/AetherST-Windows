package io.github.immaghzbad.aetherst.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.immaghzbad.aetherst.desktop.AppPaths
import io.github.immaghzbad.aetherst.model.AetherLogLevel
import io.github.immaghzbad.aetherst.model.LogEntry
import io.github.immaghzbad.aetherst.model.LogLevel
import io.github.immaghzbad.aetherst.model.logIdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    @Volatile
    var currentAppLogLevel: AetherLogLevel = AetherLogLevel.INFO
    @Volatile
    var currentCoreLogLevel: AetherLogLevel = AetherLogLevel.INFO

    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val logListAdapter = moshi.adapter<List<LogEntry>>(
        Types.newParameterizedType(List::class.java, LogEntry::class.java)
    )

    private val sensitivePatterns = listOf(
        "access_token", "cert_pem", "key_pem", "private_key",
        "wg_private_key", "wg_peer_public_key", "client_id",
        "Authorization", "Bearer"
    )

    fun initialize() {
        if (_logs.value.isNotEmpty()) return
        runCatching {
            val file = AppPaths.logsFile()
            if (file.exists()) {
                val savedLogsJson = file.readText()
                if (savedLogsJson.isNotEmpty()) {
                    val loadedLogs = logListAdapter.fromJson(savedLogsJson)
                    if (loadedLogs != null) {
                        _logs.value = loadedLogs
                        val maxId = loadedLogs.maxOfOrNull { it.id } ?: 0
                        logIdGenerator.set(maxId + 1)
                    }
                }
            }
        }
    }

    fun log(level: LogLevel, message: String, tag: String = "AetherSystem") {
        val isCore = tag == "AetherCore" || tag == "AetherRegistration"
        val configLevel = if (isCore) currentCoreLogLevel else currentAppLogLevel

        val shouldLog = when (configLevel) {
            AetherLogLevel.OFF -> false
            AetherLogLevel.ERROR -> level == LogLevel.ERROR
            AetherLogLevel.WARN -> level == LogLevel.WARN || level == LogLevel.ERROR
            AetherLogLevel.INFO -> level == LogLevel.INFO || level == LogLevel.WARN || level == LogLevel.ERROR
            AetherLogLevel.DEBUG -> true
        }

        if (!shouldLog) return

        val sanitizedMessage = sanitize(message)
        val formattedTime = synchronized(timeFormatter) {
            try {
                timeFormatter.format(Date())
            } catch (_: Exception) {
                ""
            }
        }

        val entry = LogEntry(
            timestamp = formattedTime,
            level = level,
            tag = tag,
            message = sanitizedMessage
        )
        synchronized(this) {
            val current = _logs.value.toMutableList()
            if (current.size >= 1000) {
                current.removeAt(0)
            }
            current.add(entry)
            _logs.value = current
            saveLogs(current)
        }
    }

    private fun sanitize(input: String): String {
        var output = input
        for (pattern in sensitivePatterns) {
            if (output.contains(pattern, ignoreCase = true)) {
                output = output.replace(Regex("$pattern[:\\s=]+[^\\s,;]+", RegexOption.IGNORE_CASE), "$pattern: [REDACTED]")
            }
        }
        return output
    }

    private fun saveLogs(list: List<LogEntry>) {
        scope.launch {
            try {
                writeLock.withLock {
                    val json = logListAdapter.toJson(list)
                    AppPaths.logsFile().writeText(json)
                }
            } catch (_: Exception) {}
        }
    }

    private val writeLock = Mutex()

    fun i(message: String, tag: String = "AetherSystem") = log(LogLevel.INFO, message, tag)
    fun w(message: String, tag: String = "AetherSystem") = log(LogLevel.WARN, message, tag)
    fun e(message: String, tag: String = "AetherSystem") = log(LogLevel.ERROR, message, tag)
    fun d(message: String, tag: String = "AetherSystem") = log(LogLevel.DEBUG, message, tag)

    fun clear() {
        synchronized(this) {
            _logs.value = emptyList()
            saveLogs(emptyList())
        }
    }

    fun copyToClipboard() {
        val currentLogs = _logs.value
        val text = currentLogs.joinToString("\n") { "[${it.timestamp}] [${it.level}] [${it.tag}] ${it.message}" }
        runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
        }
    }
}