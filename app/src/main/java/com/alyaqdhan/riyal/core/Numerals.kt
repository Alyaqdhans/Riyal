package com.alyaqdhan.riyal.core

import java.math.BigDecimal

object Numerals {

    private val runOfSpace = Regex("\\s+")

    fun latin(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                in '٠'..'٩' -> sb.append('0' + (ch - '٠')) // Arabic-Indic digits
                in '۰'..'۹' -> sb.append('0' + (ch - '۰')) // Eastern Arabic-Indic digits
                '٫' -> sb.append('.')                       // Arabic decimal separator
                '٬' -> sb.append(',')                       // Arabic thousands separator
                ' ', ' ', ' ', '​', '‎', '‏' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(runOfSpace, " ").trim()
    }

    fun decimal(text: String): BigDecimal? =
        latin(text).replace(",", "").toBigDecimalOrNull()
}
