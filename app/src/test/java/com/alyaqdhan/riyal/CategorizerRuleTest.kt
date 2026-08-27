package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Categorizer
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.UserRule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A saved rule is keyed on a counterparty, and the same person you pay can pay you.
 * Filing the outgoing half must leave the incoming half alone.
 *
 * This is a guard rather than a repair: no merchant in the inbox it was written
 * against currently sits on both sides. It exists because filing a whole name at once
 * is now one tap, so the day one does, it must not quietly mis-file the other half.
 */
class CategorizerRuleTest {

    private val sending = listOf(UserRule("mbi", "sending"))

    @Test
    fun `a rule made on money out files money out`() {
        val m = Categorizer.categorize(
            Direction.EXPENSE, "MBI", "تم خصم 20.000 OMR عن طريق MBI", sending, "Meethaq",
        )
        assertEquals("sending", m.categoryId)
        assertEquals("your rule", m.source)
    }

    @Test
    fun `the same rule leaves money in where it was`() {
        val m = Categorizer.categorize(
            Direction.INCOME, "MBI", "لقد استلمت OMR 20.000 عن طريق MBI", sending, "Meethaq",
        )
        // Not "sending": an expense category on an income record is a wrong answer that
        // looks like a right one.
        assertEquals("income", m.categoryId)
        assertEquals("default", m.source)
    }

    @Test
    fun `a rule for each side coexists`() {
        val rules = listOf(UserRule("mbi", "sending"), UserRule("mbi", "gift"))
        assertEquals(
            "sending",
            Categorizer.categorize(Direction.EXPENSE, "MBI", "عن طريق MBI", rules, "").categoryId,
        )
        assertEquals(
            "gift",
            Categorizer.categorize(Direction.INCOME, "MBI", "عن طريق MBI", rules, "").categoryId,
        )
    }
}
