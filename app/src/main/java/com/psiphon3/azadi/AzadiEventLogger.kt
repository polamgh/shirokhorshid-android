package com.psiphon3.azadi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AzadiEventLogger {
    private const val MAX_LINES = 4000
    private val mutex = Mutex()
    private val buffer = ArrayDeque<String>(MAX_LINES)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    suspend fun log(event: String, detail: String? = null) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(" ")
            append(event)
            if (!detail.isNullOrBlank()) {
                append(" | ")
                append(redact(detail))
            }
        }
        mutex.withLock {
            if (buffer.size >= MAX_LINES) buffer.removeFirst()
            buffer.addLast(line)
            _lines.value = buffer.toList()
        }
    }

    @JvmStatic
    fun logSync(event: String, detail: String? = null) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(" ")
            append(event)
            if (!detail.isNullOrBlank()) {
                append(" | ")
                append(redact(detail))
            }
        }
        synchronized(buffer) {
            if (buffer.size >= MAX_LINES) buffer.removeFirst()
            buffer.addLast(line)
            _lines.value = buffer.toList()
        }
    }

    fun asText(): String = synchronized(buffer) { buffer.joinToString("\n") }

    suspend fun clear() {
        mutex.withLock {
            buffer.clear()
            _lines.value = emptyList()
        }
    }

    private fun redact(text: String): String = text
        .replace(Regex("(?i)(password|secret|token|key)=\\S+"), "$1=[REDACTED]")
        .replace(Regex("(?i)\"(password|secret|token|key)\"\\s*:\\s*\"[^\"]*\""), "\"$1\":\"[REDACTED]\"")
}
