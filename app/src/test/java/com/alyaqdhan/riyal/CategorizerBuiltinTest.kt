package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Categorizer
import com.alyaqdhan.riyal.data.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The built-in keyword table, checked as a table rather than one merchant at a time.
 *
 * The failure this exists for is silent: [Categories.byId] answers "Other" for an id it
 * does not know, so a single typo in the mapping turns a whole group of merchants into
 * Other with nothing in the log to say so.
 */
class CategorizerBuiltinTest {

    private fun categorize(direction: Direction, body: String) =
        Categorizer.categorize(direction, null, body, emptyList())

    @Test
    fun `every keyword points at a category that exists`() {
        val known = Categories.BUILTIN.map { it.id }.toSet()
        val unknown = Categorizer.BUILTIN
            .map { it.second }
            .distinct()
            .filterNot { it in known }
        assertEquals("keywords pointing at a category that does not exist", emptyList<String>(), unknown)
    }

    @Test
    fun `no keyword is listed twice for the same side of the ledger`() {
        val dupes = Categorizer.BUILTIN
            .groupBy { it.first to Categories.byId(it.second).income }
            .filterValues { it.size > 1 }
            .keys
        assertEquals(emptySet<Pair<String, Boolean>>(), dupes)
    }

    @Test
    fun `power and water no longer land in the telecom category`() {
        assertEquals("utilities", categorize(Direction.EXPENSE, "NAMA Electricity bill paid").categoryId)
        assertEquals("utilities", categorize(Direction.EXPENSE, "DIAM water charge").categoryId)
        assertEquals("bills", categorize(Direction.EXPENSE, "Omantel recharge").categoryId)
    }

    @Test
    fun `streaming is a subscription, a storefront is not`() {
        assertEquals("subscriptions", categorize(Direction.EXPENSE, "NETFLIX.COM").categoryId)
        assertEquals("subscriptions", categorize(Direction.EXPENSE, "Anghami Plus").categoryId)
        // Google Play sells one-off apps and games too, so it stays where it was.
        assertEquals("entertainment", categorize(Direction.EXPENSE, "GOOGLE PLAY").categoryId)
    }

    @Test
    fun `a loan is told apart by the direction of the message, not the word`() {
        assertEquals("loan", categorize(Direction.EXPENSE, "Loan installment debited").categoryId)
        assertEquals("borrowed", categorize(Direction.INCOME, "Loan amount credited").categoryId)
    }

    @Test
    fun `a hotel spa is travel, because spa matches as a whole word`() {
        assertEquals("travel", categorize(Direction.EXPENSE, "GRAND HOTEL SPA MUSCAT").categoryId)
        assertEquals("personalcare", categorize(Direction.EXPENSE, "AL NAHDA SPA").categoryId)
    }

    @Test
    fun `rent has no english keyword, so a car rental is not filed as housing`() {
        assertTrue(categorize(Direction.EXPENSE, "CAR RENT LLC").categoryId != "rent")
        assertEquals("rent", categorize(Direction.EXPENSE, "دفع إيجار الشقة").categoryId)
    }

    @Test
    fun `the new categories are reachable and on the side they belong to`() {
        val cases = mapOf(
            "AL MADINA INSURANCE" to "insurance",
            "ZAKAT payment" to "charity",
            "MUSCAT MUNICIPALITY fee" to "government",
            "IKEA Oman" to "home",
            "GENTS SALON" to "personalcare",
        )
        for ((body, expected) in cases) {
            assertEquals(body, expected, categorize(Direction.EXPENSE, body).categoryId)
        }
        assertEquals("cashback", categorize(Direction.INCOME, "Cashback credited").categoryId)
        assertEquals("reimbursement", categorize(Direction.INCOME, "Expense claim reimburse").categoryId)
    }
}
