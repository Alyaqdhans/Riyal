package com.alyaqdhan.riyal.data

import kotlin.math.abs

/**
 * Finds internal transfers: money you moved between your own accounts, which the inbox
 * reports as two unrelated messages - a withdrawal from one bank and a deposit into
 * another. Left alone they inflate both your spending and your income by the same
 * amount, so every total on Home and Analysis is wrong in both directions at once.
 *
 * The rule is deliberately strict, because a false pair silently deletes a real expense
 * and a real income from the user's numbers: same amount, same currency, two *different*
 * accounts, opposite directions, close in time. Nothing is ever applied automatically -
 * [propose] only nominates, and the user answers in Review.
 */
object TransferMatcher {

    /** Two legs of a transfer normally land within minutes of each other. */
    const val DEFAULT_WINDOW_MILLIS = 15L * 60_000L

    /**
     * When the wording itself says "transfer", a slower settlement is believable, so the
     * pair is allowed to sit further apart before we stop considering it.
     */
    const val HINTED_WINDOW_MILLIS = 24L * 60L * 60_000L

    /**
     * Nominates pairs from [txns]. [hintedIds] are transactions whose message mentioned
     * transferring (see [SmsParser.Result.Parsed.transferHint]); a pair where either leg
     * is hinted gets the longer window.
     *
     * Each transaction is used at most once, closest pair first, so three same-sized
     * amounts in one afternoon can't fan out into a web of overlapping proposals.
     */
    fun propose(
        txns: List<Txn>,
        hintedIds: Set<String> = emptySet(),
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
        hintedWindowMillis: Long = HINTED_WINDOW_MILLIS,
    ): List<TransferProposal> {
        val outs = txns.filter { it.type == TxnType.EXPENSE }
        val ins = txns.filter { it.type == TxnType.INCOME }
        if (outs.isEmpty() || ins.isEmpty()) return emptyList()

        // Every plausible pairing, then take them greedily by how close the two legs are.
        val candidates = ArrayList<Triple<Long, Txn, Txn>>()
        for (o in outs) {
            for (i in ins) {
                if (!pairable(o, i)) continue
                val gap = abs(o.atMillis - i.atMillis)
                val limit = if (o.id in hintedIds || i.id in hintedIds) {
                    maxOf(windowMillis, hintedWindowMillis)
                } else {
                    windowMillis
                }
                if (gap > limit) continue
                candidates += Triple(gap, o, i)
            }
        }
        candidates.sortWith(compareBy({ it.first }, { it.second.id }, { it.third.id }))

        val used = HashSet<String>()
        val out = ArrayList<TransferProposal>()
        for ((_, expense, income) in candidates) {
            if (expense.id in used || income.id in used) continue
            used += expense.id
            used += income.id
            out += TransferProposal(
                id = TransferProposal.idFor(expense.id, income.id),
                outTxnId = expense.id,
                inTxnId = income.id,
                fromAccountId = expense.fromAccountId,
                toAccountId = income.toAccountId,
                amountMinor = expense.amountMinor,
                currency = expense.currency,
                atMillis = minOf(expense.atMillis, income.atMillis),
            )
        }
        return out.sortedByDescending { it.atMillis }
    }

    private fun pairable(expense: Txn, income: Txn): Boolean {
        if (expense.amountMinor != income.amountMinor) return false
        if (expense.currency != income.currency) return false
        val from = expense.fromAccountId
        val to = income.toAccountId
        // Money cannot move from an account to itself. Two unassigned legs are also
        // refused: with no accounts to move between there is nothing to net out, and
        // pairing them would just erase a real expense and a real income.
        if (from == null && to == null) return false
        if (from != null && from == to) return false
        return true
    }

    /**
     * Collapses an accepted proposal's two legs into the single record that replaces
     * them. Both source ids are kept in [Txn.legIds] so a rescan can recognise the same
     * pair and re-apply the decision without asking again.
     */
    fun merge(proposal: TransferProposal, expense: Txn, income: Txn): Txn = Txn(
        id = "trf-" + proposal.id.take(33),
        atMillis = minOf(expense.atMillis, income.atMillis),
        amountMinor = proposal.amountMinor,
        currency = proposal.currency,
        type = TxnType.TRANSFER,
        fromAccountId = expense.fromAccountId,
        toAccountId = income.toAccountId,
        merchant = null,
        sender = expense.sender,
        body = expense.body,
        categoryId = Categories.TRANSFER_ID,
        categorySource = "user",
        confidence = 100,
        manual = expense.manual && income.manual,
        legIds = listOf(expense.id, income.id),
    )
}
