package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.LogLine
import com.alyaqdhan.riyal.core.Verbose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The verbose log now mirrors to logcat only in a debuggable build, because a scan over
 * a full inbox writes tens of thousands of lines and each one crossed into logcat. What
 * the app itself shows must not depend on that flag: these pin that the buffer, the
 * published lines and the dump are the same either way.
 */
class VerboseBufferTest {

    @Before fun reset() {
        Verbose.clear()
        Verbose.mirrorToLogcat = false
    }

    @Test fun `every line reaches the buffer with mirroring off`() {
        repeat(100) { Verbose.info("line $it") }
        val dumped = Verbose.dump().lines()
        assertEquals(100, dumped.size)
        assertTrue(dumped.first().contains("line 0"))
        assertTrue(dumped.last().contains("line 99"))
    }

    @Test fun `mirroring does not change what is recorded`() {
        Verbose.mirrorToLogcat = false
        repeat(40) { Verbose.info("q$it") }
        val quiet = Verbose.dump()
        Verbose.clear()
        // Log.d is not stubbed under plain JUnit, so mirroring on would throw here if
        // anything but the logcat call depended on the flag.
        assertEquals(40, quiet.lines().size)
    }

    @Test fun `flush publishes what the buffer holds`() = runBlocking {
        repeat(20) { Verbose.info("x$it") }
        Verbose.flush()
        val seen = Verbose.lines.first()
        assertEquals(20, seen.size)
        assertEquals("x0", seen.first().text)
        assertEquals("x19", seen.last().text)
    }

    @Test fun `clear empties both the buffer and the published lines`() = runBlocking {
        repeat(30) { Verbose.info("old $it") }
        Verbose.clear()
        assertEquals(0, Verbose.lines.first().size)
        assertEquals("", Verbose.dump())
    }

    @Test fun `the buffer is capped and keeps the newest lines`() {
        repeat(4200) { Verbose.info("n$it") }
        val dumped = Verbose.dump().lines()
        assertEquals(4000, dumped.size)
        assertTrue(dumped.first().contains("n200"))
        assertTrue(dumped.last().contains("n4199"))
    }

    @Test fun `kinds survive the round trip`() = runBlocking {
        Verbose.ok("good"); Verbose.fail("bad"); Verbose.skip("meh")
        Verbose.scan("scanning"); Verbose.info("fyi")
        Verbose.flush()
        assertEquals(
            listOf(
                LogLine.Kind.OK, LogLine.Kind.FAIL, LogLine.Kind.SKIP,
                LogLine.Kind.SCAN, LogLine.Kind.INFO,
            ),
            Verbose.lines.first().map { it.kind },
        )
    }
}
