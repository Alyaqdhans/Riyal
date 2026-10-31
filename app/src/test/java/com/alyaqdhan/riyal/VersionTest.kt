package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Updates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which release came second. Getting this wrong either nags about an update that is
 * older than what is installed, or goes silent about a real one forever.
 */
class VersionTest {

    @Test
    fun `a patch after a release is newer`() {
        assertTrue(Updates.isNewer("v1.51", "1.5"))
    }

    @Test
    fun `ten comes after nine, which text and decimals both get wrong`() {
        // As text "1.10" sorts before "1.9". As a decimal 1.10 is less than 1.9. The
        // numbers, compared as numbers, are the only reading that is right.
        assertTrue(Updates.isNewer("v1.10", "1.9"))
        assertFalse(Updates.isNewer("v1.9", "1.10"))
    }

    @Test
    fun `a major step is newer whatever the minor was`() {
        assertTrue(Updates.isNewer("v2.0", "1.51"))
        assertTrue(Updates.isNewer("v1.52", "1.51"))
    }

    @Test
    fun `the same version is not an update`() {
        assertFalse(Updates.isNewer("v1.51", "1.51"))
        assertFalse(Updates.isNewer("1.51", "v1.51"))
        // The debug build carries a suffix and must still recognise its own version.
        assertFalse(Updates.isNewer("v1.51", "1.51-debug"))
    }

    @Test
    fun `going backwards is not an update`() {
        assertFalse(Updates.isNewer("v1.5", "1.51"))
        assertFalse(Updates.isNewer("v1.0", "1.51"))
    }

    @Test
    fun `a tag nobody expected is ignored rather than obeyed`() {
        assertNull(Updates.parse("nightly"))
        assertNull(Updates.parse(""))
        assertNull(Updates.parse("v"))
        // Not an update in either direction, and no exception.
        assertFalse(Updates.isNewer("nightly", "1.51"))
        assertFalse(Updates.isNewer("", "1.51"))
        assertFalse(Updates.isNewer("v1.6", "not-a-version"))
    }

    @Test
    fun `trailing zeroes are the same version, not a later one`() {
        assertFalse(Updates.isNewer("v1.51.0", "1.51"))
        assertFalse(Updates.isNewer("v1.51", "1.51.0"))
        assertTrue(Updates.isNewer("v1.51.1", "1.51"))
    }

    @Test
    fun `parse reads the numbers and drops the rest`() {
        assertEquals(listOf(1, 51), Updates.parse("v1.51"))
        assertEquals(listOf(1, 51), Updates.parse("1.51-debug"))
        assertEquals(listOf(1, 6, 0), Updates.parse("V1.6.0+build7"))
    }

    /**
     * The one case the tags themselves cannot express.
     *
     * 1.51 was published as a hotfix for 1.5, so it reads to a person as "1.5, patch 1"
     * and 1.6 looks like the release after it. To the numbers it is minor 51, and 6 is
     * a long way before that. There is no comparison that can have both: the same
     * digits must mean "fifty-one" for 1.10 to come after 1.9, and "point five one" for
     * 1.6 to come after 1.51.
     *
     * So this is asserted rather than fixed, to say out loud which way it went. The next
     * tag has to be above 1.51 as a number - v1.52, or v1.6.0 with a third place - or
     * nobody on 1.51 will ever be told about it.
     */
    @Test
    fun `1_6 does not read as newer than 1_51, and the tag is what must change`() {
        assertFalse(Updates.isNewer("v1.6", "1.51"))
        assertTrue(Updates.isNewer("v1.52", "1.51"))
        assertTrue(Updates.isNewer("v1.6.0", "1.5.1"))
    }
}
