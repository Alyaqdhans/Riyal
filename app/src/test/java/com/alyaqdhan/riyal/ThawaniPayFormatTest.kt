package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.Banks
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Thawani Pay's Mojab card alerts, in the wallet's own shape with made-up shops, figures
 * and digits. Arabic payments say the card "was used to pay" a shop, the network's name
 * field is cut off mid-word and can trail a "+", a card added to a phone wallet is checked
 * with a zero charge, the yearly fee comes in English, and a top-up "adds an amount".
 */
class ThawaniPayFormatTest {

    private val parser = SmsParser(Prefs.DEFAULT_EXPENSE_KEYWORDS, Prefs.DEFAULT_INCOME_KEYWORDS, "OMR")

    private fun parsed(body: String) = parser.parse(body) as SmsParser.Result.Parsed

    private val purchase = "عزيزي المستخدم ، تم استخدام بطاقة موجب الخاصة بك رقم 1111XXXXXXXX1234 " +
        "لدفع مبلغ 4.500 OMR  إلى EXAMPLE CAFE AND TRADIN بتاريخ 03/02/2026. " +
        "الرصيد المتاح لديك هو 12.250 OMR ر.ع."

    private val subscription = "عزيزي المستخدم ، تم استخدام بطاقة موجب الخاصة بك رقم 1111XXXXXXXX1234 " +
        "لدفع مبلغ 9.99 USD  إلى EXAMPLE* STREAM SUB   + بتاريخ 05/02/2026. " +
        "الرصيد المتاح لديك هو 8.405 OMR ر.ع."

    private val walletCheck = "عزيزي المستخدم ، تم استخدام بطاقة موجب الخاصة بك رقم 1111XXXXXXXX1234 " +
        "لدفع مبلغ .00 USD  إلى Example Wallet Servic  بتاريخ 06/02/2026. " +
        "الرصيد المتاح لديك هو 8.405 OMR ر.ع."

    private val fee = "Dear Customer, your Mojab Card XX1234 was charged OMR 2.15 for annual fees " +
        "on 10-02-2026 21:40. Thank you for using Thawani."

    private val topUp = "تم إضافة مبلغ  5.0 OMR إلى بطاقة موجب الخاصة بك XX1234 " +
        "بتاريخ 12-02-2026 18:05. رصيدك المتاح هو 11.255 OMR ."

    @Test
    fun `the sender is read as a bank`() {
        assertTrue(Banks.looksLikeBank("Thawani Pay"))
        assertEquals("Thawani Pay", Banks.displayName("Thawani Pay"))
    }

    @Test
    fun `a payment reads its amount, shop and balance`() {
        val p = parsed(purchase)
        assertEquals(Direction.EXPENSE, p.direction)
        assertEquals(4_500L, p.amountMinor)
        assertEquals("OMR", p.currency)
        assertEquals("EXAMPLE CAFE AND TRADIN", p.merchant)
        assertEquals(12_250L, p.balanceMinor)
        assertEquals("OMR", p.balanceCurrency)
    }

    @Test
    fun `a payment in dollars keeps the dollars and drops the trailing plus`() {
        val p = parsed(subscription)
        assertEquals(Direction.EXPENSE, p.direction)
        assertEquals(999L, p.amountMinor)
        assertEquals("USD", p.currency)
        assertEquals("EXAMPLE* STREAM SUB", p.merchant)
        assertEquals(8_405L, p.balanceMinor)
        assertEquals("OMR", p.balanceCurrency)
    }

    @Test
    fun `a zero charge checking the card moved nothing and is not asked about`() {
        assertTrue(parser.parse(walletCheck) is SmsParser.Result.Skipped)
    }

    @Test
    fun `the yearly fee is spending on the card`() {
        val p = parsed(fee)
        assertEquals(Direction.EXPENSE, p.direction)
        assertEquals(2_150L, p.amountMinor)
        assertEquals("OMR", p.currency)
        assertEquals("1234", p.accountTail)
        assertTrue(p.tailIsCard)
    }

    @Test
    fun `a top-up is money in, with the balance after it`() {
        val p = parsed(topUp)
        assertEquals(Direction.INCOME, p.direction)
        assertEquals(5_000L, p.amountMinor)
        assertEquals("OMR", p.currency)
        assertEquals(11_255L, p.balanceMinor)
    }
}
