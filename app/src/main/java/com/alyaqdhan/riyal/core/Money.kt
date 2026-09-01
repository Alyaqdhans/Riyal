package com.alyaqdhan.riyal.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money is stored as integer minor units (baisa for OMR) to avoid floating point drift.
 * OMR and its Gulf siblings use 3 decimal places; most other currencies use 2.
 */
object Money {

    private val THREE_DECIMALS = setOf("OMR", "BHD", "KWD", "JOD", "TND", "IQD", "LYD")

    fun decimalsFor(currency: String): Int =
        if (currency.uppercase(Locale.ROOT) in THREE_DECIMALS) 3 else 2

    fun toMinor(value: BigDecimal, currency: String): Long =
        value.setScale(decimalsFor(currency), RoundingMode.HALF_UP).unscaledValue().toLong()

    fun toMajor(minor: Long, currency: String): BigDecimal =
        BigDecimal.valueOf(minor).movePointLeft(decimalsFor(currency))

    /**
     * Building a DecimalFormat means building DecimalFormatSymbols, which loads ICU
     * locale data every time - measurably the most expensive thing a transaction row
     * did, and every row formats at least twice. There are only two patterns, so hold
     * one of each. DecimalFormat is not thread-safe, hence the lock; contention is nil
     * because formatting happens on the UI thread.
     */
    private val threeDecimalFmt by lazy { DecimalFormat("#,##0.000", DecimalFormatSymbols(Locale.US)) }
    private val twoDecimalFmt by lazy { DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)) }

    private fun formatFor(minor: Long, currency: String): String {
        val fmt = if (decimalsFor(currency) == 3) threeDecimalFmt else twoDecimalFmt
        val major = toMajor(minor, currency)
        return synchronized(fmt) { fmt.format(major) }
    }

    fun format(minor: Long, currency: String): String = "$currency ${formatFor(minor, currency)}"

    /**
     * Just the number: "269.907". For lines that have already said which currency they
     * are in, where repeating the code on every figure is noise.
     */
    fun formatAmount(minor: Long, currency: String): String = formatFor(minor, currency)

    /** "− OMR 4.500" / "+ OMR 12.000" style, for transaction rows. */
    fun formatSigned(minor: Long, currency: String, expense: Boolean): String =
        (if (expense) "− " else "+ ") + format(minor, currency)
}
