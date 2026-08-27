package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.screens.TxnSort
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Biggest out" is asked when someone wants to know where the money went, so the answer
 * must not be led by income that happens to be larger.
 */
class TxnSortTest {

    private fun txn(id: String, amount: Long, type: TxnType, at: Long = 0L) = Txn(
        id = id, atMillis = at, amountMinor = amount, currency = "OMR", type = type,
        fromAccountId = if (type == TxnType.INCOME) null else "acc_a",
        toAccountId = if (type == TxnType.INCOME) "acc_a" else null,
        merchant = null, sender = "BankMuscat", body = "t",
        categoryId = "other", categorySource = "auto", confidence = 100,
    )

    private val data = listOf(
        txn("salary", 900_000, TxnType.INCOME, at = 30),
        txn("rent", 400_000, TxnType.EXPENSE, at = 10),
        txn("coffee", 1_500, TxnType.EXPENSE, at = 20),
        txn("gift", 50_000, TxnType.INCOME, at = 40),
    )

    @Test
    fun `biggest out ranks spending first, largest first`() {
        val out = TxnSort.BIGGEST_OUT.applyTo(data).map { it.id }
        assertEquals(listOf("rent", "coffee"), out.take(2))
    }

    @Test
    fun `biggest in ranks income first, largest first`() {
        val out = TxnSort.BIGGEST_IN.applyTo(data).map { it.id }
        assertEquals(listOf("salary", "gift"), out.take(2))
    }

    @Test
    fun `every record survives a re-sort`() {
        TxnSort.entries.forEach { order ->
            assertEquals(data.size, order.applyTo(data).size)
            assertEquals(data.map { it.id }.toSet(), order.applyTo(data).map { it.id }.toSet())
        }
    }

    @Test
    fun `date orders are opposites of each other`() {
        assertEquals(
            TxnSort.NEWEST.applyTo(data).map { it.id },
            TxnSort.OLDEST.applyTo(data).map { it.id }.reversed(),
        )
    }
}
