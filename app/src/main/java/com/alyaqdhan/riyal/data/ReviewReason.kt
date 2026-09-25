package com.alyaqdhan.riyal.data

import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Verbose

object ReviewReason {

    const val SEPARATOR = "|"
    const val NO_AMOUNT = "no_amount"
    const val BALANCE_ONLY = "balance_only"
    const val AMOUNT_UNPARSABLE = "amount_unparsable"
    const val AMOUNT_ZERO = "amount_zero"
    const val DIRECTION_UNKNOWN = "direction_unknown"

    fun noteOf(code: String): ParseNote? {
        val arg = code.substringAfter(SEPARATOR, "")
        val id = when (code.substringBefore(SEPARATOR)) {
            NO_AMOUNT -> R.string.reason_no_amount
            BALANCE_ONLY -> R.string.reason_balance_only
            AMOUNT_UNPARSABLE -> R.string.reason_amount_unparsable
            AMOUNT_ZERO -> R.string.reason_amount_zero
            DIRECTION_UNKNOWN -> R.string.reason_direction_unknown
            else -> return null
        }
        return ParseNote(id, if (arg.isEmpty()) emptyList() else listOf(arg))
    }
}

fun ParseNote.text(): String =
    Verbose.str(id, *args.map { if (it is ParseNote) it.text() else it }.toTypedArray())

fun reasonText(code: String): String = ReviewReason.noteOf(code)?.text() ?: code
