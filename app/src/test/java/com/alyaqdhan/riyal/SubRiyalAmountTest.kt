package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Amounts under one riyal, written with nothing before the point (".543 OMR"), in either
 * script's digits, and in an Arabic message with the point after the digits ("543. OMR"),
 * which is how ".543" looks on a right-to-left screen. Read as they stood, 543 baisa was
 * 543 riyals. Made-up messages in a wallet's shape, made-up figures.
 */
class SubRiyalAmountTest {

    private val parser = SmsParser(Prefs.DEFAULT_EXPENSE_KEYWORDS, Prefs.DEFAULT_INCOME_KEYWORDS, "OMR")

    private fun parsed(body: String) = parser.parse(body) as SmsParser.Result.Parsed

    private fun payment(amount: String, balance: String = "12.250") =
        "عزيزي المستخدم ، تم استخدام بطاقة موجب الخاصة بك رقم 1111XXXXXXXX1234 " +
            "لدفع مبلغ $amount OMR  إلى EXAMPLE CAFE بتاريخ 03/02/2026. " +
            "الرصيد المتاح لديك هو $balance OMR ر.ع."

    @Test
    fun `an amount under a riyal keeps its point`() {
        val p = parsed(payment(".543"))
        assertEquals(543L, p.amountMinor)
        assertEquals("OMR", p.currency)
        assertEquals(12_250L, p.balanceMinor)
    }

    @Test
    fun `in Arabic digits`() {
        val p = parsed(payment("٫٥٤٣", balance = "١٢٫٢٥٠"))
        assertEquals(543L, p.amountMinor)
        assertEquals(12_250L, p.balanceMinor)
    }

    @Test
    fun `with the point after the digits, as it looks right to left`() {
        assertEquals(543L, parsed(payment("543.")).amountMinor)
    }

    @Test
    fun `in Arabic digits with the point after them`() {
        assertEquals(543L, parsed(payment("٥٤٣٫")).amountMinor)
    }

    @Test
    fun `half a riyal is not five hundred`() {
        assertEquals(500L, parsed(payment(".500")).amountMinor)
        assertEquals(500L, parsed(payment("500.")).amountMinor)
    }

    @Test
    fun `a balance under a riyal`() {
        assertEquals(250L, parsed(payment("4.500", balance = ".250")).balanceMinor)
    }

    @Test
    fun `the currency first`() {
        val fee = "Dear Customer, your Mojab Card XX1234 was charged OMR .500 for annual fees on 10-02-2026."
        assertEquals(500L, parsed(fee).amountMinor)
    }

    @Test
    fun `a wallet check of nothing is still nothing`() {
        // ".00 USD": put back as "0.00", still a charge of nothing.
        val check = payment(".00").replace("OMR  إلى", "USD  إلى")
        assertEquals(SmsParser.Result.Skipped::class, parser.parse(check)::class)
    }

    @Test
    fun `whole riyals and amounts with their point are left as they are`() {
        assertEquals(5_000L, parsed(payment("5")).amountMinor)
        assertEquals(4_500L, parsed(payment("4.500")).amountMinor)
        assertEquals(1_234_500L, parsed(payment("1,234.500")).amountMinor)
    }

    @Test
    fun `a reference is not an amount`() {
        // "Ref.123" has no currency beside it.
        val p = parsed("Ref.123 paid OMR 5.000 to EXAMPLE CAFE")
        assertEquals(5_000L, p.amountMinor)
        assertEquals("EXAMPLE CAFE", p.merchant)
    }

    @Test
    fun `a full stop before the next amount stays a full stop`() {
        // "100." ends what came before; the currency after it starts the amount. Taken
        // for ".100", it would be a second amount nearer the keyword than the real one.
        assertEquals(5_000L, parsed("تم خصم 100. OMR 5.000 من حسابك").amountMinor)
        assertEquals(5_000L, parser.parseAs("تم خصم 100. OMR 5.000 من حسابك", Direction.EXPENSE)!!.amountMinor)
    }
}
