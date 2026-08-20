package io.github.immaghzbad.aetherst.model

import com.squareup.moshi.JsonClass
import java.util.concurrent.atomic.AtomicLong

@JsonClass(generateAdapter = false)
enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    DEBUG
}

val logIdGenerator = AtomicLong(1)

@JsonClass(generateAdapter = true)
data class LogEntry(
    val id: Long = logIdGenerator.getAndIncrement(),
    val timestamp: String,
    val level: LogLevel,
    val tag: String = "AetherCore",
    val message: String
)
