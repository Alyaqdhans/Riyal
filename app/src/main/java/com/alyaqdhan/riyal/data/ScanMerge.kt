package com.alyaqdhan.riyal.data

/**
 * Folding one pass's findings into what is already stored.
 *
 * A full pass is the truth about everything and simply replaces. An incremental pass is
 * the truth only about the messages it actually read, and this is where that
 * distinction is kept honest: what it read is replaced by what it found, what it never
 * looked at is left alone, and a message it read that yields nothing this time loses the
 * record it had.
 *
 * Pure, and tested, because the last defect in this area put one purchase into the
 * totals twice. One message must produce exactly one record.
 */
object ScanMerge {

    /**
     * @param previous every record currently stored, including manual ones.
     * @param scanned what this pass produced, already filtered and with user edits applied.
     * @param examinedIds the messages this pass read, or null when it read everything.
     * @param manuals manual records to carry through, already stripped of any the pass
     *   superseded. These are keyed by their own id and never by a message.
     */
    fun records(
        previous: List<Txn>,
        scanned: List<Txn>,
        examinedIds: Set<String>?,
        manuals: List<Txn>,
    ): List<Txn> {
        val untouched = when (examinedIds) {
            // A pass over the whole range says everything it did not find is gone, which
            // is the only way a record for a deleted message goes away.
            null -> emptyList()
            else -> previous.filter { !it.manual && it.id !in examinedIds }
        }
        // Order matters: what this pass found wins over an older copy of the same
        // record. distinctBy is a guard rather than a mechanism - a record's id is the
        // message it came from, so these lists are disjoint by construction - but a
        // duplicate here is a purchase counted twice, and that is not worth trusting to
        // construction alone.
        return (scanned + untouched + manuals).distinctBy { it.id }
    }

    /** The same rule for anything else keyed by the message it came from. */
    fun <T> keepUnexamined(previous: List<T>, examinedIds: Set<String>?, id: (T) -> String): List<T> =
        when (examinedIds) {
            null -> emptyList()
            else -> previous.filter { id(it) !in examinedIds }
        }
}
