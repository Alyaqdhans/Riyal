package com.alyaqdhan.riyal.data

/**
 * What a sender the gate leaves out has shown of holding money, across scans.
 *
 * The gate reads only senders named like a bank, ones that hold an account, and ones the
 * user added, because a telecom quotes an amount in every advert. A wallet or a card with
 * a name of its own ("Example Pay") is none of those, so before this nothing it sent was
 * ever read - not recorded, not proven by its balance, not asked about.
 */
data class SenderEvidence(
    val sender: String,
    /** Messages it sent, of the ones scanned. */
    val messages: Int,
    /** Messages quoting an amount and a balance. */
    val withBalance: Int,
    /** Messages whose balance moved by exactly their amount from an earlier one's. */
    val proofs: Int,
    /** The newest message counted, so a later short scan adds only what is newer. */
    val newestAt: Long,
    /** The last balance it quoted, so a later scan can go on proving from it. */
    val lastBalanceMinor: Long? = null,
    val lastBalanceCurrency: String? = null,
    val lastAt: Long = 0L,
) {
    /**
     * Whether it is worth asking about: its balance moved by exactly the amount at least
     * twice - which a bank's or a wallet's does and an advert's cannot - or enough of what
     * it sends quotes an amount and a balance together.
     */
    val looksLikeMoney: Boolean
        get() = proofs >= MoneySenders.PROOFS ||
            (withBalance >= MoneySenders.BALANCES && withBalance * MoneySenders.SHARE >= messages)
}

/**
 * Finds the senders worth asking about among those the gate leaves out: by what they send,
 * not by their name. Nothing from them is recorded: the user is asked, and only once they
 * say "read them" does the scan treat the sender as a bank.
 */
object MoneySenders {

    /** Balances moving by exactly the amount, this many times, make a sender worth asking. */
    const val PROOFS = 2

    /** Or this many messages quoting an amount and a balance... */
    const val BALANCES = 3

    /** ...that are at least one in this many of all it sends. */
    const val SHARE = 3

    /** One message from a sender left out, as far as it was read. */
    data class Seen(
        val id: String,
        val atMillis: Long,
        val amountMinor: Long? = null,
        val currency: String? = null,
        val balanceMinor: Long? = null,
        val balanceCurrency: String? = null,
    )

    /**
     * A message read only for its amount and balance. The message's text is not kept,
     * and most messages from a telecom are passed over without being parsed at all: one
     * with no amount in a currency cannot show anything here.
     */
    fun seen(id: String, atMillis: Long, body: String, parser: SmsParser): Seen {
        if (!mightBeMoney(body)) return Seen(id, atMillis)
        val facts = when (val read = parser.parse(body)) {
            is SmsParser.Result.Parsed -> read
            is SmsParser.Result.NeedsReview ->
                if (read.amountMinor != null) parser.parseAs(body, Direction.EXPENSE) else null
            is SmsParser.Result.Skipped -> null
        } ?: return Seen(id, atMillis)
        return Seen(
            id = id,
            atMillis = atMillis,
            amountMinor = facts.amountMinor,
            currency = facts.currency,
            balanceMinor = facts.balanceMinor,
            balanceCurrency = if (facts.balanceMinor != null) facts.balanceCurrency ?: facts.currency else null,
        )
    }

    /** An amount in a currency somewhere in it: anything else has nothing to show. */
    fun mightBeMoney(body: String): Boolean =
        body.any(Char::isDigit) && (SmsParser.curFirst.containsMatchIn(body) || SmsParser.curAfter.containsMatchIn(body))

    /**
     * What [sender]'s [seen] messages show, added to what earlier scans found ([before]).
     * A short scan rereads a little of the last one, so only messages newer than [before]
     * counted are added, and the chain of balances goes on from the last one it quoted.
     */
    fun evidence(sender: String, seen: List<Seen>, before: SenderEvidence?): SenderEvidence {
        val fresh = if (before == null) seen else seen.filter { it.atMillis > before.newestAt }
        val points = ArrayList<BalanceChain.Point>()
        if (before?.lastBalanceMinor != null && before.lastBalanceCurrency != null) {
            // Where the last scan left the balance. It moved no money itself.
            points += BalanceChain.Point(
                id = CARRIED,
                sender = sender,
                tail = null,
                atMillis = before.lastAt,
                amountMinor = 0L,
                currency = before.lastBalanceCurrency,
                balanceMinor = before.lastBalanceMinor,
                balanceCurrency = before.lastBalanceCurrency,
            )
        }
        var withBalance = 0
        for (s in fresh) {
            if (s.amountMinor == null || s.currency == null || s.balanceMinor == null || s.balanceCurrency == null) continue
            withBalance++
            points += BalanceChain.Point(
                id = s.id,
                sender = sender,
                tail = null,
                atMillis = s.atMillis,
                amountMinor = s.amountMinor,
                currency = s.currency,
                balanceMinor = s.balanceMinor,
                balanceCurrency = s.balanceCurrency,
            )
        }
        val proofs = BalanceChain.directions(points).keys.count { it != CARRIED }
        val last = fresh.filter { it.balanceMinor != null && it.balanceCurrency != null }.maxByOrNull { it.atMillis }
        return SenderEvidence(
            sender = before?.sender ?: sender,
            messages = (before?.messages ?: 0) + fresh.size,
            withBalance = (before?.withBalance ?: 0) + withBalance,
            proofs = (before?.proofs ?: 0) + proofs,
            newestAt = maxOf(before?.newestAt ?: 0L, fresh.maxOfOrNull { it.atMillis } ?: 0L),
            lastBalanceMinor = last?.balanceMinor ?: before?.lastBalanceMinor,
            lastBalanceCurrency = last?.balanceCurrency ?: before?.lastBalanceCurrency,
            lastAt = last?.atMillis ?: before?.lastAt ?: 0L,
        )
    }

    private const val CARRIED = "carried-over"
}
