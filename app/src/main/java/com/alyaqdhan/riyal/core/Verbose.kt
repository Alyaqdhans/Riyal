package com.alyaqdhan.riyal.core

import android.content.Context
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * In-app verbose processing log. Every step the app takes while touching messages is
 * recorded here so the user can audit exactly what was read, parsed, skipped or refused.
 * Kept in memory only (never written to disk, never leaves the device).
 */
data class LogLine(val atMillis: Long, val kind: Kind, val text: String) {
    enum class Kind { INFO, OK, SKIP, FAIL, SCAN }

    /**
     * Formatted only when something actually shows it. A full pass over a large inbox
     * writes tens of thousands of these, and formatting a clock for each one cost real
     * seconds of a scan nobody was watching.
     */
    val time: String get() = timeFmt.format(Instant.ofEpochMilli(atMillis).atZone(zone))

    private companion object {
        val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm:ss a")
        val zone: ZoneId = ZoneId.systemDefault()
    }
}

object Verbose {

    private const val MAX_LINES = 4000
    private const val FLUSH_EVERY = 16

    private val buffer = ArrayDeque<LogLine>()
    private var pendingSinceFlush = 0

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())

    val lines: StateFlow<List<LogLine>> = _lines

    /**
     * Whether anything is watching. A scan writes ~68,000 lines over a full inbox, and
     * every one of them formatted a timestamp and crossed into logcat even when the
     * verbose screen was closed. The buffer is still filled either way - the log is a
     * record you can open after the fact, so dropping lines would break it - but the
     * two costs that only exist for a live reader are skipped while nobody reads.
     */
    @Volatile
    var mirrorToLogcat: Boolean = false

    @Synchronized
    fun log(kind: LogLine.Kind, text: String) {
        if (mirrorToLogcat) Log.d("RiyalVerbose", text)
        buffer.addLast(LogLine(System.currentTimeMillis(), kind, text))
        while (buffer.size > MAX_LINES) buffer.removeFirst()
        // Publishing means copying the whole buffer, and during a scan that happened
        // every sixteen lines whether or not the log screen existed. With nobody
        // collecting there is nothing to publish to: the buffer is still filled, so the
        // record is complete the moment someone opens it.
        if (++pendingSinceFlush >= FLUSH_EVERY && _lines.subscriptionCount.value > 0) {
            flushLocked()
        }
    }

    fun info(text: String) = log(LogLine.Kind.INFO, text)
    fun ok(text: String) = log(LogLine.Kind.OK, text)
    fun skip(text: String) = log(LogLine.Kind.SKIP, text)
    fun fail(text: String) = log(LogLine.Kind.FAIL, text)
    fun scan(text: String) = log(LogLine.Kind.SCAN, text)

    @Volatile
    private var strings: Context? = null

    fun attach(context: Context) {
        strings = context.applicationContext
            .createConfigurationContext(context.resources.configuration)
    }

    fun str(@StringRes id: Int, vararg args: Any?): String =
        strings?.getString(id, *args).orEmpty()

    fun count(@PluralsRes id: Int, n: Int): String =
        strings?.resources?.getQuantityString(id, n, n).orEmpty()

    fun info(@StringRes id: Int, vararg args: Any?) = info(str(id, *args))
    fun ok(@StringRes id: Int, vararg args: Any?) = ok(str(id, *args))
    fun skip(@StringRes id: Int, vararg args: Any?) = skip(str(id, *args))
    fun fail(@StringRes id: Int, vararg args: Any?) = fail(str(id, *args))
    fun scan(@StringRes id: Int, vararg args: Any?) = scan(str(id, *args))

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
