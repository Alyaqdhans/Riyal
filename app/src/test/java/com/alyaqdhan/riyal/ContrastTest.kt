package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.ui.theme.GRAPHIC_CONTRAST
import com.alyaqdhan.riyal.ui.theme.TEXT_CONTRAST
import com.alyaqdhan.riyal.ui.theme.badgeColours
import com.alyaqdhan.riyal.ui.theme.contrast
import com.alyaqdhan.riyal.ui.theme.inkOn
import com.alyaqdhan.riyal.ui.theme.legibleOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    // The fallback schemes' cards (surfaceContainerHighest), light and dark.
    private val lightCard = 0xFFE9E1D4.toInt()
    private val darkCard = 0xFF38342B.toInt()

    private val everyColour = Categories.BUILTIN.map { Categories.colorFor(it.id) } + Categories.PALETTE

    @Test
    fun `the WCAG ratio of black on white is 21 and of a colour on itself 1`() {
        assertEquals(21.0, contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, contrast(lightCard, lightCard), 0.0001)
    }

    @Test
    fun `every category colour stands out on the cards of both themes`() {
        for (card in listOf(lightCard, darkCard)) {
            for (colour in everyColour) {
                val shown = legibleOn(colour, card, GRAPHIC_CONTRAST)
                assertTrue(
                    "%08X on %08X: %.2f".format(shown, card, contrast(shown, card)),
                    contrast(shown, card) >= GRAPHIC_CONTRAST,
                )
                // The icon on its own badge, a tinted circle on the card, still reads,
                // and the circle is still a tint of the card rather than a blot.
                val (circle, icon) = badgeColours(colour, card)
                assertTrue(
                    "%08X on its badge %08X: %.2f".format(icon, circle, contrast(icon, circle)),
                    contrast(icon, circle) >= 3.0,
                )
                assertTrue(contrast(circle, card) < 1.6)
            }
        }
    }

    @Test
    fun `a colour that already stands out is left alone`() {
        val deep = 0xFF455A64.toInt()
        assertEquals(deep, legibleOn(deep, lightCard, GRAPHIC_CONTRAST))
    }

    @Test
    fun `the shift keeps the hue - amber stays amber, only darker`() {
        val amber = 0xFFFFB300.toInt()
        val shown = legibleOn(amber, lightCard, GRAPHIC_CONTRAST)
        val r = (shown shr 16) and 0xFF
        val g = (shown shr 8) and 0xFF
        val b = shown and 0xFF
        assertTrue("still more red than green, more green than blue: %08X".format(shown), r > g && g > b)
        assertTrue(contrast(shown, lightCard) < GRAPHIC_CONTRAST + 1.0)
    }

    @Test
    fun `money green is text-legible on light and dark cards`() {
        assertTrue(contrast(legibleOn(0xFF2E7D32.toInt(), lightCard, TEXT_CONTRAST), lightCard) >= TEXT_CONTRAST)
        assertTrue(contrast(legibleOn(0xFF81C784.toInt(), darkCard, TEXT_CONTRAST), darkCard) >= TEXT_CONTRAST)
    }

    @Test
    fun `a tick is drawn in whichever of black and white reads better on its fill`() {
        assertEquals(0xFF000000.toInt(), inkOn(0xFFFFE082.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), inkOn(0xFF455A64.toInt()))
    }
}
