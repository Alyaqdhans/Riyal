package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.MoneySenders
import com.alyaqdhan.riyal.data.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A sender the gate leaves out, judged by what it sends. Made-up messages: a wallet whose
 * name is like no bank's, a telecom's adverts and bills.
 */
class MoneySendersTest {

    private val parser = SmsParser(Prefs.DEFAULT_EXPENSE_KEYWORDS, Prefs.DEFAULT_INCOME_KEYWORDS, "OMR")
    private val hour = 3_600_000L
    private val base = 1_800_000_000_000L

    private fun seen(i: Int, body: String) = MoneySenders.seen("m$i", base + i * hour, body, parser)

    /** A wallet's payments, each quoting the balance after it. */
    private fun walletPayment(i: Int, amount: String, balance: String) = seen(
        i,
        "Your Example Pay card was used to pay OMR $amount to EXAMPLE CAFE on 0$i/02/2026. " +
            "Available balance OMR $balance.",
    )

    @Test
    fun `a wallet whose balance moves by each payment looks like money`() {
        val seen = listOf(
            walletPayment(1, "4.500", "20.000"),
            walletPayment(2, "2.000", "18.000"),
            walletPayment(3, "3.250", "14.750"),
        )
        val e = MoneySenders.evidence("ExamplePay", seen, null)
        assertEquals(3, e.messages)
        assertEquals(3, e.withBalance)
        assertEquals(2, e.proofs)
        assertTrue(e.looksLikeMoney)
    }

    @Test
    fun `adverts and bills quote amounts but no balance that moves`() {
        val seen = listOf(
            seen(1, "Win cash prizes up to OMR 60,000! Recharge now and enter the draw."),
            seen(2, "Your bill of OMR 12.500 is due on 05/03/2026. Pay now to avoid suspension."),
            seen(3, "Get 10GB for only OMR 3.000 this weekend. Dial *123#"),
            seen(4, "Your bill of OMR 11.900 is due on 05/04/2026. Pay now to avoid suspension."),
            seen(5, "Thank you for your payment of OMR 12.500."),
        )
        val e = MoneySenders.evidence("ExampleTel", seen, null)
        assertEquals(0, e.proofs)
        assertFalse(e.looksLikeMoney)
    }

    @Test
    fun `messages without an amount are counted but not parsed`() {
        val s = seen(1, "Your one-time code is 4821. Do not share it.")
        assertEquals(null, s.amountMinor)
        assertFalse(MoneySenders.mightBeMoney("Your one-time code is 4821."))
        assertTrue(MoneySenders.mightBeMoney("Paid OMR 4.500 at EXAMPLE CAFE"))
    }

    @Test
    fun `a later scan goes on from where the last one left the balance`() {
        val first = MoneySenders.evidence("ExamplePay", listOf(walletPayment(1, "4.500", "20.000")), null)
        assertEquals(0, first.proofs)
        assertFalse(first.looksLikeMoney)
        // The next scan rereads the first message (its overlap) and reads two new ones.
        val again = listOf(
            walletPayment(1, "4.500", "20.000"),
            walletPayment(2, "2.000", "18.000"),
            walletPayment(3, "3.250", "14.750"),
        )
        val later = MoneySenders.evidence("ExamplePay", again, first)
        assertEquals(3, later.messages)
        assertEquals(2, later.proofs)
        assertTrue(later.looksLikeMoney)
    }

    @Test
    fun `quoting a balance often enough is reason to ask too`() {
        // Balances that do not chain - a fee nobody texted about between each - but every
        // message quotes one.
        val seen = listOf(
            walletPayment(1, "4.500", "20.000"),
            walletPayment(2, "2.000", "17.500"),
            walletPayment(3, "3.250", "13.000"),
        )
        val e = MoneySenders.evidence("ExamplePay", seen, null)
        assertEquals(0, e.proofs)
        assertTrue(e.looksLikeMoney)
    }
}
