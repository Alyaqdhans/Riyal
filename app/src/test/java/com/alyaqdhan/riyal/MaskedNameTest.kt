package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.ui.compose.unmasked
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bank blanks the middle of a counterparty's name. Rendered whole it fills the row
 * and the identifying end is the part that gets ellipsed away, so it is collapsed for
 * display - and only for display.
 */
class MaskedNameTest {

    @Test
    fun `a masked middle keeps both readable ends`() {
        assertEquals("SAIF…HMED", unmasked("SAIFXXXXXXXXXXHMED"))
        assertEquals("HAMO…", unmasked("HAMOXXXXXXXXXXXXXXXXXXXXXXXXX"))
        assertEquals("ABDU…", unmasked("ABDUXXXXXXXXXXXXXXXXXXXXXXXXX"))
    }

    @Test
    fun `an ordinary name is returned as it is`() {
        assertEquals("Al Fatah Food Com LLC B", unmasked("Al Fatah Food Com LLC B"))
        assertEquals("OMAN OIL BAHLA PO BAHLA", unmasked("OMAN OIL BAHLA PO BAHLA"))
    }

    @Test
    fun `a short run of capitals is not a mask`() {
        // Three is inside the range real names reach, and collapsing it saves nothing.
        assertEquals("MAXX", unmasked("MAXX"))
        assertEquals("EXXON MOBIL", unmasked("EXXON MOBIL"))
    }
}
