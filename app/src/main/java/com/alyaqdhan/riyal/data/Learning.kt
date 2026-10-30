package com.alyaqdhan.riyal.data

import com.alyaqdhan.riyal.core.Numerals

/*
 * How Riyal learns to read a bank instead of being told how.
 *
 * Bank messages are not free text: each is one of a handful of templates the bank fills
 * in - the same sentence with a different amount, account, date and shop. A rule that
 * lists the words a template uses breaks the day a bank rewords one, and every bank
 * words them differently. So the unit of knowledge is the template itself:
 *
 *  1. [Skeleton] reduces a message to its template.
 *  2. [BalanceChain] proves which way money went without reading a single word: when an
 *     account quotes a balance in two messages and it moved by exactly the amount, that
 *     is money out or money in, whatever the message called it.
 *  3. [TemplateInference] spreads what was proven to every message of the template,
 *     including the ones that quote no balance.
 *  4. What is still uncertain is asked, by [Tutor], a few templates at a time - and an
 *     answer about one message is an answer about its whole template.
 */

/** What a kind of message means. */
enum class MessageKind {
    OUT, IN, TRANSFER, NONE;

    val direction: Direction?
        get() = when (this) {
            OUT -> Direction.EXPENSE
            IN -> Direction.INCOME
            else -> null
        }

    companion object {
        fun of(direction: Direction): MessageKind =
            if (direction == Direction.INCOME) IN else OUT
    }
}

/**
 * A template's meaning as the user taught it. Stored, and the strongest evidence there
 * is: nothing the inbox says overrides it.
 *
 * [accountId] is where its money moved, when the user said: a bank that holds none of
 * their accounts writing that a payment was released to them ("Transfer of OMR 10.000
 * has been released by order of ABC TRADING") is money arriving in an account at another
 * bank. Without it, such a sender would be taken for a bank of the user's own and given
 * an account.
 */
data class TemplateLabel(val kind: MessageKind, val atMillis: Long, val accountId: String? = null)

/**
 * What one scan learned about one template: how many messages use it, one to show, and
 * the app's best reading of it with how sure it is. What the setup questions are built
 * from.
 */
data class TemplateStat(
    val key: String,
    val sender: String,
    val count: Int,
    val sampleId: String,
    val sampleBody: String,
    val sampleAt: Long,
    /** The reading, or null when nothing in the inbox suggests one. */
    val guess: MessageKind?,
    val confidence: Float,
    /** Messages of this template whose direction the balances proved, each way. */
    val provenOut: Int = 0,
    val provenIn: Int = 0,
)

object Skeleton {

    /**
     * Bumped whenever what a template looks like changes. Labels are keyed by template,
     * so a change here detaches every one the user taught - which is why it is its own
     * number and not the parser's - unless they are carried over ([carryOver]).
     *
     * 3: the shop after "at" is a name whatever its case.
     */
    const val VERSION = 3

    /** The shape before this one, whose labels [carryOver] moves to this one. */
    const val LEGACY_VERSION = 2

    private val amountWithCurrency = Regex(
        "(?i)(?:(?:${SmsParser.CUR})\\s*\\d[\\d,]*(?:\\.\\d+)?|\\d[\\d,]*(?:\\.\\d+)?\\s*(?:${SmsParser.CUR})(?![\\p{L}\\d]))"
    )

    /** Anything with a digit in it: amounts, account numbers, dates, times, references. */
    private val withDigit = Regex("[\\p{L}\\d*•.\\-/:]*\\d[\\p{L}\\d*•.\\-/:]*")

    /** Runs of Latin-script words: in an Arabic message, these are names. */
    private val latinRun = Regex("[A-Za-z][A-Za-z&'.\\-]*(?:\\s+[A-Za-z][A-Za-z&'.\\-]*)*")

    /** Runs of capitalised words: in a mixed-case English message, these are names. */
    private val capsRun = Regex("\\b[A-Z][A-Z&'.\\-]+(?:\\s+[A-Z][A-Z&'.\\-]*)*\\b")

    /**
     * Capitalised words that are part of how a bank writes rather than who was paid.
     * Masking "ATM" would make a withdrawal look like a card purchase.
     */
    private val KEEP = setOf("ATM", "POS", "CDM", "ACH", "MBI", "OTP", "SMS", "IBAN", "SWIFT", "VAT", "PIN")

    /**
     * Where a message names the shop: "at" and the words after it, up to where the
     * sentence carries on - "on" a date, "Avl Bal", the end of the clause - which is where
     * the parser ends a shop's name too. Card networks write the shop in any case
     * ("GOOGLE *Some Game Studio"), so case cannot tell it from the template's own words,
     * and every shop was a template of its own: a purchase taught as money out taught
     * nothing about the next shop.
     */
    private val shopAfterAt = Regex(
        "(?i)\\bat\\s+((?:(?!(?:on|dated|date|ref|reference|txn|trx|transaction|no|number|a/c|acc|account|" +
            "avl|available|bal|balance|ur|your|card|ending|has|was|is|from|using)\\b)[^\\s.,;¤#@]+\\s*)+)"
    )

    private val separators = Regex("[^\\p{L}¤#@]+")

    private val space = Regex("\\s+")

    /**
     * Hashes a bank writes over what it hides: "00210######001", "HASS####AMAL". Dropped
     * before anything else, so a hidden number is one number and a hidden name one name -
     * otherwise every length of mask is a template of its own.
     */
    private val hashMask = Regex("#{2,}")

    /**
     * The card network's own field, pasted in whole: "LULU HYPERMARKET\\BOWSHAR\OM\123".
     * Name, city, country and terminal are all filled in, so the whole field is one name;
     * left to the other rules, a shop with a terminal number and one without were two
     * templates.
     */
    private val cardField = Regex("""[A-Za-z0-9&'.\- ]*[A-Za-z][A-Za-z0-9&'.\- ]*(?:\\+[A-Za-z0-9&'.\- ]*)+""")

    /**
     * A run of name-like words with the template's own words left in place, word by
     * word: "ATM SEEB" is a withdrawal at an ATM in Seeb, not a shop called that.
     */
    private fun maskNames(run: String): String =
        " " + run.split(space).joinToString(" ") { w -> if (w in KEEP) w else "@" } + " "

    private fun isArabicLetter(c: Char) = c in '؀'..'ۿ' || c in 'ݐ'..'ݿ'

    /**
     * The template [body] was written from, prefixed by who sent it: the words a bank
     * fills in are masked, and what is left is what every message of the kind shares.
     *
     * Deterministic from the message alone, so the same message gives the same key on
     * every scan and a label taught once keeps applying.
     */
    fun of(sender: String, body: String): String = build(sender, body, shops = true)

    /**
     * The fingerprint as [LEGACY_VERSION] made it, with a shop's words left in whenever
     * they were not in capitals. Only for carrying labels over.
     */
    fun legacyOf(sender: String, body: String): String = build(sender, body, shops = false)

    /**
     * Labels taught under [LEGACY_VERSION], moved to this fingerprint through the messages
     * they apply to: each message is fingerprinted both ways, and the label on its old
     * key goes to its new one. Several shops' templates are one now, so where their
     * labels disagree the one taught last wins.
     */
    fun carryOver(
        labels: Map<String, TemplateLabel>,
        messages: Sequence<Pair<String, String>>,
    ): Map<String, TemplateLabel> {
        if (labels.isEmpty()) return emptyMap()
        val out = HashMap<String, TemplateLabel>()
        for ((sender, body) in messages) {
            if (body.isBlank()) continue
            val label = labels[legacyOf(sender, body)] ?: continue
            val key = of(sender, body)
            val held = out[key]
            if (held == null || label.atMillis > held.atMillis) out[key] = label
        }
        return out
    }

    private fun build(sender: String, body: String, shops: Boolean): String {
        var s = Numerals.latin(body).replace(hashMask, "")
        s = amountWithCurrency.replace(s, " ¤ ")
        s = cardField.replace(s, " @ ")
        s = withDigit.replace(s, " # ")
        val arabic = s.any(::isArabicLetter)
        // Counted before the shop is masked, so whether a message counts as written in
        // capitals does not depend on how its shop is spelled.
        val letters = s.count { it.isLetter() }
        val upper = s.count { it.isUpperCase() }
        if (shops && !arabic) {
            s = shopAfterAt.replace(s) { m -> m.value.take(2) + maskNames(m.groupValues[1].trim()) }
        }
        s = when {
            arabic -> latinRun.replace(s) { m -> maskNames(m.value) }
            letters > 0 && upper.toFloat() / letters < 0.6f -> capsRun.replace(s) { m -> maskNames(m.value) }
            // A message written entirely in capitals has no case to tell names apart by,
            // so it keeps its words; its names then make templates of their own.
            else -> s
        }
        val tokens = s.lowercase().split(separators).filter { it.isNotEmpty() }
        val collapsed = ArrayList<String>(tokens.size)
        for (t in tokens) {
            if (t.length == 1 && t in "¤#@" && collapsed.lastOrNull() == t) continue
            collapsed += t
        }
        return sender.trim().lowercase() + "|" + collapsed.joinToString(" ")
    }
}

/**
 * What an account's balance proves, by arithmetic alone.
 *
 * Each message is joined to the latest earlier message from the same bank whose balance
 * it continues: the balance it quotes is that one's, moved by exactly its own amount.
 * Exactly, not roughly: a fee or a purchase nobody texted about breaks the equality, and
 * then nothing is claimed. Two things follow from a join:
 *
 *  - which way the money went, from the sign of the move, whatever the words said;
 *  - that the two messages are about one account. The joins make lines, and a line is
 *    one account's balance told message by message - which is what tells an account
 *    number from the other digits a bank puts where the account usually goes.
 *
 * The account digits a message quotes do not decide the join, because they are exactly
 * what cannot be trusted: a transfer quotes the other party's account, some messages
 * quote a reference. Only one coincidence is guarded against, the one a person's own
 * accounts produce: moving money between two of them, where one side's balance after
 * can equal the other's before.
 */
object BalanceChain {

    data class Point(
        val id: String,
        /** Who sent it. One bank's balance never continues another's. */
        val sender: String,
        /** The account digits the message quoted, if any. */
        val tail: String?,
        val atMillis: Long,
        val amountMinor: Long,
        val currency: String,
        val balanceMinor: Long,
        val balanceCurrency: String,
    )

    class Chains(
        /** Which way each joined message moved money. */
        val directions: Map<String, Direction>,
        /** Message id to its line. Messages on one line are one account. */
        val lineOf: Map<String, Int>,
    )

    /** How many earlier messages a message is tried against, newest first. */
    private const val LOOKBACK = 12

    fun directions(points: List<Point>): Map<String, Direction> = chain(points).directions

    fun chain(points: List<Point>): Chains {
        val directions = HashMap<String, Direction>()
        val index = points.withIndex().associate { it.value.id to it.index }
        val parent = IntArray(points.size) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) {
                parent[r] = parent[parent[r]]
                r = parent[r]
            }
            return r
        }

        for ((_, list) in points.groupBy { it.sender.trim().lowercase() to it.balanceCurrency }) {
            val ordered = list.sortedWith(compareBy({ it.atMillis }, { it.id }))
            // A balance is continued once: two messages cannot both be the next one.
            val continued = HashSet<Int>()
            for (i in ordered.indices) {
                val now = ordered[i]
                // Bought abroad: the balance moved by the converted figure, which the
                // message does not state exactly.
                if (now.currency != now.balanceCurrency || now.amountMinor <= 0L) continue
                var j = i - 1
                while (j >= 0 && j >= i - LOOKBACK) {
                    val before = ordered[j]
                    val direction = when (now.balanceMinor - before.balanceMinor) {
                        -now.amountMinor -> Direction.EXPENSE
                        now.amountMinor -> Direction.INCOME
                        else -> null
                    }
                    if (direction != null && j !in continued && !transferBetween(before, now)) {
                        directions[now.id] = direction
                        continued += j
                        val a = root(index.getValue(before.id))
                        val b = root(index.getValue(now.id))
                        if (a != b) parent[b] = a
                        break
                    }
                    j--
                }
            }
        }
        val lineOf = points.associate { it.id to root(index.getValue(it.id)) }
        return Chains(directions, lineOf)
    }

    /**
     * Two of the user's accounts, one paying the other: the payer's balance after can be
     * the payee's before, and then the payee's message looks like the payer's next one.
     * Both quote the same amount and name different accounts, which an account's own
     * consecutive messages almost never do.
     */
    private fun transferBetween(before: Point, now: Point): Boolean =
        before.tail != null && now.tail != null && before.tail != now.tail &&
            before.amountMinor == now.amountMinor
}

/**
 * What the messages of one template add up to.
 *
 * Proven directions outweigh read ones by a wide margin: a word can mislead ("الدفع" in
 * a message about money *received* through payment services), arithmetic cannot.
 */
object TemplateInference {

    data class Observation(
        val key: String,
        val sender: String,
        val messageId: String,
        val atMillis: Long,
        val body: String,
        /** What the words suggested, when they suggested anything. */
        val read: Direction?,
        /** What the balances proved, when they could. */
        val proven: Direction?,
        /** The message named both of the user's own accounts. */
        val selfTransfer: Boolean,
        /** The reader found nothing worth recording: an advert, a notice, no amount. */
        val skipped: Boolean,
        val hasAmount: Boolean,
    )

    /** How many agreeing proofs, and none against, make a template proven. */
    const val PROVEN_AT = 2

    fun infer(observations: List<Observation>): Map<String, TemplateStat> =
        observations.groupBy { it.key }.mapValues { (key, group) -> statOf(key, group) }

    private fun statOf(key: String, group: List<Observation>): TemplateStat {
        val provenOut = group.count { it.proven == Direction.EXPENSE }
        val provenIn = group.count { it.proven == Direction.INCOME }
        val readOut = group.count { it.read == Direction.EXPENSE }
        val readIn = group.count { it.read == Direction.INCOME }
        val transfers = group.count { it.selfTransfer }
        val skipped = group.count { it.skipped }

        val (guess, confidence) = when {
            transfers * 2 > group.size -> MessageKind.TRANSFER to 0.8f
            provenOut + provenIn > 0 -> {
                val out = provenOut >= provenIn
                val agree = maxOf(provenOut, provenIn)
                val against = minOf(provenOut, provenIn)
                var c = (agree + 1f) / (agree + against + 2f)
                // The words agreeing with the arithmetic is worth a little more trust.
                val readAgrees = if (out) readOut > readIn else readIn > readOut
                if (readAgrees) c += 0.1f
                (if (out) MessageKind.OUT else MessageKind.IN) to c.coerceAtMost(0.99f)
            }
            readOut + readIn > 0 -> {
                val out = readOut >= readIn
                val share = maxOf(readOut, readIn).toFloat() / (readOut + readIn)
                (if (out) MessageKind.OUT else MessageKind.IN) to (0.5f + 0.35f * share)
            }
            skipped == group.size -> MessageKind.NONE to 0.6f
            group.any { it.hasAmount } -> MessageKind.OUT to 0.3f
            else -> null to 0f
        }
        val sample = group.filter { it.hasAmount }.maxByOrNull { it.atMillis } ?: group.maxBy { it.atMillis }
        return TemplateStat(
            key = key,
            sender = group.first().sender,
            count = group.size,
            sampleId = sample.messageId,
            sampleBody = sample.body,
            sampleAt = sample.atMillis,
            guess = guess,
            confidence = confidence,
            provenOut = provenOut,
            provenIn = provenIn,
        )
    }

    /**
     * Whether the balances settled this template on their own: at least [PROVEN_AT]
     * proofs one way and none the other. Only then is its reading spread to messages
     * that quote no balance, or allowed to overrule what their words said.
     */
    fun isProven(stat: TemplateStat): Boolean =
        (stat.provenOut >= PROVEN_AT && stat.provenIn == 0) ||
            (stat.provenIn >= PROVEN_AT && stat.provenOut == 0)
}

/**
 * The setup's questions: which template to ask about next, and when to stop.
 *
 * It keeps an estimate of how often the app's reading is right - a beta posterior over
 * the answers so far, starting from three right and one wrong - and the share of all
 * messages it still expects to be reading wrongly:
 *
 *     expected error = Σ unasked templates (messages × (1 − accuracy × confidence)) / all messages
 *
 * Each question goes to the template that removes the most of that, and the questions
 * end once it falls under [TARGET]. A right answer raises the accuracy, so the remaining
 * error falls faster and the end comes sooner; a wrong one lowers it, and also makes the
 * sender's other readings less trusted, so more questions are needed. Which is the whole
 * deal offered to the user: confirm the app and it stops asking, correct it and it checks
 * its work.
 */
class Tutor(
    candidates: List<Candidate>,
    right: Int = 0,
    wrong: Int = 0,
) {
    data class Candidate(val key: String, val sender: String, val volume: Int, val confidence: Float)

    private val confidence = candidates.associate { it.key to it.confidence }.toMutableMap()
    private val all = candidates.filter { it.volume > 0 }.sortedByDescending { it.volume }
    private val total = all.sumOf { it.volume }.toFloat()
    private val answered = LinkedHashSet<String>()

    var right: Int = right
        private set
    var wrong: Int = wrong
        private set

    val accuracy: Float get() = (PRIOR_RIGHT + right) / (PRIOR_RIGHT + PRIOR_WRONG + right + wrong)

    private fun risk(c: Candidate): Float = c.volume * (1f - accuracy * (confidence[c.key] ?: 0f))

    private val remaining: List<Candidate> get() = all.filter { it.key !in answered }

    fun expectedError(): Float = if (total <= 0f) 0f else remaining.sumOf { risk(it).toDouble() }.toFloat() / total

    private val startError = expectedError()

    val asked: Int get() = answered.size

    val done: Boolean
        get() = remaining.isEmpty() || answered.size >= MAX_QUESTIONS ||
            (answered.size >= MIN_QUESTIONS && expectedError() <= TARGET)

    /** The template to ask about now, or null when the questions are over. */
    fun next(): Candidate? = if (done) null else remaining.maxByOrNull(::risk)

    fun answer(key: String, wasRight: Boolean) {
        if (!answered.add(key)) return
        if (wasRight) {
            right++
        } else {
            wrong++
            // One wrong reading from a bank makes its other readings worth less: they
            // were made by the same reasoning about the same sender's wording.
            val sender = all.firstOrNull { it.key == key }?.sender
            all.filter { it.sender == sender && it.key !in answered }.forEach {
                confidence[it.key] = (confidence[it.key] ?: 0f) * SIBLING_DOUBT
            }
        }
    }

    /** How far through: the share of the starting expected error that is gone. */
    val progress: Float
        get() = when {
            done -> 1f
            startError <= 0f -> 1f
            else -> (1f - expectedError() / startError).coerceIn(0f, 0.99f)
        }

    /** Questions still to come if every answer from here confirms the app. */
    fun questionsLeft(): Int {
        if (done) return 0
        val copy = Tutor(all.map { it.copy(confidence = confidence[it.key] ?: 0f) }, right, wrong)
        answered.forEach { copy.answered += it }
        var n = 0
        while (true) {
            val c = copy.next() ?: break
            copy.answer(c.key, wasRight = true)
            n++
        }
        return n
    }

    companion object {
        const val PRIOR_RIGHT = 3f
        const val PRIOR_WRONG = 1f
        const val TARGET = 0.05f
        const val MIN_QUESTIONS = 3
        const val MAX_QUESTIONS = 30
        const val SIBLING_DOUBT = 0.7f
    }
}
