package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.AccountDiscovery
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * An archived record is kept, and counted in nothing. [Stats.counted] is the gate every
 * figure reads through, so what it lets past is what a total or a balance can add up.
 */
class ArchivedTest {

    private val base = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun txn(
        id: String,
        amount: Long,
        at: Long = base + hour,
        type: TxnType = TxnType.EXPENSE,
    ) = Txn(
        id = id, atMillis = at, amountMinor = amount, currency = "OMR",
        type = type,
        fromAccountId = if (type == TxnType.INCOME) null else "acc_a",
        toAccountId = if (type == TxnType.INCOME) "acc_a" else null,
        merchant = "Example Cafe", sender = "Bank", body = "b $id",
        categoryId = "food", categorySource = "user", confidence = 90,
    )

    @Test
    fun `an archived record leaves the records that count`() {
        val records = listOf(txn("kept", 10_000), txn("gone", 4_000))
        assertEquals(listOf("kept"), Stats.counted(records, setOf("gone")).map { it.id })
    }

    @Test
    fun `nothing archived hands back the same list`() {
        val records = listOf(txn("kept", 10_000))
        assertSame(records, Stats.counted(records, emptySet()))
    }

    @Test
    fun `an archived record is in no total`() {
        val records = listOf(
            txn("spent", 10_000),
            txn("gone", 4_000),
            txn("paid", 50_000, type = TxnType.INCOME),
            txn("gone in", 1_000, type = TxnType.INCOME),
        )
        val totals = Stats.totalsIn(
            Stats.counted(records, setOf("gone", "gone in")),
            base, base + 2 * hour, "OMR",
        )
        assertEquals(10_000L, totals.spent)
        assertEquals(50_000L, totals.received)
    }

    @Test
    fun `an archived record is in no breakdown`() {
        val records = listOf(txn("spent", 10_000), txn("gone", 4_000))
        val slices = Stats.breakdownIn(Stats.counted(records, setOf("gone")), base, base + 2 * hour, "OMR")
        assertEquals(10_000L, slices.single().amountMinor)
    }

    @Test
    fun `an archived record stops moving the balance`() {
        val account = Account(
            id = "acc_a", name = "Main", bankName = "Example Bank", last4 = "1234",
            currency = "OMR", openingBalanceMinor = 100_000, openingAtMillis = base,
        )
        val records = listOf(txn("spent", 10_000), txn("gone", 4_000))
        assertEquals(86_000L, AccountDiscovery.balanceOf(account, records))
        assertEquals(90_000L, AccountDiscovery.balanceOf(account, Stats.counted(records, setOf("gone"))))
    }

    @Test
    fun `the bank's own opening figure is not recounted for a record before it`() {
        // A record older than the opening moment was never added on: it is already inside
        // the figure the bank quoted, so archiving it changes nothing.
        val account = Account(
            id = "acc_a", name = "Main", bankName = "Example Bank", last4 = "1234",
            currency = "OMR", openingBalanceMinor = 100_000, openingAtMillis = base,
        )
        val older = txn("older", 4_000, at = base - hour)
        assertEquals(100_000L, AccountDiscovery.balanceOf(account, Stats.counted(listOf(older), setOf("older"))))
    }
}
