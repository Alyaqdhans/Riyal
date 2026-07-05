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

    // ── arbitrary time slices (Analysis lets the user pick any period) ──

    private fun inRange(t: Txn, start: Long, endExclusive: Long) =
        t.atMillis in start until endExclusive

    fun totalsIn(txns: List<Txn>, start: Long, endExclusive: Long, currency: String): MonthTotals {
        var spent = 0L
        var received = 0L
        var other = 0
        for (t in txns) {
            if (!inRange(t, start, endExclusive)) continue
            if (t.currency != currency) {
                other++
                continue
            }
            if (t.direction == Direction.EXPENSE) spent += t.amountMinor else received += t.amountMinor
        }
        return MonthTotals(spent, received, other)
    }

    fun breakdownIn(txns: List<Txn>, start: Long, endExclusive: Long, currency: String): List<Slice> {
        val expenses = txns.filter {
            it.direction == Direction.EXPENSE && it.currency == currency && inRange(it, start, endExclusive)
        }
        val total = expenses.sumOf { it.amountMinor }
        if (total <= 0L) return emptyList()
        return expenses.groupBy { it.categoryId }
            .map { (cat, list) -> cat to list.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }
            .map { (cat, sum) -> Slice(cat, sum, sum.toFloat() / total.toFloat()) }
    }

    fun topMerchantIn(txns: List<Txn>, start: Long, endExclusive: Long, currency: String): Pair<String, Long>? =
        txns.filter {
            it.direction == Direction.EXPENSE && it.currency == currency &&
                inRange(it, start, endExclusive) && !it.merchant.isNullOrBlank()
        }
            .groupBy { it.merchant!! }
            .map { (merchant, list) -> merchant to list.sumOf { it.amountMinor } }
            .maxByOrNull { it.second }

    fun biggestExpenseIn(txns: List<Txn>, start: Long, endExclusive: Long, currency: String): Txn? =
        txns.filter {
            it.direction == Direction.EXPENSE && it.currency == currency && inRange(it, start, endExclusive)
        }.maxByOrNull { it.amountMinor }

    /** Average per elapsed day of the slice (days in the future don't dilute it). */
    fun avgSpentPerDayIn(spent: Long, start: Long, endExclusive: Long): Long {
        val zone = ZoneId.systemDefault()
        val startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(endExclusive - 1).atZone(zone).toLocalDate()
        val lastCounted = minOf(endDay, LocalDate.now())
        val days = (lastCounted.toEpochDay() - startDay.toEpochDay() + 1).coerceAtLeast(1)
        return spent / days
    }

    data class TrendPoint(val label: String, val spentCumulative: Long, val receivedCumulative: Long)

    /**
     * Running totals across the slice, for the "money over time" chart: how spending
     * and income accumulated day by day (month by month for long slices). Stops at
     * today so an ongoing month doesn't drag a flat line into the future.
     */
    fun cumulativeTrend(txns: List<Txn>, start: Long, endExclusive: Long, currency: String): List<TrendPoint> {
        val zone = ZoneId.systemDefault()
        val startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(endExclusive - 1).atZone(zone).toLocalDate()
        val lastDay = minOf(endDay, LocalDate.now())
        if (lastDay < startDay) return emptyList()

        val inSlice = txns.filter { it.atMillis in start until endExclusive && it.currency == currency }
        val totalDays = lastDay.toEpochDay() - startDay.toEpochDay() + 1
        var spent = 0L
        var received = 0L
        val out = ArrayList<TrendPoint>()

        if (totalDays <= 92) {
            val byDay = inSlice.groupBy { Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() }
            val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")
            var day = startDay
            while (day <= lastDay) {
                byDay[day]?.forEach { t ->
                    if (t.direction == Direction.EXPENSE) spent += t.amountMinor else received += t.amountMinor
                }
                out += TrendPoint(fmt.format(day), spent, received)
                day = day.plusDays(1)
            }
        } else {
            val byMonth = inSlice.groupBy { ym(it.atMillis) }
            val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM uu")
            var m = YearMonth.from(startDay)
            val lastMonth = YearMonth.from(lastDay)
            while (m <= lastMonth) {
                byMonth[m]?.forEach { t ->
                    if (t.direction == Direction.EXPENSE) spent += t.amountMinor else received += t.amountMinor
                }
                out += TrendPoint(fmt.format(m), spent, received)
                m = m.plusMonths(1)
            }
        }
        return out
    }

    /** Months overlapping the slice, oldest first, capped so the chart stays readable. */
    fun monthsIn(start: Long, endExclusive: Long, cap: Int = 12): List<YearMonth> {
        val first = ym(start)
        val last = ym(endExclusive - 1)
        val months = ArrayList<YearMonth>()
        var m = first
        while (m <= last && months.size < cap * 4) {
            months += m
            m = m.plusMonths(1)
        }
        return months.takeLast(cap)
    }

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
