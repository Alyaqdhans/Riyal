package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test

/** What orders the chips in a category picker. */
class CategoryRankTest {

    private var seq = 0

    private fun txn(
        categoryId: String,
        source: String = "user",
        type: TxnType = TxnType.EXPENSE,
    ) = Txn(
        id = "t${seq++}",
        atMillis = 0L,
        amountMinor = 1000L,
        currency = "OMR",
        type = type,
        fromAccountId = if (type == TxnType.INCOME) null else "a1",
        toAccountId = if (type == TxnType.EXPENSE) null else "a2",
        merchant = "m",
        sender = "BANK",
        body = "b",
        categoryId = categoryId,
        categorySource = source,
        confidence = 90,
    )

    @Test
    fun `only the user's own answers are counted`() {
        val use = Stats.categoryUse(
            listOf(
                txn("food"), txn("food"),
                txn("travel", source = "auto"),
                txn("travel", source = "auto"),
                txn("travel", source = "auto"),
                txn("health"),
            )
        )
        assertEquals(mapOf("food" to 2, "health" to 1), use)
    }

    @Test
    fun `a transfer never votes`() {
        val use = Stats.categoryUse(
            listOf(txn(Categories.TRANSFER_ID, type = TxnType.TRANSFER), txn("food"))
        )
        assertEquals(mapOf("food" to 1), use)
    }

    @Test
    fun `most used first`() {
        val cats = Categories.forType(TxnType.EXPENSE)
        val ranked = Stats.rankCategories(cats, mapOf("charity" to 9, "cash" to 4))
        assertEquals("charity", ranked[0].id)
        assertEquals("cash", ranked[1].id)
    }

    @Test
    fun `no history at all leaves the declared order alone`() {
        val cats = Categories.forType(TxnType.EXPENSE)
        assertEquals(cats.map { it.id }, Stats.rankCategories(cats, emptyMap()).map { it.id })
    }

    @Test
    fun `categories with no history keep their declared order behind the ranked ones`() {
        val cats = Categories.forType(TxnType.EXPENSE)
        val ranked = Stats.rankCategories(cats, mapOf("charity" to 3))
        val rest = ranked.drop(1).map { it.id }
        assertEquals(cats.map { it.id }.filterNot { it == "charity" }, rest)
    }

    @Test
    fun `ranking never invents or drops a category`() {
        for (type in listOf(TxnType.EXPENSE, TxnType.INCOME)) {
            val cats = Categories.forType(type)
            val ranked = Stats.rankCategories(cats, mapOf("food" to 5, "salary" to 2))
            assertEquals(cats.toSet(), ranked.toSet())
            assertEquals(cats.size, ranked.size)
        }
    }
}
