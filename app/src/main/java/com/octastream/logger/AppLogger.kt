package com.octastream.logger

import android.util.Log
import com.octastream.model.LogEntry
import com.octastream.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Thread-safe singleton diagnostic ring buffer (max 500 entries) backed by Kotlin StateFlow.
 * Captures real-time extraction diagnostics, HTTP Range headers, thread offsets, and stack traces.
 */
object AppLogger {
    private const val MAX_BUFFER_LINES = 500

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _unreadAlertCount = MutableStateFlow(0)
    val unreadAlertCount: StateFlow<Int> = _unreadAlertCount.asStateFlow()

    private val _hasRecentError = MutableStateFlow(false)
    val hasRecentError: StateFlow<Boolean> = _hasRecentError.asStateFlow()

    init {
        info(
            tag = "OctaKernel",
            message = "OctaStream Parallel Engine v2.4.0-PRO initialized. Ring buffer capacity: $MAX_BUFFER_LINES lines."
        )
    }

    fun info(tag: String, message: String) {
        append(tag = tag, message = message, level = LogLevel.INFO, throwable = null)
    }

    fun network(tag: String, message: String) {
        append(tag = tag, message = message, level = LogLevel.NETWORK, throwable = null)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        append(tag = tag, message = message, level = LogLevel.WARN, throwable = throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        append(tag = tag, message = message, level = LogLevel.ERROR, throwable = throwable)
    }

    private fun append(
        tag: String,
        message: String,
        level: LogLevel,
        throwable: Throwable?
    ) {
        val trace = throwable?.let { formatStackTrace(it) }
        val entry = LogEntry(
            tag = tag,
            message = message,
            level = level,
            stackTrace = trace
        )

        runCatching {
            when (level) {
                LogLevel.INFO, LogLevel.NETWORK -> Log.i(tag, message)
                LogLevel.WARN -> Log.w(tag, message, throwable)
                LogLevel.ERROR -> Log.e(tag, message, throwable)
            }
        }

        _logs.update { current ->
            val updated = current + entry
            if (updated.size > MAX_BUFFER_LINES) {
                updated.takeLast(MAX_BUFFER_LINES)
            } else {
                updated
            }
        }

        if (level == LogLevel.WARN || level == LogLevel.ERROR) {
            _unreadAlertCount.update { it + 1 }
            if (level == LogLevel.ERROR) {
                _hasRecentError.value = true
            }
        }
    }

    fun markAlertsRead() {
        _unreadAlertCount.value = 0
        _hasRecentError.value = false
    }

    fun clearBuffer() {
        _logs.value = emptyList()
        _unreadAlertCount.value = 0
        _hasRecentError.value = false
        info("OctaKernel", "Diagnostic log ring buffer cleared by user.")
    }

    fun exportFormattedLogs(): String {
        val snapshot = _logs.value
        if (snapshot.isEmpty()) return "# OctaStream Diagnostic Console (Empty)"
        return buildString {
            appendLine("=== OCTASTREAM DIAGNOSTIC TELEMETRY DUMP ===")
            appendLine("Entries: ${snapshot.size} / $MAX_BUFFER_LINES")
            appendLine("============================================")
            snapshot.forEach { entry ->
                appendLine(entry.toRawConsoleLine())
            }
        }
    }

    private fun formatStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString().trimEnd()
    }
}
