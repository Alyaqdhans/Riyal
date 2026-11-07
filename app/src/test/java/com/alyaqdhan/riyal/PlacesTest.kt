package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Analysis's Places tab: a period's spending, by where it went. */
class PlacesTest {

    private val base = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val end = base + 24 * hour

    private fun txn(
        id: String,
        amount: Long,
        merchant: String?,
        category: String = "food",
        type: TxnType = TxnType.EXPENSE,
        at: Long = base + hour,
        currency: String = "OMR",
        account: String = "acc_a",
    ) = Txn(
        id = id, atMillis = at, amountMinor = amount, currency = currency,
        type = type,
        fromAccountId = if (type == TxnType.INCOME) null else account,
        toAccountId = if (type == TxnType.INCOME) account else null,
        merchant = merchant, sender = "Bank", body = "b $id",
        categoryId = category, categorySource = "user", confidence = 90,
    )

    private fun places(vararg txns: Txn, accountId: String? = null) =
        Stats.placesIn(txns.toList(), base, end, "OMR", accountId)

    @Test
    fun `one place is one row, biggest first`() {
        val rows = places(
            txn("1", 2_000, "Example Cafe"),
            txn("2", 3_000, "Example Cafe"),
            txn("3", 9_000, "Sample Market"),
        ).places
        assertEquals(listOf("Sample Market", "Example Cafe"), rows.map { it.name })
        assertEquals(5_000L, rows[1].amountMinor)
        assertEquals(2, rows[1].count)
    }

    @Test
    fun `spellings of one place fold together`() {
        val rows = places(
            txn("1", 2_000, "EXAMPLE CAFE"),
            txn("2", 3_000, "Example Cafe"),
            txn("3", 1_000, "Example Cafe"),
        ).places
        assertEquals(1, rows.size)
        assertEquals("Example Cafe", rows.single().name)
    }

    @Test
    fun `only spending is listed`() {
        val rows = places(
            txn("1", 2_000, "Example Cafe"),
            txn("2", 8_000, "Example Employer", type = TxnType.INCOME),
            txn("3", 8_000, "Sample Move", type = TxnType.TRANSFER),
        ).places
        assertEquals(listOf("Example Cafe"), rows.map { it.name })
    }

    @Test
    fun `the period, the currency and the account all apply`() {
        val result = places(
            txn("in", 2_000, "Example Cafe"),
            txn("before", 2_000, "Early Shop", at = base - hour),
            txn("foreign", 2_000, "Abroad Shop", currency = "USD"),
            txn("elsewhere", 2_000, "Other Bank Shop", account = "acc_b"),
            accountId = "acc_a",
        )
        assertEquals(listOf("Example Cafe"), result.places.map { it.name })
    }

    @Test
    fun `the list and the unnamed come to the period's spending`() {
        val records = listOf(
            txn("1", 2_000, "Example Cafe"),
            txn("2", 3_000, null),
            txn("3", 4_000, "  "),
        )
        val result = Stats.placesIn(records, base, end, "OMR")
        assertEquals(2, result.unnamedCount)
        assertEquals(7_000L, result.unnamedMinor)
        assertEquals(
            Stats.totalsIn(records, base, end, "OMR").spent,
            result.places.sumOf { it.amountMinor } + result.unnamedMinor,
        )
    }

    @Test
    fun `a place takes the category most of its money went under`() {
        val place = places(
            txn("1", 1_000, "Example Cafe", category = "food"),
            txn("2", 1_000, "Example Cafe", category = "food"),
            txn("3", 5_000, "Example Cafe", category = "shopping"),
        ).places.single()
        assertEquals("shopping", place.categoryId)
    }

    @Test
    fun `search finds a place by any part of its name`() {
        val place = places(txn("1", 2_000, "Example Cafe")).places.single()
        assertTrue(place.matches("cafe"))
        assertTrue(place.matches("  EXAMPLE  "))
        assertTrue(place.matches(""))
        assertFalse(place.matches("market"))
    }

    @Test
    fun `search finds a masked name by either readable end`() {
        val place = places(txn("1", 2_000, "TESTXXXXXXXXNAME")).places.single()
        assertTrue(place.matches("test"))
        assertTrue(place.matches("name"))
    }

    @Test
    fun `a place's records are the ones its row added up`() {
        val records = listOf(
            txn("1", 2_000, "EXAMPLE CAFE"),
            txn("2", 3_000, "Example Cafe"),
            txn("3", 9_000, "Sample Market"),
            txn("4", 1_000, "Example Cafe", type = TxnType.INCOME),
        )
        val place = Stats.placesIn(records, base, end, "OMR").places.first { it.name.endsWith("Cafe", ignoreCase = true) }
        val behind = Stats.recordsAt(records, place.key, base, end, "OMR")
        assertEquals(setOf("1", "2"), behind.map { it.id }.toSet())
        assertEquals(place.amountMinor, behind.sumOf { it.amountMinor })
    }
}
