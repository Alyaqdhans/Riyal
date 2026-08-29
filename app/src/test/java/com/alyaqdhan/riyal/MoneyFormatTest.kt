package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Money
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Money.format holds one DecimalFormat per pattern instead of building one per call.
 * DecimalFormat carries mutable state, so these pin both the exact strings and the
 * fact that sharing the instance cannot corrupt them.
 */
class MoneyFormatTest {

    @Test fun `three decimal currencies keep three places`() {
        assertEquals("OMR 4.500", Money.format(4500L, "OMR"))
        assertEquals("OMR 0.395", Money.format(395L, "OMR"))
        assertEquals("BHD 1.000", Money.format(1000L, "BHD"))
        assertEquals("KWD 12.345", Money.format(12345L, "KWD"))
    }

    @Test fun `two decimal currencies keep two places`() {
        assertEquals("USD 14.99", Money.format(1499L, "USD"))
        assertEquals("SAR 35.00", Money.format(3500L, "SAR"))
        assertEquals("AED 0.05", Money.format(5L, "AED"))
    }

    @Test fun `thousands separators survive the shared formatter`() {
        assertEquals("OMR 1,234.567", Money.format(1234567L, "OMR"))
        assertEquals("USD 1,000,000.00", Money.format(100000000L, "USD"))
    }

    @Test fun `formatAmount drops the code but not the digits`() {
        assertEquals("4.500", Money.formatAmount(4500L, "OMR"))
        assertEquals("14.99", Money.formatAmount(1499L, "USD"))
        assertEquals("1,234.567", Money.formatAmount(1234567L, "OMR"))
    }

    @Test fun `signed amounts keep their sign and spacing`() {
        assertEquals("− OMR 4.500", Money.formatSigned(4500L, "OMR", expense = true))
        assertEquals("+ OMR 4.500", Money.formatSigned(4500L, "OMR", expense = false))
    }

    @Test fun `zero and negative minor units format cleanly`() {
        assertEquals("OMR 0.000", Money.format(0L, "OMR"))
        assertEquals("USD 0.00", Money.format(0L, "USD"))
        assertEquals("OMR -4.500", Money.format(-4500L, "OMR"))
    }

    /**
     * Interleaving both patterns across threads: a shared DecimalFormat without a lock
     * would return digits from another thread's number here.
     */
    @Test fun `concurrent formatting never mixes results`() {
        val pool = Executors.newFixedThreadPool(8)
        val jobs = (0 until 8).map { worker ->
            Callable {
                repeat(2000) { i ->
                    val n = (worker * 2000 + i).toLong()
                    assertEquals("OMR " + threeDp(n), Money.format(n, "OMR"))
                    assertEquals("USD " + twoDp(n), Money.format(n, "USD"))
                }
                true
            }
        }
        val results = pool.invokeAll(jobs).map { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()
        assertTrue(results.all { it })
    }

    private fun threeDp(minor: Long): String {
        val s = (minor / 1000).toString().reversed().chunked(3).joinToString(",").reversed()
        return s + "." + (minor % 1000).toString().padStart(3, '0')
    }

    private fun twoDp(minor: Long): String {
        val s = (minor / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return s + "." + (minor % 100).toString().padStart(2, '0')
    }
}
