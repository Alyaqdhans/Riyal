package com.alyaqdhan.riyal.core

import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object Dates {

    @Volatile
    private var locale: Locale = Locale.getDefault()

    private val cache = ConcurrentHashMap<String, DateTimeFormatter>()

    fun use(locale: Locale?) {
        this.locale = locale ?: Locale.getDefault()
    }

    fun pattern(pattern: String): DateTimeFormatter {
        val chosen = locale
        return cache.computeIfAbsent(chosen.toLanguageTag() + "\u0000" + pattern) {
            val punctuated =
                if (chosen.language == "ar") pattern.replace(',', '\u060c') else pattern
            DateTimeFormatter.ofPattern(punctuated, chosen).withDecimalStyle(DecimalStyle.STANDARD)
        }
    }
}
