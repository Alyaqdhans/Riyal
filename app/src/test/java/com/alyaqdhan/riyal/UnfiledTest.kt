package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.data.UserRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backlog is grouped so one choice files many records. What it must never do is
 * ask again about something already answered, or put two sides of the ledger in one
 * decision.
 */
class UnfiledTest {

    private fun txn(
        id: String,
        amount: Long,
        merchant: String?,
        category: String = "other",
        source: String = "auto",
        type: TxnType = TxnType.EXPENSE,
        currency: String = "OMR",
    ) = Txn(
        id = id, atMillis = 1_800_000_000_000L, amountMinor = amount, currency = currency,
        type = type,
        fromAccountId = if (type == TxnType.INCOME) null else "acc_a",
        toAccountId = if (type == TxnType.INCOME) "acc_a" else null,
        merchant = merchant, sender = "Meethaq", body = "b $merchant",
        categoryId = category, categorySource = source, confidence = 90,
    )

    @Test
    fun `records of one merchant become one decision`() {
        val groups = Stats.unfiledByMerchant(
            listOf(
                txn("1", 10_000, "Al Fatah"),
                txn("2", 4_000, "Al Fatah"),
                txn("3", 2_000, "Turkish Days"),
            ),
            "OMR",
        )
        assertEquals(2, groups.size)
        assertEquals("Al Fatah", groups[0].merchant)
        assertEquals(2, groups[0].count)
        assertEquals(14_000L, groups[0].amountMinor)
        assertEquals(listOf("1", "2"), groups[0].txnIds)
    }

    @Test
    fun `the biggest money comes first, not the newest`() {
        val groups = Stats.unfiledByMerchant(
            listOf(
                txn("1", 500, "Small Shop"),
                txn("2", 90_000, "Big Shop"),
                txn("3", 4_000, "Middling"),
            ),
            "OMR",
        )
        assertEquals(listOf("Big Shop", "Middling", "Small Shop"), groups.map { it.merchant })
    }

    @Test
    fun `a category the user chose is never asked about again`() {
        val groups = Stats.unfiledByMerchant(
            listOf(
                txn("1", 10_000, "Al Fatah"),
                // Filed by hand as Other: an answer, even though it is the same category.
                txn("2", 10_000, "Turkish Days", source = "user"),
                // Auto, but the scan did place it.
                txn("3", 10_000, "Lulu", category = "groceries"),
            ),
            "OMR",
        )
        assertEquals(listOf("Al Fatah"), groups.map { it.merchant })
    }

    @Test
    fun `one name on both sides of the ledger is two decisions`() {
        val groups = Stats.unfiledByMerchant(
            listOf(
                txn("1", 20_000, "MBI"),
                txn("2", 30_000, "MBI", category = "income", type = TxnType.INCOME),
            ),
            "OMR",
        )
        assertEquals(2, groups.size)
        assertEquals(TxnType.INCOME, groups[0].type)
        assertEquals(TxnType.EXPENSE, groups[1].type)
    }

    @Test
    fun `a merchant is one merchant however the bank capitalised it`() {
        val groups = Stats.unfiledByMerchant(
            listOf(txn("1", 1_000, "Al Fatah"), txn("2", 1_000, "AL FATAH")),
            "OMR",
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().count)
        assertEquals("Al Fatah", groups.single().merchant)
    }

    @Test
    fun `records naming nobody are left out, and transfers never appear`() {
        val all = listOf(
            txn("1", 1_000, null),
            txn("2", 1_000, "Al Fatah"),
            txn("3", 50_000, "MBI", category = "transfer", type = TxnType.TRANSFER),
        )
        assertEquals(listOf("Al Fatah"), Stats.unfiledByMerchant(all, "OMR").map { it.merchant })
        // Still counted as unfiled - they need a category, they just cannot be batched.
        assertEquals(setOf("1", "2"), Stats.unfiled(all).map { it.id }.toSet())
    }

    @Test
    fun `an archived record is out of the way`() {
        val all = listOf(txn("1", 1_000, "Al Fatah"), txn("2", 9_000, "Turkish Days"))
        val groups = Stats.unfiledByMerchant(all, "OMR", archived = setOf("2"))
        assertEquals(listOf("Al Fatah"), groups.map { it.merchant })
    }

    @Test
    fun `another currency is not folded into the total`() {
        val all = listOf(
            txn("1", 1_000, "Al Fatah"),
            txn("2", 9_000, "Al Fatah", currency = "AED"),
        )
        val groups = Stats.unfiledByMerchant(all, "OMR")
        assertEquals(1, groups.single().count)
        assertTrue(groups.single().amountMinor == 1_000L)
    }
    @Test
    fun `two spellings of one shop are one decision`() {
        // The Arabic message named the shop and stopped; the English one ran on into
        // the account, and the leftover "in" made it a second shop with its own row.
        val groups = Stats.unfiledByMerchant(
            listOf(
                txn("1", 25_000, "ALIS SALIM"),
                txn("2", 25_000, "ALIS SALIM in"),
                txn("3", 10_000, "alis salim"),
            ),
            "OMR",
        )
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().count)
        assertEquals(60_000L, groups.single().amountMinor)
        // The bank's own capitalisation, and the spelling it used most.
        assertEquals("ALIS SALIM", groups.single().merchant)
        assertEquals(listOf("1", "2", "3"), groups.single().txnIds)
    }

    @Test
    fun `a connector inside the name does not merge two shops`() {
        val groups = Stats.unfiledByMerchant(
            listOf(txn("1", 1_000, "Made in Oman"), txn("2", 1_000, "Made")),
            "OMR",
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun `a name that is nothing but connectors still keys to something`() {
        // A blank pattern is a rule that matches every message ever sent.
        assertTrue(UserRule.patternOf("in").isNotBlank())
        assertTrue(UserRule.patternOf("  to  ").isNotBlank())
    }

    @Test
    fun `a record left under Other is out of the backlog, not answered`() {
        val all = listOf(txn("1", 10_000, "Al Fatah"), txn("2", 9_000, "Turkish Days"))
        val groups = Stats.unfiledByMerchant(all, "OMR", deferred = setOf("1"))
        assertEquals(listOf("Turkish Days"), groups.map { it.merchant })
        assertEquals(listOf("2"), Stats.unfiled(all, deferred = setOf("1")).map { it.id })
        // Nothing about the record itself changed - drop the deferral and it is back,
        // which is what "you'll be asked again next time" has to mean.
        assertEquals(2, Stats.unfiled(all).size)
    }

    @Test
    fun `a group carries its records, newest first`() {
        val groups = Stats.unfiledByMerchant(
            listOf(
                txn("old", 1_000, "Al Fatah").copy(atMillis = 1_000L),
                txn("new", 2_000, "Al Fatah").copy(atMillis = 3_000L),
                txn("mid", 3_000, "Al Fatah").copy(atMillis = 2_000L),
            ),
            "OMR",
        )
        assertEquals(listOf("new", "mid", "old"), groups.single().txnIds)
        assertEquals(3, groups.single().count)
    }

}
