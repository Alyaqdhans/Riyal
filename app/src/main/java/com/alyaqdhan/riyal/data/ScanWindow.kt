package com.alyaqdhan.riyal.data

/**
 * How much of the inbox one pass has to read.
 *
 * Scanning used to re-read everything every time, which is why a record could exist
 * only as the output of the most recent pass. Records are kept between passes now, so
 * a pass reads what arrived since the last one - and the whole question is when that is
 * not safe. Pure and separate from [ScanEngine] because the answer is a handful of
 * rules, and getting one of them wrong means either a slow app or a missing
 * transaction.
 */
object ScanWindow {

    data class Plan(
        /** Read the whole range: this pass is the truth about every message in it. */
        val readEverything: Boolean,
        /** The earliest message date to ask the provider for. */
        val fromMillis: Long,
        /** Why, in words, for the log. */
        val reason: Reason,
    )

    enum class Reason {
        /** The user asked for it in Settings. */
        ASKED,
        /** The keywords, sender rules or parser changed, so stored records are stale. */
        RULES_CHANGED,
        /** Nothing has ever been read, so there is no mark to start from. */
        NOTHING_READ_YET,
        /** The usual case: only what arrived since last time. */
        ONLY_WHAT_IS_NEW,
    }

    /**
     * @param full the user asked to re-read everything.
     * @param sinceMillis the floor from the "How far back" setting and any fresh start.
     *   Never read before this, whatever else is true.
     * @param highWaterMillis the date of the newest message the last pass read, or 0.
     * @param storedFingerprint what the last pass was reading with.
     * @param fingerprint what this pass would read with.
     * @param overlapMillis how far before the high-water mark to start anyway.
     */
    fun plan(
        full: Boolean,
        sinceMillis: Long,
        highWaterMillis: Long,
        storedFingerprint: String,
        fingerprint: String,
        overlapMillis: Long,
    ): Plan {
        val reason = when {
            full -> Reason.ASKED
            storedFingerprint != fingerprint -> Reason.RULES_CHANGED
            highWaterMillis <= 0L -> Reason.NOTHING_READ_YET
            else -> Reason.ONLY_WHAT_IS_NEW
        }
        if (reason != Reason.ONLY_WHAT_IS_NEW) {
            return Plan(readEverything = true, fromMillis = sinceMillis, reason = reason)
        }
        // Starting exactly at the high-water mark would be wrong. A message can be dated
        // before one already read - delayed delivery, a clock change, a restored backup -
        // and a strict "newer than" would step over it and never look again. Nothing
        // about that failure is visible: the transaction simply never exists.
        //
        // The floor still wins. Reaching back past the range the user chose would start
        // reading messages they asked not to have read.
        val start = maxOf(sinceMillis, highWaterMillis - overlapMillis)
        return Plan(readEverything = false, fromMillis = start, reason = reason)
    }
}
