package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsRangeTest {

    private val zone = ZoneId.systemDefault()

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun txn(date: LocalDate, amountMinor: Long, direction: Direction, currency: String = "OMR") = Txn(
        id = "t-$date-$amountMinor",
        atMillis = millis(date) + 3_600_000,
        amountMinor = amountMinor,
        currency = currency,
        direction = direction,
        merchant = "Lulu",
        sender = "BankMuscat",
        body = "test",
        categoryId = "groceries",
        categorySource = "auto",
        confidence = 90,
    )

    private val txns = listOf(
        txn(LocalDate.of(2026, 5, 10), 5_000, Direction.EXPENSE),
        txn(LocalDate.of(2026, 6, 1), 2_000, Direction.EXPENSE),
        txn(LocalDate.of(2026, 6, 15), 1_000, Direction.INCOME),
        txn(LocalDate.of(2026, 7, 2), 7_000, Direction.EXPENSE),
        txn(LocalDate.of(2026, 6, 20), 9_000, Direction.EXPENSE, currency = "AED"),
    )

    @Test
    fun `totalsIn only counts the slice and the currency`() {
        val start = millis(LocalDate.of(2026, 6, 1))
        val end = millis(LocalDate.of(2026, 7, 1))
        val totals = Stats.totalsIn(txns, start, end, "OMR")
        assertEquals(2_000, totals.spent)
        assertEquals(1_000, totals.received)
        assertEquals(1, totals.otherCurrencyCount)
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
    fun `monthsIn lists overlapping months oldest first`() {
        val start = millis(LocalDate.of(2026, 5, 20))
        val end = millis(LocalDate.of(2026, 7, 3))
        val months = Stats.monthsIn(start, end)
        assertEquals(3, months.size)
        assertEquals(5, months[0].monthValue)
        assertEquals(7, months[2].monthValue)
    }

    @Test
    fun `avgSpentPerDayIn divides by elapsed days of a past slice`() {
        val start = millis(LocalDate.of(2026, 6, 1))
        val end = millis(LocalDate.of(2026, 6, 11)) // 10 full days, all in the past
        assertEquals(1_000, Stats.avgSpentPerDayIn(10_000, start, end))
    }
}
