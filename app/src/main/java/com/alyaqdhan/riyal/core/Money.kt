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

    fun format(minor: Long, currency: String): String {
        val decimals = decimalsFor(currency)
        val pattern = if (decimals == 3) "#,##0.000" else "#,##0.00"
        val fmt = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
        return "$currency ${fmt.format(toMajor(minor, currency))}"
    }

    /** "− OMR 4.500" / "+ OMR 12.000" style, for transaction rows. */
    fun formatSigned(minor: Long, currency: String, expense: Boolean): String =
        (if (expense) "− " else "+ ") + format(minor, currency)
}
