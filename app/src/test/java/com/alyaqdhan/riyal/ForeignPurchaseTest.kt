package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.SmsParser
import com.alyaqdhan.riyal.ui.compose.withCurrencySigns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Purchases made abroad - a Steam game, an app store - arrive with no rial figure in
 * them at all. They used to be left in dollars and so left out of every total.
 */
class ForeignPurchaseTest {

    private val parser = SmsParser(Prefs.DEFAULT_EXPENSE_KEYWORDS, Prefs.DEFAULT_INCOME_KEYWORDS, "OMR")

    private fun parsed(body: String) = parser.parse(body) as SmsParser.Result.Parsed

    @Test
    fun `a purchase quoted only in dollars is read in dollars`() {
        val r = parsed(
            "Purchase of USD 9.99 at STEAMGAMES.COM with card ending 1234 on 12/10/2026. " +
                "Avl Bal OMR 120.500"
        )
        assertEquals("USD", r.currency)
        assertEquals(999L, r.amountMinor)
    }

    @Test
    fun `the balance is read in its own currency, not the purchase's`() {
        val r = parsed(
            "Purchase of USD 9.99 at STEAMGAMES.COM with card ending 1234 on 12/10/2026. " +
                "Avl Bal OMR 120.500"
        )
        // Read as dollars this was 12050.0 cents; it is 120.500 rials.
        assertEquals("OMR", r.balanceCurrency)
        assertEquals(120_500L, r.balanceMinor)
    }

    @Test
    fun `US dollar written US$ is a dollar`() {
        val r = parsed("Purchase of US$ 4.99 at GOOGLE PLAY with card ending 1234")
        assertEquals("USD", r.currency)
        assertEquals(499L, r.amountMinor)
    }

    @Test
    fun `when both figures are given the rial one is what left the account`() {
        // The dollar figure sits nearer the keyword, but the rial one includes the
        // bank's own rate and fees, and it is the one the balance moved by.
        val r = parsed("Purchase of USD 9.99 (OMR 3.912) at STEAMGAMES.COM on 12/10/2026")
        assertEquals("OMR", r.currency)
        assertEquals(3_912L, r.amountMinor)
    }

    @Test
    fun `a rial purchase is untouched`() {
        val r = parsed("Purchase of OMR 2.500 at TALABAT with card ending 9876")
        assertEquals("OMR", r.currency)
        assertEquals(2_500L, r.amountMinor)
    }

    // ── what the screens draw ──

    @Test
    fun `a written amount becomes the figure and the rial sign`() {
        val (text, codes) = withCurrencySigns("Over by ⁦OMR 4.500⁩ this month")
        assertEquals(setOf("OMR"), codes)
        // The code survives only as the sign's alternate text, after the figure.
        val plain = text.text
        assertTrue(plain, plain.contains("4.500"))
        assertTrue(plain, plain.indexOf("4.500") < plain.indexOf("OMR"))
        assertFalse(plain, plain.contains("OMR 4.500"))
    }

    @Test
    fun `a dollar figure keeps its sign in front`() {
        val (text, _) = withCurrencySigns("USD 9.99")
        assertTrue(text.text, text.text.contains("$9.99"))
    }

    @Test
    fun `words that look like codes but are not currencies are left as they are`() {
        val (text, codes) = withCurrencySigns("ATM 1234 at SEEB")
        assertEquals("ATM 1234 at SEEB", text.text)
        assertTrue(codes.isEmpty())
    }
}
