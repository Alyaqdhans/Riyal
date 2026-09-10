package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.ScanMerge
import com.alyaqdhan.riyal.data.ScanWindow
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scanning reads what is new rather than the whole inbox, and keeps the records it made
 * last time instead of deriving them again. Both halves of that are here.
 *
 * The failure this guards against is not slowness. It is a message that is silently
 * never read, or one that is read twice: the first is a transaction that does not exist,
 * the second is a purchase counted twice, and neither looks wrong on any screen.
 */
class IncrementalScanTest {

    private val overlap = Prefs.SCAN_OVERLAP_MILLIS
    private val day = 24L * 60L * 60_000L
    private val now = 1_760_000_000_000L
    private val fp = "v1|debited|credited"

    private fun plan(
        full: Boolean = false,
        since: Long = 0L,
        highWater: Long = now,
        stored: String = fp,
        fingerprint: String = fp,
    ) = ScanWindow.plan(full, since, highWater, stored, fingerprint, overlap)

    // ───────────────────────── which messages to read ─────────────────────────

    @Test
    fun `the usual pass reads from before the newest message it saw last time`() {
        val p = plan()
        assertFalse(p.readEverything)
        assertEquals(now - overlap, p.fromMillis)
        assertEquals(ScanWindow.Reason.ONLY_WHAT_IS_NEW, p.reason)
    }

    @Test
    fun `a message dated before the mark is still read, rather than stepped over`() {
        // The whole reason the window starts early. A message can arrive dated hours or
        // days before one already read - a delayed delivery, a clock change, a restored
        // backup - and a strict "newer than the mark" query would never look at it
        // again. Nothing about that is visible afterwards: the transaction simply is
        // not there.
        val p = plan()
        val backdated = now - day
        assertTrue(
            "a message dated $day ms before the mark must still fall inside the window",
            backdated >= p.fromMillis,
        )
    }

    @Test
    fun `the overlap is wider than the matcher looks, so transfer pairs stay together`() {
        // A transfer is two messages. If the second is new and the first is not re-read
        // with it, the pair is never nominated and one movement of money is counted as
        // both spending and income.
        assertTrue(
            "overlap ($overlap) must exceed the matcher's widest window",
            overlap > com.alyaqdhan.riyal.data.TransferMatcher.HINTED_WINDOW_MILLIS,
        )
    }

    @Test
    fun `nothing is read from before the range the user chose`() {
        // "How far back" and a fresh start are floors. Reaching past them to satisfy the
        // overlap would read messages the user asked not to have read.
        val floor = now - day
        val p = plan(since = floor)
        assertEquals(floor, p.fromMillis)
        assertFalse(p.readEverything)
    }

    @Test
    fun `a keyword change forces one full re-read, and only one`() {
        val changed = plan(stored = "v1|debited", fingerprint = "v1|debited,used for")
        assertTrue(changed.readEverything)
        assertEquals(ScanWindow.Reason.RULES_CHANGED, changed.reason)
        // Once the new fingerprint is stored, the pass after it is incremental again.
        val next = plan(stored = "v1|debited,used for", fingerprint = "v1|debited,used for")
        assertFalse(next.readEverything)
    }

    @Test
    fun `a parser version bump forces a full re-read`() {
        // Stored records were produced by the parser that was current when they were
        // made. A fix that changes what a message parses to has to reach them.
        val p = plan(stored = "v1|debited", fingerprint = "v2|debited")
        assertTrue(p.readEverything)
        assertEquals(ScanWindow.Reason.RULES_CHANGED, p.reason)
    }

    @Test
    fun `the first pass ever reads everything in range`() {
        val p = plan(highWater = 0L, since = 5_000L)
        assertTrue(p.readEverything)
        assertEquals(5_000L, p.fromMillis)
        assertEquals(ScanWindow.Reason.NOTHING_READ_YET, p.reason)
    }

    @Test
    fun `asking for a full rescan reads everything whatever else is true`() {
        val p = plan(full = true)
        assertTrue(p.readEverything)
        assertEquals(ScanWindow.Reason.ASKED, p.reason)
    }

    // ───────────────────────── what to do with what it found ─────────────────────────

    private fun txn(id: String, amount: Long, manual: Boolean = false) = Txn(
        id = id,
        atMillis = now,
        amountMinor = amount,
        currency = "OMR",
        type = TxnType.EXPENSE,
        fromAccountId = "acc-1",
        toAccountId = null,
        merchant = "SHOP $id",
        sender = "BankMuscat",
        body = "body $id",
        categoryId = "other",
        categorySource = "auto",
        confidence = 90,
        manual = manual,
    )

    @Test
    fun `a pass that read nothing new changes nothing`() {
        val stored = listOf(txn("a", 100), txn("b", 200))
        val merged = ScanMerge.records(
            previous = stored,
            scanned = emptyList(),
            examinedIds = emptySet(),
            manuals = emptyList(),
        )
        assertEquals(stored.map { it.id }.toSet(), merged.map { it.id }.toSet())
        assertEquals(300L, merged.sumOf { it.amountMinor })
    }

    @Test
    fun `a new message is added without disturbing the rest`() {
        val stored = listOf(txn("a", 100), txn("b", 200))
        val merged = ScanMerge.records(
            previous = stored,
            scanned = listOf(txn("c", 50)),
            examinedIds = setOf("c"),
            manuals = emptyList(),
        )
        assertEquals(setOf("a", "b", "c"), merged.map { it.id }.toSet())
        assertEquals(350L, merged.sumOf { it.amountMinor })
    }

    @Test
    fun `re-reading a message replaces its record instead of adding a second`() {
        // Every incremental pass re-reads the overlap window, so a message just recorded
        // is read again on the very next scan. This is the case that must never grow the
        // totals.
        val stored = listOf(txn("a", 100), txn("b", 200))
        var merged = stored
        repeat(3) {
            merged = ScanMerge.records(
                previous = merged,
                scanned = listOf(txn("b", 200)),
                examinedIds = setOf("b"),
                manuals = emptyList(),
            )
        }
        assertEquals(2, merged.size)
        assertEquals(merged.map { it.id }, merged.map { it.id }.distinct())
        assertEquals(300L, merged.sumOf { it.amountMinor })
    }

    @Test
    fun `a message that stops producing a record loses the one it had`() {
        // Inside the window the pass is the whole truth. A message that no longer parses
        // to anything - a sender rule changed, its kind was dismissed - must not leave
        // its old record behind.
        val stored = listOf(txn("a", 100), txn("b", 200))
        val merged = ScanMerge.records(
            previous = stored,
            scanned = emptyList(),
            examinedIds = setOf("b"),
            manuals = emptyList(),
        )
        assertEquals(listOf("a"), merged.map { it.id })
    }

    @Test
    fun `a message outside the window keeps its record even when nothing re-read it`() {
        val stored = listOf(txn("old", 999))
        val merged = ScanMerge.records(
            previous = stored,
            scanned = listOf(txn("new", 1)),
            examinedIds = setOf("new"),
            manuals = emptyList(),
        )
        assertEquals(setOf("old", "new"), merged.map { it.id }.toSet())
    }

    @Test
    fun `a full pass is the truth about everything, including what is gone`() {
        // This is the only thing that notices a bank message deleted from the inbox, and
        // it is why "Rescan everything" exists as its own control.
        val stored = listOf(txn("a", 100), txn("deleted-from-inbox", 200))
        val merged = ScanMerge.records(
            previous = stored,
            scanned = listOf(txn("a", 100)),
            examinedIds = null,
            manuals = emptyList(),
        )
        assertEquals(listOf("a"), merged.map { it.id })
    }

    @Test
    fun `hand-made records survive a pass that never looked at them`() {
        val stored = listOf(txn("a", 100), txn("typed-by-hand", 500, manual = true))
        val merged = ScanMerge.records(
            previous = stored,
            scanned = emptyList(),
            examinedIds = emptySet(),
            manuals = listOf(txn("typed-by-hand", 500, manual = true)),
        )
        assertEquals(setOf("a", "typed-by-hand"), merged.map { it.id }.toSet())
        assertEquals(1, merged.count { it.id == "typed-by-hand" })
    }

    @Test
    fun `anything keyed by its message follows the same rule`() {
        val kept = ScanMerge.keepUnexamined(listOf("a", "b", "c"), setOf("b")) { it }
        assertEquals(listOf("a", "c"), kept)
        assertEquals(emptyList<String>(), ScanMerge.keepUnexamined(listOf("a"), null) { it })
    }
}
