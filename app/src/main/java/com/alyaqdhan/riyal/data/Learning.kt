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
 */
data class TemplateLabel(val kind: MessageKind, val atMillis: Long)

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
     * number and not the parser's.
     */
    const val VERSION = 1

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

    private val separators = Regex("[^\\p{L}¤#@]+")

    private val space = Regex("\\s+")

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
    fun of(sender: String, body: String): String {
        var s = Numerals.latin(body)
        s = amountWithCurrency.replace(s, " ¤ ")
        s = withDigit.replace(s, " # ")
        s = if (s.any(::isArabicLetter)) {
            latinRun.replace(s) { m -> maskNames(m.value) }
        } else {
            val letters = s.count { it.isLetter() }
            val upper = s.count { it.isUpperCase() }
            // A message written entirely in capitals has no case to tell names apart by,
            // so it keeps its words; its names then make templates of their own.
            if (letters > 0 && upper.toFloat() / letters < 0.6f) {
                capsRun.replace(s) { m -> maskNames(m.value) }
            } else {
                s
            }
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
 * Direction proven by arithmetic: an account's balance before and after a message.
 *
 * Messages are grouped by the account they quote, put in time order, and each one whose
 * balance differs from the one before by exactly its own amount is decided by the sign
 * of that difference. Exactly, not roughly: two purchases between a pair of messages, or
 * a fee, break the equality, and then nothing is claimed.
 */
object BalanceChain {

    data class Point(
        val id: String,
        /** Sender and account tail: which balance this message quotes. */
        val account: String,
        val atMillis: Long,
        val amountMinor: Long,
        val currency: String,
        val balanceMinor: Long,
        val balanceCurrency: String,
    )

    fun directions(points: List<Point>): Map<String, Direction> {
        val out = HashMap<String, Direction>()
        for ((_, list) in points.groupBy { it.account }) {
            val ordered = list.sortedWith(compareBy({ it.atMillis }, { it.id }))
            for (i in 1 until ordered.size) {
                val before = ordered[i - 1]
                val now = ordered[i]
                if (before.balanceCurrency != now.balanceCurrency) continue
                // Bought abroad: the balance moved by the converted figure, which the
                // message does not state exactly.
                if (now.currency != now.balanceCurrency) continue
                if (now.amountMinor <= 0L) continue
                val delta = now.balanceMinor - before.balanceMinor
                when (delta) {
                    -now.amountMinor -> out[now.id] = Direction.EXPENSE
                    now.amountMinor -> out[now.id] = Direction.INCOME
                }
            }
        }
        return out
    }
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
