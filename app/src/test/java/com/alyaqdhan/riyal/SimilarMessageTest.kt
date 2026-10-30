package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * "Dismiss, and remember for similar messages" decides what similar means by the same
 * fingerprint teaching does: [Skeleton]. Made-up messages in the banks' formats.
 */
class SimilarMessageTest {

    @Test
    fun `same message kind with different numbers matches`() {
        val a = Skeleton.of("BankMuscat", "Your account balance as of 12/05/2026 10:30 is OMR 154.320")
        val b = Skeleton.of("BankMuscat", "Your account balance as of 03/06/2026 08:15 is OMR 89.100")
        assertEquals(a, b)
    }

    @Test
    fun `sender is part of the fingerprint`() {
        val a = Skeleton.of("BankMuscat", "Balance is OMR 10.000")
        val b = Skeleton.of("NBO", "Balance is OMR 10.000")
        assertNotEquals(a, b)
    }

    @Test
    fun `different wording does not match`() {
        val a = Skeleton.of("BankMuscat", "Your card was charged OMR 5.000")
        val b = Skeleton.of("BankMuscat", "Loan installment of OMR 5.000 is due")
        assertNotEquals(a, b)
    }

    @Test
    fun `case spacing and arabic digits are normalized`() {
        val a = Skeleton.of("bankmuscat ", "RASEED  DAILY LIMIT ٥٠ REACHED")
        val b = Skeleton.of("BankMuscat", "Raseed daily limit 99 reached")
        assertEquals(a, b)
    }

    @Test
    fun `payments to two people are one kind of message`() {
        // Dismissing one of these with "remember" left every other one in Review: the
        // old fingerprint kept the masked names, and no two payees are masked alike.
        val a = Skeleton.of("BANK NIZWA", "Your mobile payment was accepted for SALI##########AHMD OMR on 0.5")
        val b = Skeleton.of("BANK NIZWA", "Your mobile payment was accepted for MOHA#############KHAL OMR on 0.5")
        assertEquals(a, b)
    }
}
