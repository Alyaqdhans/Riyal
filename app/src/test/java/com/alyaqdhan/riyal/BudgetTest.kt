package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.BudgetPlan
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTest {

    private val day = 24L * 3_600_000L
    private val start = 1_800_000_000_000L
    private val end = start + 30 * day

    private val plan = BudgetPlan(
        id = "bp_1",
        label = "This month",
        startMillis = start,
        endExclusiveMillis = end,
        lines = mapOf("food" to 100_000L, "transport" to 50_000L),
    )

    private fun txn(
        id: String,
        amount: Long,
        at: Long,
        categoryId: String,
        type: TxnType = TxnType.EXPENSE,
    ) = Txn(
        id = id, atMillis = at, amountMinor = amount, currency = "OMR", type = type,
        fromAccountId = if (type == TxnType.EXPENSE) "acc_a" else null,
        toAccountId = if (type == TxnType.EXPENSE) null else "acc_a",
        merchant = null, sender = "BankMuscat", body = "t",
        categoryId = categoryId, categorySource = "auto", confidence = 100,
    )

    @Test
    fun `progress sums each capped category and flags the ones over`() {
        val txns = listOf(
            txn("a", 60_000, start + day, "food"),
            txn("b", 52_000, start + 2 * day, "transport"),
        )
        val p = Stats.budgetProgress(plan, txns, "OMR", now = start + 15 * day)
        assertEquals(112_000L, p.totalSpentMinor)
        assertEquals(150_000L, p.totalCapMinor)
        val transport = p.lines.single { it.categoryId == "transport" }
        assertTrue(transport.over)
        assertEquals(-2_000L, transport.remainingMinor)
        assertFalse(p.lines.single { it.categoryId == "food" }.over)
    }

    @Test
    fun `spending outside a capped category is reported, not hidden`() {
        val txns = listOf(
            txn("a", 60_000, start + day, "food"),
            txn("b", 25_000, start + day, "shopping"), // no cap for shopping
        )
        val p = Stats.budgetProgress(plan, txns, "OMR", now = start + 15 * day)
        assertEquals(60_000L, p.totalSpentMinor)
        assertEquals(25_000L, p.unbudgetedMinor)
    }

    @Test
    fun `pace compares money spent against calendar elapsed`() {
        val txns = listOf(txn("a", 120_000, start + day, "food"))
        // A fifth of the way through the period, 80% of the budget is gone.
        val early = Stats.budgetProgress(plan, txns, "OMR", now = start + 6 * day)
        assertEquals(0.2f, early.elapsedFraction, 0.01f)
        assertTrue(early.aheadOfPace)

        // The same spend at the very end of the period is not "ahead" of anything.
        val late = Stats.budgetProgress(plan, txns, "OMR", now = end)
        assertEquals(1f, late.elapsedFraction, 0.01f)
        assertFalse(late.aheadOfPace)
    }

    @Test
    fun `transfers never count towards a budget`() {
        val txns = listOf(
            txn("a", 60_000, start + day, "food"),
            txn("t", 500_000, start + day, "food", type = TxnType.TRANSFER),
        )
        val p = Stats.budgetProgress(plan, txns, "OMR", now = start + 15 * day)
        assertEquals(60_000L, p.totalSpentMinor)
        assertEquals(0L, p.unbudgetedMinor)
    }

    @Test
    fun `records outside the plan's period are not counted`() {
        val txns = listOf(
            txn("before", 90_000, start - day, "food"),
            txn("inside", 10_000, start + day, "food"),
            txn("after", 90_000, end + day, "food"),
        )
        val p = Stats.budgetProgress(plan, txns, "OMR", now = start + 15 * day)
        assertEquals(10_000L, p.totalSpentMinor)
    }

    @Test
    fun `overlaps and covers describe the plan's own window`() {
        assertTrue(plan.covers(start))
        assertFalse(plan.covers(end))
        assertTrue(plan.overlaps(start - day, start + day))
        assertFalse(plan.overlaps(end, end + day))
    }

    @Test
    fun `the summary shows what is closest to its cap, not the biggest cap`() {
        // A small category already over its limit must not be hidden behind "show all"
        // by two large plans that are barely touched.
        val lines = listOf(
            Stats.BudgetLineProgress("food", spentMinor = 10_000, capMinor = 500_000),
            Stats.BudgetLineProgress("transport", spentMinor = 5_000, capMinor = 300_000),
            Stats.BudgetLineProgress("coffee", spentMinor = 12_000, capMinor = 10_000),
            Stats.BudgetLineProgress("fuel", spentMinor = 40_000, capMinor = 50_000),
        )
        val shown = Stats.mostAtRisk(lines, 2)
        assertEquals(listOf("coffee", "fuel"), shown.map { it.categoryId })
        assertTrue(shown.first().over)
    }

    @Test
    fun `a capless line never outranks one that is actually spending`() {
        val lines = listOf(
            Stats.BudgetLineProgress("broken", spentMinor = 90_000, capMinor = 0),
            Stats.BudgetLineProgress("food", spentMinor = 5_000, capMinor = 100_000),
        )
        assertEquals("food", Stats.mostAtRisk(lines, 1).single().categoryId)
    }
}
