package com.octastream.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LogLevel(val label: String) {
    INFO("INFO"),
    NETWORK("NET"),
    WARN("WARN"),
    ERROR("ERR")
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val stackTrace: String? = null
) {
    val formattedTimestamp: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            return sdf.format(Date(timestampMillis))
        }

    fun toRawConsoleLine(): String {
        val base = "[$formattedTimestamp] [${level.label}] [$tag] $message"
        return if (!stackTrace.isNullOrBlank()) {
            "$base\n$stackTrace"
        } else {
            base
        }
    }
}
