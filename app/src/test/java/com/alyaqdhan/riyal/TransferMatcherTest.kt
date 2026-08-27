package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.TransferMatcher
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pairing is the one piece of logic that can silently *delete* a real expense and a
 * real income from the user's totals, so these tests lean on the refusals as hard as
 * on the matches.
 */
class TransferMatcherTest {

    private val base = 1_800_000_000_000L
    private fun minutes(n: Long) = n * 60_000L

    private fun out(
        id: String,
        amount: Long,
        at: Long,
        account: String? = "acc_a",
        currency: String = "OMR",
    ) = txn(id, amount, at, TxnType.EXPENSE, from = account, currency = currency)

    private fun inc(
        id: String,
        amount: Long,
        at: Long,
        account: String? = "acc_b",
        currency: String = "OMR",
    ) = txn(id, amount, at, TxnType.INCOME, to = account, currency = currency)

    private fun txn(
        id: String,
        amount: Long,
        at: Long,
        type: TxnType,
        from: String? = null,
        to: String? = null,
        currency: String = "OMR",
    ) = Txn(
        id = id,
        atMillis = at,
        amountMinor = amount,
        currency = currency,
        type = type,
        fromAccountId = from,
        toAccountId = to,
        merchant = null,
        sender = "BankMuscat",
        body = "test",
        categoryId = "other",
        categorySource = "auto",
        confidence = 100,
    )

    @Test
    fun `pairs an expense and an income of the same amount minutes apart`() {
        val proposals = TransferMatcher.propose(
            listOf(out("o1", 200_000, base), inc("i1", 200_000, base + minutes(2)))
        )
        assertEquals(1, proposals.size)
        assertEquals("o1", proposals[0].outTxnId)
        assertEquals("i1", proposals[0].inTxnId)
        assertEquals("acc_a", proposals[0].fromAccountId)
        assertEquals("acc_b", proposals[0].toAccountId)
    }

    @Test
    fun `refuses a pair outside the window`() {
        val proposals = TransferMatcher.propose(
            listOf(out("o1", 200_000, base), inc("i1", 200_000, base + minutes(90)))
        )
        assertTrue(proposals.isEmpty())
    }

    @Test
    fun `a transfer hint widens the window`() {
        val proposals = TransferMatcher.propose(
            txns = listOf(out("o1", 200_000, base), inc("i1", 200_000, base + minutes(90))),
            hintedIds = setOf("o1"),
        )
        assertEquals(1, proposals.size)
    }

    @Test
    fun `refuses different amounts and different currencies`() {
        assertTrue(
            TransferMatcher.propose(
                listOf(out("o1", 200_000, base), inc("i1", 200_001, base + minutes(1)))
            ).isEmpty()
        )
        assertTrue(
            TransferMatcher.propose(
                listOf(
                    out("o1", 200_000, base, currency = "OMR"),
                    inc("i1", 200_000, base + minutes(1), currency = "AED"),
                )
            ).isEmpty()
        )
    }

    @Test
    fun `money cannot move from an account to itself`() {
        val proposals = TransferMatcher.propose(
            listOf(out("o1", 200_000, base, account = "acc_a"), inc("i1", 200_000, base + minutes(1), account = "acc_a"))
        )
        assertTrue(proposals.isEmpty())
    }

    @Test
    fun `two unassigned legs are not paired`() {
        // With no accounts to move between there is nothing to net out, and pairing
        // would erase a real expense and a real income for no gain.
        val proposals = TransferMatcher.propose(
            listOf(out("o1", 200_000, base, account = null), inc("i1", 200_000, base + minutes(1), account = null))
        )
        assertTrue(proposals.isEmpty())
    }

    @Test
    fun `each transaction is consumed at most once, closest pair winning`() {
        val proposals = TransferMatcher.propose(
            listOf(
                out("o1", 200_000, base),
                inc("i1", 200_000, base + minutes(10)),
                inc("i2", 200_000, base + minutes(1)),
            )
        )
        assertEquals(1, proposals.size)
        assertEquals("i2", proposals[0].inTxnId) // the closer of the two
    }

    @Test
    fun `merging keeps both source ids so a rescan can replay the decision`() {
        val expense = out("o1", 200_000, base)
        val income = inc("i1", 200_000, base + minutes(2))
        val proposal = TransferMatcher.propose(listOf(expense, income)).single()
        val merged = TransferMatcher.merge(proposal, expense, income)
        assertEquals(TxnType.TRANSFER, merged.type)
        assertEquals("acc_a", merged.fromAccountId)
        assertEquals("acc_b", merged.toAccountId)
        assertEquals(listOf("o1", "i1"), merged.legIds)
        assertEquals(base, merged.atMillis)
    }

    @Test
    fun `proposal ids are stable, so an answer survives a rescan`() {
        val first = TransferMatcher.propose(
            listOf(out("o1", 200_000, base), inc("i1", 200_000, base + minutes(2)))
        ).single()
        val second = TransferMatcher.propose(
            listOf(inc("i1", 200_000, base + minutes(2)), out("o1", 200_000, base))
        ).single()
        assertEquals(first.id, second.id)
    }

    @Test
    fun `a merged transfer carries both legs, a marked one carries the record it came from`() {
        val expense = out("o1", 200_000, base)
        val income = inc("i1", 200_000, base + minutes(2))
        val proposal = TransferMatcher.propose(listOf(expense, income)).single()

        // A confirmed pair keeps two ids: splitting it restores two records.
        assertEquals(2, TransferMatcher.merge(proposal, expense, income).legIds.size)

        // A record the scanner read as an expense keeps its own id when marked by
        // hand, so undoing the mark has something to restore it to.
        assertEquals(TxnType.EXPENSE, expense.type)
        assertTrue(expense.legIds.isEmpty())
    }

    // ── bank to bank: the legs are further apart because the money has to settle ──

    private fun account(id: String, bank: String) = Account(
        id = id, name = "", bankName = bank, last4 = null, currency = "OMR",
        openingBalanceMinor = 0L, openingAtMillis = 0L,
    )

    @Test
    fun `two banks get a wider window than two accounts at one bank`() {
        val accounts = listOf(account("acc_a", "Bank Dhofar"), account("acc_b", "Bank Muscat"))
        val pair = listOf(out("o", 250_000, base), inc("i", 250_000, base + minutes(16)))

        // The same-bank rule would have dropped this at 16 minutes.
        assertTrue(TransferMatcher.propose(pair).isEmpty())
        assertEquals(1, TransferMatcher.propose(pair, accounts = accounts).size)
    }

    @Test
    fun `the wider window does not apply within one bank`() {
        val accounts = listOf(account("acc_a", "Bank Muscat"), account("acc_b", "Bank Muscat"))
        val pair = listOf(out("o", 5_000, base), inc("i", 5_000, base + minutes(90)))
        assertTrue(TransferMatcher.propose(pair, accounts = accounts).isEmpty())
    }

    @Test
    fun `an unknown bank gets the strict window, not the benefit of the doubt`() {
        val accounts = listOf(account("acc_a", "Bank Dhofar"), account("acc_b", ""))
        val pair = listOf(out("o", 5_000, base), inc("i", 5_000, base + minutes(20)))
        assertTrue(TransferMatcher.propose(pair, accounts = accounts).isEmpty())
    }

    @Test
    fun `even between banks a coincidence hours later is not proposed`() {
        val accounts = listOf(account("acc_a", "Bank Dhofar"), account("acc_b", "Bank Muscat"))
        val pair = listOf(out("o", 5_000, base), inc("i", 5_000, base + minutes(300)))
        assertTrue(TransferMatcher.propose(pair, accounts = accounts).isEmpty())
    }

}
