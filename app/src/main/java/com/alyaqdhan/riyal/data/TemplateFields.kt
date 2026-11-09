package com.alyaqdhan.riyal.data

/**
 * Where a kind of message keeps its facts: which of its amounts is the money that moved,
 * which is the balance after it, and where the shop's name sits.
 *
 * The reader decides these by rules - the amount nearest the keyword, a balance behind
 * "bal" or "رصيد", a shop after "at" or "إلى" - and a bank that writes "limit" for its
 * balance, puts the fee first, or names the shop with no preposition is read wrong, every
 * message of the kind alike. Since one kind of message is one template ([Skeleton]), what
 * is true of one message is true of all of them: shown once - by the user, or by the
 * balances - every message written like it is read the same way.
 *
 * Amounts are counted in the order the message quotes them ([SmsParser.amountChoices]),
 * which a template fixes. The shop is found from the word written right before it (the
 * how-manieth time it comes), up to the word written right after it.
 */
data class TemplateFields(
    /** Which of its amounts moved, counted from the start; null leaves it to the reader. */
    val amount: Int? = null,
    /** Which is the balance after it, or [NO_BALANCE]; null leaves it to the reader. */
    val balance: Int? = null,
    /** The word right before the shop's name. */
    val shopAfter: String? = null,
    /** Which time that word comes, from the start: 0 for the first. */
    val shopAfterNth: Int = 0,
    /** The word right after the shop's name, if a word comes next. */
    val shopUntil: String? = null,
    /** The user said nobody is named. */
    val noShop: Boolean = false,
    /** Shown by the user, rather than worked out from the balances. A shown one is never replaced by a worked-out one. */
    val taught: Boolean = false,
    val atMillis: Long = 0L,
) {
    /** Whether it says anything at all. */
    val placesAnything: Boolean get() = amount != null || balance != null || shopAfter != null || noShop

    companion object {
        /** "It quotes no balance": the reader must not take one of its amounts for it. */
        const val NO_BALANCE = -1
    }
}

/**
 * Which of a template's amounts is the amount and which the balance, by arithmetic: in
 * one account's messages written alike, the balance moves from one message to the next by
 * exactly that message's amount. Whichever pair of its amounts does that again and again,
 * and no other pair as often, is the pair - whatever words surround them.
 *
 * What is learned is kept only where it differs from how the reader takes the template
 * already, and never over what the user showed.
 */
object FieldInference {

    /** One message of a template, with its amounts and how the reader took them. */
    data class Sample(
        val key: String,
        val atMillis: Long,
        val amounts: List<SmsParser.AmountChoice>,
        /** Which of [amounts] the reader took as the amount; null when none. */
        val readAmount: Int?,
        /** Which it took as the balance; null when none. */
        val readBalance: Int?,
    )

    /** Times a pair must move the balance by the amount, with no other pair as often. */
    const val LINKS = 2

    fun infer(samples: List<Sample>): Map<String, TemplateFields> {
        val out = HashMap<String, TemplateFields>()
        for ((key, group) in samples.groupBy { it.key }) {
            if (group.size <= LINKS) continue
            // A template is one sender's, so its messages in order are one account's, or a
            // few; a message about another of them between two breaks nothing but that link.
            val ordered = group.sortedBy { it.atMillis }
            val links = HashMap<Pair<Int, Int>, Int>()
            for (k in 1 until ordered.size) {
                val before = ordered[k - 1].amounts
                val now = ordered[k].amounts
                for (i in now.indices) {
                    val amount = now[i].minor ?: continue
                    if (amount <= 0L) continue
                    for (j in now.indices) {
                        if (i == j || j >= before.size) continue
                        val after = now[j]
                        val was = before[j]
                        val balance = after.minor ?: continue
                        val previous = was.minor ?: continue
                        if (after.currency != now[i].currency || was.currency != after.currency) continue
                        val moved = balance - previous
                        if (moved == amount || moved == -amount) links[i to j] = (links[i to j] ?: 0) + 1
                    }
                }
            }
            val best = links.maxByOrNull { it.value } ?: continue
            if (best.value < LINKS || links.count { it.value == best.value } > 1) continue
            val (i, j) = best.key
            // How the reader takes it most of the time: if that is the pair, there is
            // nothing to learn.
            val read = group.groupingBy { it.readAmount to it.readBalance }.eachCount().maxByOrNull { it.value }?.key
            if (read == (i to j)) continue
            out[key] = TemplateFields(amount = i, balance = j)
        }
        return out
    }

    /**
     * Which of [amounts] is [minor] in [currency]: the first, skipping [not] - the
     * amount's own place, when looking for the balance.
     */
    fun indexOf(amounts: List<SmsParser.AmountChoice>, minor: Long?, currency: String?, not: Int? = null): Int? {
        if (minor == null || currency == null) return null
        return amounts.indices.firstOrNull { k -> k != not && amounts[k].minor == minor && amounts[k].currency == currency }
    }
}
