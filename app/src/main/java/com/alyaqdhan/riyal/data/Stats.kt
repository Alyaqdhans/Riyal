package com.alyaqdhan.riyal.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Pure aggregation helpers for the Home and Analysis screens. */
object Stats {

    fun ym(millis: Long): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** The currency you actually transact in, most frequent one, falling back to the setting. */
    fun primaryCurrency(txns: List<Txn>, fallback: String): String =
        txns.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key ?: fallback

    data class MonthTotals(val spent: Long, val received: Long, val otherCurrencyCount: Int)

    fun totalsFor(txns: List<Txn>, month: YearMonth, currency: String): MonthTotals {
        var spent = 0L
        var received = 0L
        var other = 0
        for (t in txns) {
            if (ym(t.atMillis) != month) continue
            if (t.currency != currency) {
                other++
                continue
            }
            if (t.direction == Direction.EXPENSE) spent += t.amountMinor else received += t.amountMinor
        }
        return MonthTotals(spent, received, other)
    }

    data class Slice(val categoryId: String, val amountMinor: Long, val fraction: Float)

    fun breakdown(txns: List<Txn>, month: YearMonth, currency: String): List<Slice> {
        val expenses = txns.filter {
            it.direction == Direction.EXPENSE && it.currency == currency && ym(it.atMillis) == month
        }
        val total = expenses.sumOf { it.amountMinor }
        if (total <= 0L) return emptyList()
        return expenses.groupBy { it.categoryId }
            .map { (cat, list) -> cat to list.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }
            .map { (cat, sum) -> Slice(cat, sum, sum.toFloat() / total.toFloat()) }
    }

    data class MonthPoint(val month: YearMonth, val spent: Long, val received: Long)

    fun series(txns: List<Txn>, currency: String, end: YearMonth, monthsBack: Int = 6): List<MonthPoint> =
        (monthsBack - 1 downTo 0).map { back ->
            val m = end.minusMonths(back.toLong())
            val t = totalsFor(txns, m, currency)
            MonthPoint(m, t.spent, t.received)
        }

    fun monthsWithData(txns: List<Txn>): List<YearMonth> =
        txns.map { ym(it.atMillis) }.distinct().sorted()

    fun topMerchant(txns: List<Txn>, month: YearMonth, currency: String): Pair<String, Long>? =
        txns.filter {
            it.direction == Direction.EXPENSE && it.currency == currency &&
                ym(it.atMillis) == month && !it.merchant.isNullOrBlank()
        }
            .groupBy { it.merchant!! }
            .map { (merchant, list) -> merchant to list.sumOf { it.amountMinor } }
            .maxByOrNull { it.second }

    fun biggestExpense(txns: List<Txn>, month: YearMonth, currency: String): Txn? =
        txns.filter {
            it.direction == Direction.EXPENSE && it.currency == currency && ym(it.atMillis) == month
        }.maxByOrNull { it.amountMinor }

    fun avgSpentPerDay(spent: Long, month: YearMonth): Long {
        val today = LocalDate.now()
        val days = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
        return if (days <= 0) spent else spent / days
    }

    /**
     * Mood for the mascot: +1 when spending is well under income, -1 when spending
     * exceeds income. With no income data, base it on whether anything was spent.
     */
    fun mood(totals: MonthTotals): Float {
        if (totals.received <= 0L) return if (totals.spent == 0L) 0.6f else 0.1f
        val ratio = totals.spent.toFloat() / totals.received.toFloat()
        return (1f - (ratio / 1.25f) * 2f).coerceIn(-1f, 1f)
    }

    fun moodLabel(totals: MonthTotals): String {
        if (totals.received <= 0L) {
            return if (totals.spent == 0L) "Quiet month so far" else "Tracking spending, no income seen yet"
        }
        val ratio = totals.spent.toFloat() / totals.received.toFloat()
        return when {
            ratio < 0.5f -> "Smooth sailing, well under your income"
            ratio < 0.8f -> "Doing fine, keep an eye on it"
            ratio <= 1.0f -> "Cutting it close this month"
            else -> "Spending is above income this month"
        }
    }
}
