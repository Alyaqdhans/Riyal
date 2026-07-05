package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.MsgTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MsgTemplateTest {

    @Test
    fun `same message kind with different numbers matches`() {
        val a = MsgTemplate.of("BankMuscat", "Your account balance as of 12/05/2026 10:30 is OMR 154.320")
        val b = MsgTemplate.of("BankMuscat", "Your account balance as of 03/06/2026 08:15 is OMR 89.100")
        assertEquals(a, b)
    }

    @Test
    fun `sender is part of the fingerprint`() {
        val a = MsgTemplate.of("BankMuscat", "Balance is OMR 10.000")
        val b = MsgTemplate.of("NBO", "Balance is OMR 10.000")
        assertNotEquals(a, b)
    }

    @Test
    fun `different wording does not match`() {
        val a = MsgTemplate.of("BankMuscat", "Your card was charged OMR 5.000")
        val b = MsgTemplate.of("BankMuscat", "Loan installment of OMR 5.000 is due")
        assertNotEquals(a, b)
    }

    @Test
    fun `case spacing and arabic digits are normalized`() {
        val a = MsgTemplate.of("bankmuscat ", "RASEED  DAILY LIMIT ٥٠ REACHED")
        val b = MsgTemplate.of("BankMuscat", "Raseed daily limit 99 reached")
        assertEquals(a, b)
    }
}
