package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Numerals
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumeralsTest {

    @Test
    fun `arabic indic digits become latin`() {
        assertEquals("12500", Numerals.latin("١٢٥٠٠"))
        assertEquals("12500", Numerals.latin("۱۲۵۰۰"))
    }

    @Test
    fun `arabic separators become latin`() {
        assertEquals("1,234.500", Numerals.latin("1٬234٫500"))
    }

    @Test
    fun `an amount typed on an arabic keyboard parses`() {
        assertEquals(BigDecimal("12.500"), Numerals.decimal("١٢٫٥٠٠"))
        assertEquals(BigDecimal("1234.500"), Numerals.decimal("١٬٢٣٤٫٥٠٠"))
    }

    @Test
    fun `a latin amount still parses`() {
        assertEquals(BigDecimal("12.500"), Numerals.decimal(" 12.500 "))
        assertEquals(BigDecimal("1234.500"), Numerals.decimal("1,234.500"))
    }

    @Test
    fun `words are still not a number`() {
        assertNull(Numerals.decimal("خمسة"))
        assertNull(Numerals.decimal(""))
    }
}
