package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsRangeTest {

    private val zone = ZoneId.systemDefault()

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun txn(
        date: LocalDate,
        amountMinor: Long,
        type: TxnType,
        currency: String = "OMR",
        categoryId: String = "groceries",
        from: String? = "acc_a",
        to: String? = null,
    ) = Txn(
        id = "t-$date-$amountMinor-$type",
        atMillis = millis(date) + 3_600_000,
        amountMinor = amountMinor,
        currency = currency,
        type = type,
        fromAccountId = if (type == TxnType.INCOME) null else from,
        toAccountId = if (type == TxnType.EXPENSE) null else to ?: "acc_a",
        merchant = "Lulu",
        sender = "BankMuscat",
        body = "test",
        categoryId = categoryId,
        categorySource = "auto",
        confidence = 90,
    )

    private val txns = listOf(
        txn(LocalDate.of(2026, 5, 10), 5_000, TxnType.EXPENSE),
        txn(LocalDate.of(2026, 6, 1), 2_000, TxnType.EXPENSE),
        txn(LocalDate.of(2026, 6, 15), 1_000, TxnType.INCOME, categoryId = "salary"),
        txn(LocalDate.of(2026, 7, 2), 7_000, TxnType.EXPENSE),
        txn(LocalDate.of(2026, 6, 20), 9_000, TxnType.EXPENSE, currency = "AED"),
    )

    @Test
    fun `totalsIn only counts the slice and the currency`() {
        val start = millis(LocalDate.of(2026, 6, 1))
        val end = millis(LocalDate.of(2026, 7, 1))
        val totals = Stats.totalsIn(txns, start, end, "OMR")
        assertEquals(2_000, totals.spent)
        assertEquals(1_000, totals.received)
        assertEquals(1, totals.otherCurrencyCount)
        assertEquals(-1_000, totals.net)
    }

    @Test
    fun `breakdownIn covers only expenses in range`() {
        val start = millis(LocalDate.of(2026, 5, 1))
        val end = millis(LocalDate.of(2026, 7, 1))
        val slices = Stats.breakdownIn(txns, start, end, "OMR")
        assertEquals(1, slices.size)
        assertEquals(7_000, slices[0].amountMinor) // 5000 + 2000, same category
        assertEquals(1f, slices[0].fraction)
    }

    @Test
    fun `breakdownIn can split the income side instead`() {
        val start = millis(LocalDate.of(2026, 6, 1))
        val end = millis(LocalDate.of(2026, 7, 1))
        val slices = Stats.breakdownIn(txns, start, end, "OMR", type = TxnType.INCOME)
        assertEquals(1, slices.size)
        assertEquals("salary", slices[0].categoryId)
        assertEquals(1_000, slices[0].amountMinor)
    }

    @Test
    fun `avgSpentPerDayIn divides by elapsed days of a past slice`() {
        val start = millis(LocalDate.of(2026, 6, 1))
        val end = millis(LocalDate.of(2026, 6, 11)) // 10 full days, all in the past
        assertEquals(1_000, Stats.avgSpentPerDayIn(10_000, start, end))
    }

    // ── the whole point of TxnType.TRANSFER ──

    @Test
    fun `a transfer is neither spending nor income`() {
        val start = millis(LocalDate.of(2026, 6, 1))
        val end = millis(LocalDate.of(2026, 7, 1))
        val withTransfer = txns + txn(
            LocalDate.of(2026, 6, 10), 50_000, TxnType.TRANSFER,
            categoryId = "transfer", from = "acc_a", to = "acc_b",
        )
        val totals = Stats.totalsIn(withTransfer, start, end, "OMR")
        // Identical to the run without it: 50.000 moved, nothing was earned or spent.
        assertEquals(2_000, totals.spent)
        assertEquals(1_000, totals.received)
        assertEquals(50_000, Stats.transferTotalIn(withTransfer, start, end, "OMR"))
        assertTrue(Stats.breakdownIn(withTransfer, start, end, "OMR").none { it.categoryId == "transfer" })
    }

    @Test
    fun `an account filter keeps only records that touch it`() {
        val start = millis(LocalDate.of(2026, 6, 1))
        val end = millis(LocalDate.of(2026, 7, 1))
        val other = txn(LocalDate.of(2026, 6, 5), 4_000, TxnType.EXPENSE, from = "acc_b")
        val all = txns + other
        assertEquals(2_000, Stats.totalsIn(all, start, end, "OMR", accountId = "acc_a").spent)
        assertEquals(4_000, Stats.totalsIn(all, start, end, "OMR", accountId = "acc_b").spent)
        assertEquals(6_000, Stats.totalsIn(all, start, end, "OMR").spent)
    }

    // ── comparison against the previous window ──

    @Test
    fun `previousWindow is the same length immediately before`() {
        val start = millis(LocalDate.of(2026, 6, 11))
        val end = millis(LocalDate.of(2026, 6, 21))
        val (prevStart, prevEnd) = Stats.previousWindow(start, end)
        assertEquals(start, prevEnd)
        assertEquals(end - start, prevEnd - prevStart)
    }

    @Test
    fun `deltaPct refuses to invent a percentage from nothing`() {
        assertNull(Stats.deltaPct(500, 0))
        assertEquals(1f, Stats.deltaPct(200, 100)!!, 0.001f)
        assertEquals(-0.5f, Stats.deltaPct(50, 100)!!, 0.001f)
    }

    @Test
    fun `spread describes the period beyond its total`() {
        val start = millis(LocalDate.of(2026, 7, 1))
        val end = millis(LocalDate.of(2026, 8, 1))
        val data = listOf(
            txn(LocalDate.of(2026, 7, 2), 1_000, TxnType.EXPENSE),
            txn(LocalDate.of(2026, 7, 2), 3_000, TxnType.EXPENSE, categoryId = "food"),
            txn(LocalDate.of(2026, 7, 9), 2_000, TxnType.EXPENSE),
            txn(LocalDate.of(2026, 7, 20), 90_000, TxnType.INCOME, categoryId = "salary"),
            // Transfers move nothing in or out, so they belong in none of these counts.
            txn(LocalDate.of(2026, 7, 21), 50_000, TxnType.TRANSFER, to = "acc_b"),
        )
        val s = Stats.spread(data, start, end, "OMR", zone = zone)
        assertEquals(3, s.payments)
        assertEquals(1, s.deposits)
        assertEquals(2_000L, s.medianMinor)          // 1_000, 2_000, 3_000
        assertEquals(2_000L, s.averageMinor)
        assertEquals(2, s.activeDays)                 // two of the payments share a day
        assertEquals(31, s.periodDays)
        assertEquals(millis(LocalDate.of(2026, 7, 2)), s.busiestDayMillis)
        assertEquals(4_000L, s.busiestDayMinor)
        assertEquals(0.933f, s.savedFraction(90_000, 6_000)!!, 0.001f)
    }

    @Test
    fun `spread stays silent rather than dividing by nothing`() {
        val start = millis(LocalDate.of(2026, 7, 1))
        val end = millis(LocalDate.of(2026, 8, 1))
        val s = Stats.spread(emptyList(), start, end, "OMR", zone = zone)
        assertEquals(0, s.payments)
        assertEquals(0L, s.medianMinor)
        assertEquals(0L, s.averageMinor)
        assertNull(s.busiestDayMillis)
        assertNull(s.savedFraction(0, 0))
    }

    @Test
    fun `biggestMovers ranks by absolute change against the period before`() {
        val july = millis(LocalDate.of(2026, 7, 1))
        val data = listOf(
            txn(LocalDate.of(2026, 6, 5), 10_000, TxnType.EXPENSE, categoryId = "food"),
            txn(LocalDate.of(2026, 6, 6), 3_000, TxnType.EXPENSE, categoryId = "transport"),
            txn(LocalDate.of(2026, 7, 5), 1_000, TxnType.EXPENSE, categoryId = "food"),
            txn(LocalDate.of(2026, 7, 6), 9_000, TxnType.EXPENSE, categoryId = "transport"),
        )
        val movers = Stats.biggestMovers(data, july, millis(LocalDate.of(2026, 8, 1)), "OMR")
        assertEquals("food", movers[0].categoryId)
        assertEquals(-9_000, movers[0].deltaMinor)
        assertEquals("transport", movers[1].categoryId)
        assertEquals(6_000, movers[1].deltaMinor)
    }
}
