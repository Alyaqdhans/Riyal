package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Currencies
import java.math.BigDecimal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrenciesTest {

    @After
    fun clearOverrides() = Currencies.setOverrides(emptyMap())

    @Test
    fun `a steam game in dollars is counted in rials at the peg`() {
        // 9.99 x 0.3845 = 3.841155, rounded to the baisa.
        assertEquals(3_841L, Currencies.convert(999L, "USD", "OMR"))
    }

    @Test
    fun `the gulf currencies convert through their own dollar pegs`() {
        // 100 SAR = 100 x 0.3845 / 3.75 = 10.2533 OMR
        assertEquals(10_253L, Currencies.convert(10_000L, "SAR", "OMR"))
        // 36.73 AED = 36.73 x 0.3845 / 3.6725 = 3.8455 OMR
        assertEquals(3_846L, Currencies.convert(3_673L, "AED", "OMR"))
    }

    @Test
    fun `rials go the other way too, in the target's own minor unit`() {
        // 1 OMR = 2.6008 USD, two decimals.
        assertEquals(260L, Currencies.convert(1_000L, "OMR", "USD"))
    }

    @Test
    fun `the same currency is not converted at all`() {
        assertEquals(12_345L, Currencies.convert(12_345L, "OMR", "OMR"))
    }

    @Test
    fun `a currency the app does not know is left alone rather than guessed`() {
        assertNull(Currencies.convert(1_000L, "XYZ", "OMR"))
    }

    @Test
    fun `a corrected rate is used for a floating currency`() {
        Currencies.setOverrides(mapOf("EUR" to BigDecimal("0.5")))
        assertEquals(5_000L, Currencies.convert(1_000L, "EUR", "OMR"))
        assertTrue(Currencies.isOverridden("EUR"))
    }

    @Test
    fun `a pegged rate cannot be overridden`() {
        Currencies.setOverrides(mapOf("USD" to BigDecimal("1")))
        assertFalse(Currencies.isOverridden("USD"))
        assertEquals(3_841L, Currencies.convert(999L, "USD", "OMR"))
    }

    @Test
    fun `overrides survive being stored and read back`() {
        val rates = mapOf("EUR" to BigDecimal("0.4471"), "GBP" to BigDecimal("0.512"))
        assertEquals(rates, Currencies.decode(Currencies.encode(rates)))
    }

    @Test
    fun `the rial carries its flag and its new sign`() {
        val omr = Currencies.of("OMR")!!
        assertEquals("🇴🇲", omr.flag)
        assertTrue(omr.symbol != null)
        assertTrue(omr.symbolAfter)
    }
}
