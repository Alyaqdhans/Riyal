package com.alyaqdhan.riyal.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * In-app verbose processing log. Every step the app takes while touching messages is
 * recorded here so the user can audit exactly what was read, parsed, skipped or refused.
 * Kept in memory only (never written to disk, never leaves the device).
 */
data class LogLine(val time: String, val kind: Kind, val text: String) {
    enum class Kind { INFO, OK, SKIP, FAIL, SCAN }
}

object Verbose {

    private const val MAX_LINES = 4000
    private const val FLUSH_EVERY = 16

    private val timeFmt = DateTimeFormatter.ofPattern("h:mm:ss a")
    private val buffer = ArrayDeque<LogLine>()
    private var pendingSinceFlush = 0

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines

    @Synchronized
    fun log(kind: LogLine.Kind, text: String) {
        Log.d("RiyalVerbose", text)
        buffer.addLast(LogLine(LocalTime.now().format(timeFmt), kind, text))
        while (buffer.size > MAX_LINES) buffer.removeFirst()
        if (++pendingSinceFlush >= FLUSH_EVERY) flushLocked()
    }

    fun info(text: String) = log(LogLine.Kind.INFO, text)
    fun ok(text: String) = log(LogLine.Kind.OK, text)
    fun skip(text: String) = log(LogLine.Kind.SKIP, text)
    fun fail(text: String) = log(LogLine.Kind.FAIL, text)
    fun scan(text: String) = log(LogLine.Kind.SCAN, text)

    @Synchronized
    fun flush() = flushLocked()

    private fun flushLocked() {
        pendingSinceFlush = 0
        _lines.value = buffer.toList()
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        flushLocked()
    }

    @Synchronized
    fun dump(): String = buffer.joinToString("\n") { "[${it.time}] ${it.kind} ${it.text}" }
}
