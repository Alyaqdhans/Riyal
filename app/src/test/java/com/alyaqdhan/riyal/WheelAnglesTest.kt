package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.ui.compose.wheelAngles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelAnglesTest {

    private fun assertFullCircle(angles: List<Float>) =
        assertEquals(360f, angles.sum(), 0.05f)

    @Test
    fun `shares large enough to draw are left as they are`() {
        val angles = wheelAngles(listOf(0.5f, 0.3f, 0.2f), minDegrees = 12f)
        assertEquals(180f, angles[0], 0.01f)
        assertEquals(108f, angles[1], 0.01f)
        assertEquals(72f, angles[2], 0.01f)
    }

    @Test
    fun `a tiny category still gets an arc of its own`() {
        // 0.5% of a month is under two degrees, narrower than the round end of a stroke.
        val angles = wheelAngles(listOf(0.7f, 0.295f, 0.005f), minDegrees = 12f)
        assertEquals(12f, angles[2], 0.01f)
        assertFullCircle(angles)
        // The big slices pay for it in proportion, so their order is unchanged.
        assertTrue(angles[0] > angles[1])
    }

    @Test
    fun `several small categories never overlap`() {
        val fractions = listOf(0.80f) + List(10) { 0.02f }
        val angles = wheelAngles(fractions, minDegrees = 12f)
        assertFullCircle(angles)
        assertTrue(angles.all { it >= 12f - 0.01f })
    }

    @Test
    fun `too many categories to fit share the circle evenly`() {
        val angles = wheelAngles(List(40) { 0.025f }, minDegrees = 12f)
        assertTrue(angles.all { kotlin.math.abs(it - 9f) < 0.01f })
    }

    @Test
    fun `nothing to draw is nothing`() {
        assertEquals(emptyList<Float>(), wheelAngles(emptyList(), minDegrees = 12f))
    }
}
