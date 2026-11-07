package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.PlaceGroup
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.data.UserRule
import org.junit.Assert.assertEquals
import org.junit.Test

/** Transactions' search: what a row says, or its amount, finds it. */
class RecordSearchTest {

    private fun txn(
        id: String,
        amount: Long,
        merchant: String?,
        category: String = "food",
        sender: String = "BankSMS",
        account: String = "acc_a",
        original: Pair<Long, String>? = null,
    ) = Txn(
        id = id, atMillis = 1_800_000_000_000L, amountMinor = amount, currency = "OMR",
        type = TxnType.EXPENSE, fromAccountId = account,
        merchant = merchant, sender = sender, body = "b $id",
        categoryId = category, categorySource = "user", confidence = 90,
        originalAmountMinor = original?.first, originalCurrency = original?.second,
    )

    private val cafe = txn("1", 2_500, "Example Cafe")
    private val market = txn("2", 12_500, "Sample Market", category = "groceries", account = "acc_b")
    private val unnamed = txn("3", 1_234_500, null, category = "shopping", sender = "OtherBank")
    private val abroad = txn("4", 3_850, "Example Store", category = "shopping", original = 999L to "USD")
    private val all = listOf(cafe, market, unnamed, abroad)

    private val accounts = listOf(
        Account(id = "acc_a", name = "", bankName = "Gulf Test Bank", last4 = "0019", currency = "OMR",
            openingBalanceMinor = 0, openingAtMillis = 0),
        Account(id = "acc_b", name = "Savings", bankName = "Gulf Test Bank", last4 = "0042", currency = "OMR",
            openingBalanceMinor = 0, openingAtMillis = 0),
    )

    private fun ids(query: String, groups: List<PlaceGroup> = emptyList()) =
        Stats.search(all, query, accounts, groups).map { it.id }

    @Test
    fun `a blank search finds every record`() {
        assertEquals(listOf("1", "2", "3", "4"), ids("  "))
    }

    @Test
    fun `the place is found whatever its case`() {
        assertEquals(listOf("1", "4"), ids("example"))
        assertEquals(listOf("2"), ids("MARKET"))
    }

    @Test
    fun `a merged place is found by the name it goes by`() {
        val group = PlaceGroup("merged:x", "Corner Spot", setOf(UserRule.patternOf("Example Cafe")))
        assertEquals(listOf("1"), ids("corner", listOf(group)))
    }

    @Test
    fun `the category, the account and the sender find records`() {
        assertEquals(listOf("2"), ids("Groceries"))
        assertEquals(listOf("2"), ids("savings"))
        assertEquals(listOf("1", "3", "4"), ids("0019"))
        assertEquals(listOf("3"), ids("otherbank"))
    }

    @Test
    fun `an amount is found as written, without its grouping`() {
        assertEquals(listOf("2"), ids("12.5"))
        assertEquals(listOf("3"), ids("1,234.5"))
        assertEquals(listOf("3"), ids("1234"))
    }

    @Test
    fun `what a shop abroad charged finds its record`() {
        assertEquals(listOf("4"), ids("9.99"))
    }

    @Test
    fun `arabic digits find the same amount`() {
        assertEquals(listOf("2"), ids("١٢٫٥"))
    }
}
