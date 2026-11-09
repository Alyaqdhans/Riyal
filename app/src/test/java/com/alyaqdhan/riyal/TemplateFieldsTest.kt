package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.FieldInference
import com.alyaqdhan.riyal.data.SmsParser
import com.alyaqdhan.riyal.data.TemplateFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a kind of message keeps its facts, shown by the user or worked out from its
 * balances, and read into every message of the kind. Made-up messages and figures.
 */
class TemplateFieldsTest {

    private val parser = SmsParser(Prefs.DEFAULT_EXPENSE_KEYWORDS, Prefs.DEFAULT_INCOME_KEYWORDS, "OMR")

    private fun parsed(r: SmsParser.Result?) = r as SmsParser.Result.Parsed

    /** A card that calls its balance a limit, which the reader does not take for one. */
    private fun limit(amount: String, left: String) =
        "Purchase of OMR $amount at EXAMPLE CAFE. Available limit OMR $left"

    @Test
    fun `the balance a kind keeps where the rules do not look`() {
        val plain = parsed(parser.parse(limit("5.000", "95.000")))
        assertEquals(5_000L, plain.amountMinor)
        assertNull(plain.balanceMinor)

        val placed = parsed(parser.parse(limit("5.000", "95.000"), TemplateFields(balance = 1)))
        assertEquals(5_000L, placed.amountMinor)
        assertEquals(95_000L, placed.balanceMinor)
    }

    @Test
    fun `an amount the rules took for a balance`() {
        // Both figures sit behind "balance", so the reader finds no amount at all.
        val body = "Your balance changed: OMR 5.000 spent. Balance OMR 95.000"
        assertTrue(parser.parse(body) is SmsParser.Result.Skipped)

        val fields = TemplateFields(amount = 0, balance = 1)
        val asked = parser.parse(body, fields) as SmsParser.Result.NeedsReview
        assertEquals(5_000L, asked.amountMinor)
        val read = parser.parseAs(body, Direction.EXPENSE, fields)!!
        assertEquals(5_000L, read.amountMinor)
        assertEquals(95_000L, read.balanceMinor)
    }

    @Test
    fun `no balance where the kind quotes none`() {
        val body = "Paid OMR 5.000 to EXAMPLE CAFE. Bal reward points OMR 1.000"
        assertNotNull(parsed(parser.parse(body)).balanceMinor)
        assertNull(parsed(parser.parse(body, TemplateFields(balance = TemplateFields.NO_BALANCE))).balanceMinor)
    }

    @Test
    fun `the amounts are offered in the order the message quotes them`() {
        val choices = parser.amountChoices(limit("5.000", "95.000"))
        assertEquals(listOf(5_000L, 95_000L), choices.map { it.minor })
        assertEquals(listOf("OMR", "OMR"), choices.map { it.currency })
    }

    @Test
    fun `a shop shown once is read the same way in the next message of the kind`() {
        fun alert(shop: String, amount: String) =
            "عزيزي المستخدم، تم استخدام بطاقتك رقم XX1234 لدفع مبلغ $amount OMR إلى $shop بتاريخ 03/02/2026."
        val first = alert("EXAMPLE CAFE AND TRADIN", "4.500")
        assertTrue("EXAMPLE CAFE AND TRADIN" in parser.shopChoices(first))
        val fields = parser.placeShop(first, "EXAMPLE CAFE AND TRADIN", TemplateFields())!!
        assertEquals("إلى", fields.shopAfter)
        assertEquals("بتاريخ", fields.shopUntil)

        val next = parsed(parser.parse(alert("EXAMPLE GROCERY", "2.250"), fields))
        assertEquals("EXAMPLE GROCERY", next.merchant)
    }

    @Test
    fun `a shop named with no word in front, in English`() {
        fun alert(shop: String, amount: String) = "Your card was used for OMR $amount $shop on 03/02/2026"
        val first = alert("EXAMPLE STORE", "5.000")
        assertTrue("EXAMPLE STORE" in parser.shopChoices(first))
        val fields = parser.placeShop(first, "EXAMPLE STORE", TemplateFields())!!
        val next = parser.parseAs(alert("EXAMPLE SHOP", "7.250"), Direction.EXPENSE, fields)!!
        assertEquals("EXAMPLE SHOP", next.merchant)
        assertEquals(7_250L, next.amountMinor)
    }

    @Test
    fun `nobody named, when the user says so`() {
        val body = "Paid OMR 5.000 to EXAMPLE CAFE"
        assertEquals("EXAMPLE CAFE", parsed(parser.parse(body)).merchant)
        assertNull(parsed(parser.parse(body, TemplateFields(noShop = true))).merchant)
    }

    private fun sample(at: Long, body: String): FieldInference.Sample {
        val amounts = parser.amountChoices(body)
        val read = parsed(parser.parse(body))
        val amountAt = FieldInference.indexOf(amounts, read.amountMinor, read.currency)
        return FieldInference.Sample(
            key = "examplecard|purchase of ¤ at @ available limit ¤",
            atMillis = at,
            amounts = amounts,
            readAmount = amountAt,
            readBalance = FieldInference.indexOf(amounts, read.balanceMinor, read.balanceCurrency, not = amountAt),
        )
    }

    @Test
    fun `the balances show which amount moved and which is the balance after it`() {
        val samples = listOf(
            sample(1, limit("5.000", "95.000")),
            sample(2, limit("2.000", "93.000")),
            sample(3, limit("3.000", "90.000")),
        )
        val learned = FieldInference.infer(samples)
        val fields = learned.getValue("examplecard|purchase of ¤ at @ available limit ¤")
        assertEquals(0, fields.amount)
        assertEquals(1, fields.balance)
    }

    @Test
    fun `nothing is learned where the reader already reads it so`() {
        fun bal(amount: String, left: String) = "Purchase of OMR $amount at EXAMPLE CAFE. Avl Bal OMR $left"
        val samples = listOf(sample(1, bal("5.000", "95.000")), sample(2, bal("2.000", "93.000")), sample(3, bal("3.000", "90.000")))
        assertTrue(FieldInference.infer(samples).isEmpty())
    }

    @Test
    fun `one coincidence teaches nothing`() {
        val samples = listOf(
            sample(1, limit("5.000", "95.000")),
            sample(2, limit("2.000", "93.000")),
            // A fee nobody texted about came between: the chain breaks here.
            sample(3, limit("3.000", "89.500")),
        )
        assertTrue(FieldInference.infer(samples).isEmpty())
    }
}
