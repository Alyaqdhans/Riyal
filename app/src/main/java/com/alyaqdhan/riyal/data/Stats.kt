package com.alyaqdhan.riyal.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Pure aggregation helpers for the Home, Categories and Analysis screens.
 *
 * One rule runs through all of it: **a [TxnType.TRANSFER] is never money earned or
 * money spent.** Moving your own savings into your current account is not income, and
 * the withdrawal side is not an expense; counting either would inflate both halves of
 * every total at once. [flows] is the single gate that enforces it, and every figure
 * on every screen comes through it.
 */
object Stats {

    fun ym(millis: Long): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** The currency you actually transact in, most frequent one, falling back to the setting. */
    fun primaryCurrency(txns: List<Txn>, fallback: String): String =
        txns.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key ?: fallback

    /**
     * The records that count as money in or money out, in one currency, optionally
     * narrowed to one account. Transfers are excluded here and nowhere else.
     */
    private fun flows(
        txns: List<Txn>,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String?,
    ): List<Txn> = txns.filter {
        it.type.isFlow &&
            it.currency == currency &&
            it.atMillis >= start && it.atMillis < endExclusive &&
            (accountId == null || it.touches(accountId))
    }

    private fun monthBounds(month: YearMonth): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        return month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    data class MonthTotals(val spent: Long, val received: Long, val otherCurrencyCount: Int) {
        val net: Long get() = received - spent
    }

    fun totalsFor(
        txns: List<Txn>,
        month: YearMonth,
        currency: String,
        accountId: String? = null,
    ): MonthTotals {
        val (start, end) = monthBounds(month)
        return totalsIn(txns, start, end, currency, accountId)
    }

    fun totalsIn(
        txns: List<Txn>,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String? = null,
    ): MonthTotals {
        var spent = 0L
        var received = 0L
        var other = 0
        for (t in txns) {
            if (!t.type.isFlow) continue
            if (t.atMillis < start || t.atMillis >= endExclusive) continue
            if (accountId != null && !t.touches(accountId)) continue
            if (t.currency != currency) {
                other++
                continue
            }
            if (t.isExpense) spent += t.amountMinor else received += t.amountMinor
        }
        return MonthTotals(spent, received, other)
    }

    /** What moved between the user's own accounts in a period; shown, never counted. */
    fun transferTotalIn(
        txns: List<Txn>,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String? = null,
    ): Long = txns.filter {
        it.isTransfer && it.currency == currency &&
            it.atMillis >= start && it.atMillis < endExclusive &&
            (accountId == null || it.touches(accountId))
    }.sumOf { it.amountMinor }

    data class Slice(val categoryId: String, val amountMinor: Long, val fraction: Float)

    /** Category split of one side of the ledger, biggest first. */
    fun breakdownIn(
        txns: List<Txn>,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String? = null,
        type: TxnType = TxnType.EXPENSE,
    ): List<Slice> {
        val matching = flows(txns, start, endExclusive, currency, accountId).filter { it.type == type }
        val total = matching.sumOf { it.amountMinor }
        if (total <= 0L) return emptyList()
        return matching.groupBy { it.categoryId }
            .map { (cat, list) -> cat to list.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }
            .map { (cat, sum) -> Slice(cat, sum, sum.toFloat() / total.toFloat()) }
    }

    fun categoryTotalIn(
        txns: List<Txn>,
        categoryId: String,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String? = null,
    ): Long = flows(txns, start, endExclusive, currency, accountId)
        .filter { it.categoryId == categoryId }
        .sumOf { it.amountMinor }

    // ── comparison against the previous, equally long period ──

    /**
     * The window of the same length immediately before [start]. Comparing August with
     * July only means something if both are measured over the same span, so the shift
     * is by the slice's own length rather than by a calendar month.
     */
    fun previousWindow(start: Long, endExclusive: Long): Pair<Long, Long> {
        val length = endExclusive - start
        return (start - length) to start
    }

    /**
     * Change from [before] to [now] as a fraction (0.25 = up a quarter). Null when
     * there is nothing to compare against - "up from zero" is not a percentage, and
     * showing +100% or ∞ there would be a lie dressed as precision.
     */
    fun deltaPct(now: Long, before: Long): Float? {
        if (before <= 0L) return null
        return (now - before).toFloat() / before.toFloat()
    }

    data class Mover(
        val categoryId: String,
        val nowMinor: Long,
        val beforeMinor: Long,
    ) {
        val deltaMinor: Long get() = nowMinor - beforeMinor
        val pct: Float? get() = deltaPct(nowMinor, beforeMinor)
    }

    /** The categories whose spending changed most against the previous period. */
    fun biggestMovers(
        txns: List<Txn>,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String? = null,
        limit: Int = 3,
        minMinor: Long = 0L,
    ): List<Mover> {
        val (prevStart, prevEnd) = previousWindow(start, endExclusive)
        val now = breakdownIn(txns, start, endExclusive, currency, accountId)
            .associate { it.categoryId to it.amountMinor }
        val before = breakdownIn(txns, prevStart, prevEnd, currency, accountId)
            .associate { it.categoryId to it.amountMinor }
        return (now.keys + before.keys)
            .map { Mover(it, now[it] ?: 0L, before[it] ?: 0L) }
            .filter { abs(it.deltaMinor) > minMinor }
            .sortedByDescending { abs(it.deltaMinor) }
            .take(limit)
    }

    // ── cashflow ──

    data class CashflowPoint(val label: String, val spent: Long, val received: Long) {
        val net: Long get() = received - spent
    }

    /**
     * Money in against money out, bucketed so the bars stay readable whatever the
     * slice: days for a short window, weeks for a season, months beyond that.
     */
    fun cashflow(
        txns: List<Txn>,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String? = null,
    ): List<CashflowPoint> {
        val zone = ZoneId.systemDefault()
        val startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(endExclusive - 1).atZone(zone).toLocalDate()
        val lastDay = minOf(endDay, LocalDate.now())
        if (lastDay < startDay) return emptyList()

        val inSlice = flows(txns, start, endExclusive, currency, accountId)
        val spanDays = lastDay.toEpochDay() - startDay.toEpochDay() + 1

        fun bucket(list: List<Txn>): Pair<Long, Long> {
            var s = 0L
            var r = 0L
            list.forEach { if (it.isExpense) s += it.amountMinor else r += it.amountMinor }
            return s to r
        }

        return when {
            spanDays <= 14 -> {
                val fmt = DateTimeFormatter.ofPattern("d MMM")
                val byDay = inSlice.groupBy { Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() }
                generateSequence(startDay) { it.plusDays(1) }.takeWhile { it <= lastDay }
                    .map { day ->
                        val (s, r) = bucket(byDay[day].orEmpty())
                        CashflowPoint(fmt.format(day), s, r)
                    }.toList()
            }

            spanDays <= 120 -> {
                val fmt = DateTimeFormatter.ofPattern("d MMM")
                val byWeek = inSlice.groupBy {
                    val d = Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate()
                    startDay.plusDays(((d.toEpochDay() - startDay.toEpochDay()) / 7) * 7)
                }
                generateSequence(startDay) { it.plusWeeks(1) }.takeWhile { it <= lastDay }
                    .map { weekStart ->
                        val (s, r) = bucket(byWeek[weekStart].orEmpty())
                        CashflowPoint(fmt.format(weekStart), s, r)
                    }.toList()
            }

            else -> {
                val fmt = DateTimeFormatter.ofPattern("MMM uu")
                val byMonth = inSlice.groupBy { ym(it.atMillis) }
                generateSequence(YearMonth.from(startDay)) { it.plusMonths(1) }
                    .takeWhile { it <= YearMonth.from(lastDay) }
                    .map { m ->
                        val (s, r) = bucket(byMonth[m].orEmpty())
                        CashflowPoint(fmt.format(m), s, r)
                    }.toList()
            }
        }
    }

    // ── the shape of a period's spending ──

    /**
     * What a period's spending was made of, beyond its total: how many payments, how
     * big a normal one was, how many days had any spending at all, and the worst day.
     *
     * The median is here because the mean is not the typical payment - one rent
     * payment among sixty coffees drags an average nowhere near either of them.
     */
    data class Spread(
        val payments: Int,
        val deposits: Int,
        val medianMinor: Long,
        val averageMinor: Long,
        val activeDays: Int,
        val periodDays: Int,
        val busiestDayMillis: Long?,
        val busiestDayMinor: Long,
    ) {
        /** Share of what came in that was still there at the end, or null if nothing came in. */
        fun savedFraction(received: Long, spent: Long): Float? =
            if (received <= 0L) null else ((received - spent).toFloat() / received.toFloat())
    }

    fun spread(
        txns: List<Txn>,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Spread {
        val inPeriod = flows(txns, start, endExclusive, currency, accountId)
        val out = inPeriod.filter { it.isExpense }
        val amounts = out.map { it.amountMinor }.sorted()
        val median = when {
            amounts.isEmpty() -> 0L
            amounts.size % 2 == 1 -> amounts[amounts.size / 2]
            else -> (amounts[amounts.size / 2 - 1] + amounts[amounts.size / 2]) / 2
        }
        val byDay = out.groupBy {
            Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate()
        }
        val worst = byDay.maxByOrNull { (_, list) -> list.sumOf { it.amountMinor } }
        val days = ((endExclusive - start) / 86_400_000L).toInt().coerceAtLeast(1)
        return Spread(
            payments = out.size,
            deposits = inPeriod.count { it.isIncome },
            medianMinor = median,
            averageMinor = if (out.isEmpty()) 0L else amounts.sum() / out.size,
            activeDays = byDay.size,
            periodDays = days,
            busiestDayMillis = worst?.key?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            busiestDayMinor = worst?.value?.sumOf { it.amountMinor } ?: 0L,
        )
    }

    fun biggestExpenseIn(
        txns: List<Txn>,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String? = null,
    ): Txn? = flows(txns, start, endExclusive, currency, accountId)
        .filter { it.isExpense }
        .maxByOrNull { it.amountMinor }

    // ── recurring charges ──

    data class Recurring(
        val merchant: String,
        val categoryId: String,
        val typicalMinor: Long,
        val occurrences: Int,
        val intervalDays: Int,
        val lastAtMillis: Long,
        val nextAtMillis: Long,
    )

    /**
     * Subscriptions and standing charges: the same merchant billing at a steady cadence
     * for a steady amount. Requires at least three charges, so one repeat purchase
     * cannot masquerade as a commitment, and allows only mild variation in amount
     * (a bill that swings wildly is a bill, not a subscription).
     */
    fun recurring(
        txns: List<Txn>,
        currency: String,
        accountId: String? = null,
        now: Long = System.currentTimeMillis(),
        limit: Int = 6,
    ): List<Recurring> {
        val candidates = txns.filter {
            it.isExpense && it.currency == currency && !it.merchant.isNullOrBlank() &&
                (accountId == null || it.touches(accountId))
        }
        return candidates
            .groupBy { it.merchant!!.trim().lowercase() }
            .mapNotNull { (_, group) ->
                if (group.size < 3) return@mapNotNull null
                val sorted = group.sortedBy { it.atMillis }
                val gapsDays = sorted.zipWithNext { a, b ->
                    ((b.atMillis - a.atMillis) / DAY_MILLIS).toInt()
                }
                val cadence = medianInt(gapsDays)
                val period = CADENCES.firstOrNull { cadence in it } ?: return@mapNotNull null
                // Every gap must sit near the cadence, or this is just a busy merchant.
                if (gapsDays.any { it !in period }) return@mapNotNull null

                val amounts = sorted.map { it.amountMinor }
                val typical = medianLong(amounts)
                if (typical <= 0L) return@mapNotNull null
                if (amounts.max().toDouble() / amounts.min().toDouble() > AMOUNT_TOLERANCE) return@mapNotNull null

                val last = sorted.last()
                Recurring(
                    merchant = last.merchant!!.trim(),
                    categoryId = last.categoryId,
                    typicalMinor = typical,
                    occurrences = sorted.size,
                    intervalDays = cadence,
                    lastAtMillis = last.atMillis,
                    nextAtMillis = last.atMillis + cadence * DAY_MILLIS,
                )
            }
            .filter { it.nextAtMillis > now - GRACE_MILLIS }
            .sortedByDescending { it.typicalMinor }
            .take(limit)
    }

    private fun medianInt(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        return s[s.size / 2]
    }

    private fun medianLong(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val s = values.sorted()
        return s[s.size / 2]
    }

    // ── budgets ──

    data class BudgetLineProgress(
        val categoryId: String,
        val spentMinor: Long,
        val capMinor: Long,
    ) {
        val fraction: Float get() = if (capMinor > 0) spentMinor.toFloat() / capMinor.toFloat() else 0f
        val over: Boolean get() = spentMinor > capMinor
        val remainingMinor: Long get() = capMinor - spentMinor
    }

    data class BudgetProgress(
        val plan: BudgetPlan,
        val lines: List<BudgetLineProgress>,
        val totalSpentMinor: Long,
        val totalCapMinor: Long,
        /** How much of the plan's period has already gone by, 0..1. */
        val elapsedFraction: Float,
        /** Spending outside any budgeted category, so the totals stay honest. */
        val unbudgetedMinor: Long,
    ) {
        val fraction: Float
            get() = if (totalCapMinor > 0) totalSpentMinor.toFloat() / totalCapMinor.toFloat() else 0f
        val over: Boolean get() = totalSpentMinor > totalCapMinor

        /**
         * True when the money is going faster than the calendar - the signal that
         * matters mid-period, well before a bar actually fills up.
         */
        val aheadOfPace: Boolean get() = elapsedFraction > 0f && fraction > elapsedFraction
    }

    /**
     * The [n] budget lines worth showing when there is not room for all of them: the
     * ones closest to (or past) their cap, not the ones with the biggest caps.
     *
     * A summary that showed the largest plans could hide a small category already over
     * its limit, which is the one thing on a budget card worth interrupting someone for.
     */
    fun mostAtRisk(lines: List<BudgetLineProgress>, n: Int): List<BudgetLineProgress> =
        lines.sortedWith(
            compareByDescending<BudgetLineProgress> { it.fraction }.thenByDescending { it.spentMinor },
        ).take(n)

    fun budgetProgress(
        plan: BudgetPlan,
        txns: List<Txn>,
        currency: String,
        accountId: String? = null,
        now: Long = System.currentTimeMillis(),
    ): BudgetProgress {
        val lines = plan.lines.entries
            .map { (categoryId, cap) ->
                BudgetLineProgress(
                    categoryId = categoryId,
                    spentMinor = categoryTotalIn(
                        txns, categoryId, plan.startMillis, plan.endExclusiveMillis, currency, accountId,
                    ),
                    capMinor = cap,
                )
            }
            .sortedByDescending { it.capMinor }

        val allSpent = totalsIn(txns, plan.startMillis, plan.endExclusiveMillis, currency, accountId).spent
        val budgetedSpent = lines.sumOf { it.spentMinor }
        val span = (plan.endExclusiveMillis - plan.startMillis).coerceAtLeast(1L)
        val elapsed = ((now - plan.startMillis).toFloat() / span.toFloat()).coerceIn(0f, 1f)

        return BudgetProgress(
            plan = plan,
            lines = lines,
            totalSpentMinor = budgetedSpent,
            totalCapMinor = plan.totalMinor,
            elapsedFraction = elapsed,
            unbudgetedMinor = (allSpent - budgetedSpent).coerceAtLeast(0L),
        )
    }

    // ── time-slice helpers used by Analysis ──

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
    fun cumulativeTrend(
        txns: List<Txn>,
        start: Long,
        endExclusive: Long,
        currency: String,
        accountId: String? = null,
    ): List<TrendPoint> {
        val zone = ZoneId.systemDefault()
        val startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(endExclusive - 1).atZone(zone).toLocalDate()
        val lastDay = minOf(endDay, LocalDate.now())
        if (lastDay < startDay) return emptyList()

        val inSlice = flows(txns, start, endExclusive, currency, accountId)
        val totalDays = lastDay.toEpochDay() - startDay.toEpochDay() + 1
        var spent = 0L
        var received = 0L
        val out = ArrayList<TrendPoint>()

        if (totalDays <= 92) {
            val byDay = inSlice.groupBy { Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() }
            val fmt = DateTimeFormatter.ofPattern("d MMM")
            var day = startDay
            while (day <= lastDay) {
                byDay[day]?.forEach { t ->
                    if (t.isExpense) spent += t.amountMinor else received += t.amountMinor
                }
                out += TrendPoint(fmt.format(day), spent, received)
                day = day.plusDays(1)
            }
        } else {
            val byMonth = inSlice.groupBy { ym(it.atMillis) }
            val fmt = DateTimeFormatter.ofPattern("MMM uu")
            var m = YearMonth.from(startDay)
            val lastMonth = YearMonth.from(lastDay)
            while (m <= lastMonth) {
                byMonth[m]?.forEach { t ->
                    if (t.isExpense) spent += t.amountMinor else received += t.amountMinor
                }
                out += TrendPoint(fmt.format(m), spent, received)
                m = m.plusMonths(1)
            }
        }
        return out
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

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** Weekly, fortnightly, monthly, quarterly and yearly billing, with slack. */
    private val CADENCES = listOf(6..8, 13..16, 26..35, 85..95, 355..375)

    /** A "same" charge may vary by a third; beyond that it is a variable bill. */
    private const val AMOUNT_TOLERANCE = 1.35

    /** A charge stays listed for a while after its due date before it looks stale. */
    private const val GRACE_MILLIS = 45L * DAY_MILLIS
}
