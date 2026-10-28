package com.alyaqdhan.riyal.data

import androidx.annotation.StringRes
import com.alyaqdhan.riyal.R

import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Numerals
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure-Kotlin message parser (fully unit-testable, no Android dependencies).
 *
 * Contract, in order:
 *  1. Keyword gate, a message is not processed AT ALL unless it contains one of the
 *     user's withdraw/deposit keywords (English or Arabic). Everything else → Skipped.
 *  2. Amount extraction, currency-tagged numbers first (OMR 12.500 / 12.500 OMR /
 *     ر.ع ٢٥٫٥٠٠), "balance"-adjacent numbers excluded, the candidate closest to the
 *     keyword wins. Failure → NeedsReview with a human-readable reason.
 *  3. Merchant / counterparty via "at … / to … / from …" patterns.
 *  4. Account context: the balance the bank quoted and the account tail it quoted it
 *     for. These are not part of the transaction, they are what lets [AccountDiscovery]
 *     seed a real account with a real balance instead of asking the user to type one.
 *
 * Every decision is appended to a trace so the UI can show exactly what happened.
 */
data class ParseNote(@StringRes val id: Int, val args: List<Any?> = emptyList())

class SmsParser(
    expenseKeywords: Collection<String>,
    incomeKeywords: Collection<String>,
    private val defaultCurrency: String = "OMR",
) {

    sealed class Result {
        data class Parsed(
            val amountMinor: Long,
            val currency: String,
            val direction: Direction,
            val merchant: String?,
            val confidence: Int,
            val trace: List<ParseNote>,
            /** The balance the bank quoted in this message, if it quoted one. */
            val balanceMinor: Long? = null,
            /**
             * The currency of [balanceMinor]. Usually the same as [currency], but not on a
             * purchase made abroad: "USD 9.99 at STEAM ... Avl Bal OMR 120.000" spent
             * dollars from an account that holds rials.
             */
            val balanceCurrency: String? = null,
            /** Last digits of the account this message is about ("a/c XXXX1234"). */
            val accountTail: String? = null,
            /**
             * Whether that tail is a card's last digits rather than an account number.
             * A card belongs to an account but does not share its number, so a card
             * tail can say which account a message is about only by elimination.
             */
            val tailIsCard: Boolean = false,
            /**
             * The digits written before the mask in a masked account number: "00210" of
             * "00210 ###### 001". The branch, never the account - an earlier reading took
             * its end for one, and this is how that account is recognised and folded.
             */
            val accountHead: String? = null,
            /** The wording suggests moving money between accounts rather than spending. */
            val transferHint: Boolean = false,
            /**
             * The message names both ends itself ("debited from your A/C 0038 and
             * credited to your A/C 0022"), so it is a whole transfer on its own and
             * needs no second message to pair with.
             */
            val selfTransferTo: String? = null,
            /**
             * The bank's own timestamp for the transaction ("2026/07/23 10:21:53"),
             * which is not the same thing as when the message arrived: a bank can text
             * one side of a transfer an hour after the other, both quoting this.
             */
            val bankStamp: String? = null,
        ) : Result()

        data class NeedsReview(
            val reason: String,
            val trace: List<ParseNote>,
            /**
             * What was readable even though the message as a whole was not.
             *
             * A message can carry a perfectly clear amount and still say nothing this
             * parser recognises about which way the money went - "your card ···1234 was
             * used for OMR 5.500 at LULU" names no keyword at all. Throwing the amount
             * away meant the user had to retype a figure that was sitting right there.
             */
            val amountMinor: Long? = null,
            val currency: String? = null,
            val merchant: String? = null,
            /**
             * Words from the message that would make it readable next time, best first.
             * Offered to the user to adopt; never adopted automatically, because a word
             * added to the gate changes how every future message is read.
             */
            val suggestedWords: List<String> = emptyList(),
        ) : Result()

        data class Skipped(val reason: ParseNote) : Result()
    }

    private val expense = expenseKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    private val income = incomeKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun parse(rawBody: String): Result {
        val trace = mutableListOf<ParseNote>()
        val body = normalize(rawBody)
        if (body != rawBody) trace += ParseNote(R.string.trace_normalized)
        val lower = body.lowercase()

        // 1 ── keyword gate
        val hits = ArrayList<KeywordHit>()
        for (kw in expense) {
            val p = lower.indexOf(kw)
            if (p >= 0) hits += KeywordHit(kw, p, Direction.EXPENSE)
        }
        for (kw in income) {
            val p = lower.indexOf(kw)
            if (p >= 0) hits += KeywordHit(kw, p, Direction.INCOME)
        }
        // 1b ── advertising gate. A bank markets to you from the same sender it uses for
        // real alerts, and "win a prize worth OMR 2,250" or "minimum salary transfer of
        // OMR 500" contains both a money word and an amount. Nothing was debited or
        // credited, so recording it invents spending and income that never happened.
        //
        // These two gates run before the keyword decision, not after it. They are what
        // keeps a message with an amount and no keyword from reaching the user as a
        // question: an advert has both, and being asked about every one of them would
        // make the queue useless.
        val advert = ADVERT_WORDS.filter { wordIn(lower, it) }
        if (advert.isNotEmpty()) {
            return Result.Skipped(ParseNote(R.string.skip_advert, listOf(advert.first())))
        }

        // 1c ── a bank also texts about money that did NOT move: a request waiting for
        // your approval, an instruction you cancelled, a card that was declined. Each
        // names an amount, and recording any of them invents a transaction.
        val notMoved = NOT_MOVED_WORDS.firstOrNull { wordIn(lower, it) }
        if (notMoved != null) {
            return Result.Skipped(ParseNote(R.string.skip_not_moved, listOf(notMoved)))
        }

        // 1d ── no keyword, but possibly still a transaction.
        //
        // The gate exists so the app never guesses, and it stays: nothing here is
        // recorded. What changed is that a message carrying a real amount is now handed
        // to the user instead of dropped in silence. Every bank has wording the list
        // does not cover yet ("your card was used for", "an amount of ... has been
        // applied to"), and the old behaviour was that those purchases simply never
        // existed, with nothing on any screen to say so.
        if (hits.isEmpty()) return unknownDirection(body, lower, trace)
        hits.sortBy { it.pos }
        val hit = hits.first()
        var confidence = 100
        trace += ParseNote(
            R.string.trace_keyword,
            listOf(
                hit.keyword,
                ParseNote(
                    if (hit.direction == Direction.EXPENSE) R.string.log_word_out
                    else R.string.log_word_in
                ),
            ),
        )
        if (hits.any { it.direction != hit.direction }) {
            confidence -= 20
            trace += ParseNote(R.string.trace_both_directions)
        }
        return read(body, lower, hit, trace, confidence)
    }

    /**
     * Reads [rawBody] as money moving in [direction], whatever its words say or whether
     * it has any. For a message whose kind is already known some other way - the user
     * taught its template, or the account's balances proved it - so the amount, the
     * counterparty and the account are read exactly as they would be for a message the
     * keywords had decided.
     *
     * Null when there is no usable amount, which no amount of knowing the direction can
     * fix.
     */
    fun parseAs(rawBody: String, direction: Direction): Result.Parsed? {
        val body = normalize(rawBody)
        val lower = body.lowercase()
        val usable = findAmounts(body, lower).filter { !it.nearBalance }
        val anchor = usable.firstOrNull { c -> c.currencyToken?.let { tokenToIso(it) } == defaultCurrency }
            ?: usable.firstOrNull()
            ?: return null
        val trace = mutableListOf<ParseNote>()
        if (body != rawBody) trace += ParseNote(R.string.trace_normalized)
        return read(body, lower, KeywordHit("", anchor.pos, direction), trace, 90) as? Result.Parsed
    }

    /** Everything after the direction is settled: amount, counterparty, account. */
    private fun read(
        body: String,
        lower: String,
        hit: KeywordHit,
        trace: MutableList<ParseNote>,
        startConfidence: Int,
    ): Result {
        var confidence = startConfidence

        // 2 ── amount
        val candidates = findAmounts(body, lower)
        if (candidates.isEmpty()) {
            trace += ParseNote(R.string.trace_no_candidates)
        } else {
            trace += ParseNote(
                R.string.trace_candidates,
                listOf(
                    candidates.joinToString("; ") { c ->
                        c.raw + (c.currencyToken?.let { " [$it]" } ?: "")
                    }
                ),
            )
            if (candidates.any { it.nearBalance }) {
                trace += ParseNote(R.string.trace_candidates_balance)
            }
        }
        if (candidates.isEmpty()) return Result.NeedsReview(ReviewReason.NO_AMOUNT, trace)
        val usable = candidates.filter { !it.nearBalance }
        if (usable.isEmpty()) return Result.NeedsReview(ReviewReason.BALANCE_ONLY, trace)
        // A purchase abroad is often quoted twice - "USD 9.99 (OMR 3.841)" - and the
        // figure in the account's own currency is the one that actually left it, fees
        // and the bank's rate included. Only when there is none is the foreign figure
        // taken, to be converted when the record is built.
        val home = usable.filter { c -> c.currencyToken?.let { tokenToIso(it) } == defaultCurrency }
        val chosen = home.ifEmpty { usable }.minByOrNull { abs(it.pos - hit.pos) }!!

        var currency = chosen.currencyToken?.let { tokenToIso(it) }
        if (currency == null) {
            currency = defaultCurrency
            confidence -= 15
            trace += ParseNote(R.string.trace_default_currency, listOf(defaultCurrency))
        }
        val value = try {
            BigDecimal(chosen.raw.replace(",", ""))
        } catch (e: NumberFormatException) {
            return Result.NeedsReview(
                ReviewReason.AMOUNT_UNPARSABLE + ReviewReason.SEPARATOR + chosen.raw,
                trace,
            )
        }
        if (value.signum() <= 0) return Result.NeedsReview(ReviewReason.AMOUNT_ZERO, trace)
        val minor = Money.toMinor(value, currency)
        trace += ParseNote(R.string.trace_amount, listOf(currency, value.toPlainString()))

        // 3 ── merchant / counterparty
        val merchant = findMerchant(body, hit.pos, hit.direction, lower)
        if (merchant != null) {
            trace += ParseNote(R.string.trace_merchant, listOf(merchant))
        } else {
            confidence -= 10
            trace += ParseNote(R.string.trace_no_merchant)
        }

        // 4 ── account context: the balance-like numbers the amount step rejected are
        // exactly what an account needs, so keep the last one instead of dropping it.
        val balanceCandidate = candidates.lastOrNull { it.nearBalance }
        val balanceCurrency = balanceCandidate?.currencyToken?.let { tokenToIso(it) } ?: currency
        val balanceMinor = balanceCandidate?.let { c ->
            runCatching { Money.toMinor(BigDecimal(c.raw.replace(",", "")), balanceCurrency) }.getOrNull()
        }
        if (balanceMinor != null) {
            trace += ParseNote(
                R.string.trace_balance,
                listOf(Money.format(balanceMinor, balanceCurrency)),
            )
        }
        val tail = findAccountTail(body)
        val accountTail = tail?.digits
        if (tail != null) {
            trace += ParseNote(
                if (tail.fromCard) R.string.trace_card_ending else R.string.trace_account_ending,
                listOf(tail.digits),
            )
        }
        val transferHint = TRANSFER_WORDS.any { it in lower }
        if (transferHint) trace += ParseNote(R.string.trace_transfer_hint)
        // One message, both ends: the bank is telling you it moved money between two
        // of your own accounts. Judged on its first keyword alone this reads as a plain
        // debit, which counts your own transfer as spending.
        val selfTransferTo = SELF_TRANSFER.find(body)?.groupValues?.get(1)
            ?.let { asDisplayed(it.trim(), readsRightToLeft(body)) }
            ?.takeLastWhile { it.isDigit() }?.takeIf { it.length >= 3 }?.takeLast(4)
        if (selfTransferTo != null) {
            trace += ParseNote(R.string.trace_self_transfer, listOf(selfTransferTo))
        }
        val bankStamp = findBankStamp(body)
        if (bankStamp != null) trace += ParseNote(R.string.trace_bank_stamp, listOf(bankStamp))

        return Result.Parsed(
            amountMinor = minor,
            currency = currency,
            direction = hit.direction,
            merchant = merchant,
            confidence = confidence.coerceIn(5, 100),
            trace = trace,
            balanceMinor = balanceMinor,
            balanceCurrency = balanceCurrency.takeIf { balanceMinor != null },
            accountTail = accountTail,
            tailIsCard = tail?.fromCard == true,
            accountHead = tail?.head,
            transferHint = transferHint,
            bankStamp = bankStamp,
            selfTransferTo = selfTransferTo,
        )
    }

    // ───────────────────────────── internals ─────────────────────────────

    /**
     * A message with no direction word: readable enough to ask about, or not worth it.
     *
     * The bar is deliberately high. One clean, non-balance amount means the bank is
     * telling you about one movement of money and only the direction is missing, which
     * is a question a person can answer in one tap. Several amounts, or only
     * balance-like ones, means the message is something else - a statement summary, a
     * promotion - and asking would be noise.
     */
    private fun unknownDirection(body: String, lower: String, trace: MutableList<ParseNote>): Result {
        val candidates = findAmounts(body, lower)
        val usable = candidates.filter { !it.nearBalance }
        if (usable.size != 1) {
            return Result.Skipped(
                if (usable.isEmpty()) {
                    ParseNote(R.string.skip_no_kw_no_amount)
                } else {
                    ParseNote(R.string.skip_no_kw_many_amounts, listOf(usable.size))
                }
            )
        }
        val chosen = usable.first()
        val currency = chosen.currencyToken?.let { tokenToIso(it) } ?: defaultCurrency
        val value = try {
            BigDecimal(chosen.raw.replace(",", ""))
        } catch (e: NumberFormatException) {
            return Result.Skipped(ParseNote(R.string.skip_no_kw_unparsable))
        }
        if (value.signum() <= 0) return Result.Skipped(ParseNote(R.string.skip_no_kw_zero))

        trace += ParseNote(R.string.trace_no_keyword)
        trace += ParseNote(R.string.trace_one_amount, listOf(currency, value.toPlainString()))
        val words = suggestKeywords(lower, chosen.pos)
        if (words.isNotEmpty()) {
            trace += ParseNote(R.string.trace_suggest_words, listOf(words.joinToString(", ")))
        }
        trace += ParseNote(R.string.trace_asking)
        return Result.NeedsReview(
            reason = ReviewReason.DIRECTION_UNKNOWN,
            trace = trace,
            amountMinor = Money.toMinor(value, currency),
            currency = currency,
            // Direction decides which "at/to/from" pattern applies, so with no direction
            // there is no merchant to read. The user names it if they want one.
            merchant = null,
            suggestedWords = words,
        )
    }

    /**
     * The words a bank put immediately before the amount, which is where the verb lives:
     * "...was used for OMR 5.500...". Offered as the keyword to adopt.
     *
     * Both the two-word phrase and the single word are offered, best first, because
     * "used for" is specific where "for" alone would gate in half the inbox.
     */
    private fun suggestKeywords(lower: String, amountPos: Int): List<String> {
        val words = lower.take(amountPos)
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 2 && it !in NOISE_WORDS && !maskRun.matches(it) }
        if (words.isEmpty()) return emptyList()
        val last = words.last()
        val pair = if (words.size >= 2) words[words.size - 2] + " " + last else null
        // A word like "for" or "was" would gate in most of the inbox on its own, so it
        // is only ever offered inside a phrase. The single-word option skips back to
        // one that actually carries meaning.
        val single = words.lastOrNull { it !in WEAK_ALONE }
        return listOfNotNull(pair, single)
            .filter { it !in expense && it !in income }
            .distinct()
    }

    private data class KeywordHit(val keyword: String, val pos: Int, val direction: Direction)

    private data class Candidate(
        val raw: String,
        val currencyToken: String?,
        val pos: Int,
        val nearBalance: Boolean,
    )

    private fun findAmounts(body: String, lower: String): List<Candidate> {
        val out = ArrayList<Candidate>()
        for (m in curFirst.findAll(body)) {
            val numGroup = m.groups[2]!!
            out += Candidate(numGroup.value, m.groups[1]!!.value, numGroup.range.first, isNearBalance(lower, numGroup.range.first))
        }
        for (m in curAfter.findAll(body)) {
            val numGroup = m.groups[1]!!
            out += Candidate(numGroup.value, m.groups[2]!!.value, numGroup.range.first, isNearBalance(lower, numGroup.range.first))
        }
        if (out.isEmpty()) {
            for (m in bareAmount.findAll(body)) {
                out += Candidate(m.value, null, m.range.first, isNearBalance(lower, m.range.first))
            }
        }
        return out.distinctBy { it.pos }.sortedBy { it.pos }
    }

    private fun isNearBalance(lower: String, pos: Int): Boolean {
        val ctx = lower.substring(max(0, pos - 22), pos)
        return "bal" in ctx || "رصيد" in ctx
    }

    /**
     * "a/c 0427XXXXXXXX0019", "a/c ...1234", "account ending 4321", "card *5678".
     * Banks mask the middle, the start, or nothing at all, so rather than describe
     * every style the pattern grabs the whole account token and the tail is read off
     * its trailing digits - which is the part the bank keeps quoting and the part that
     * routes a message to an account.
     *
     * Deliberately never matched against a bare number: without one of these words in
     * front, digits in a bank SMS are far more often a reference or a date.
     */
    /**
     * The transaction time the bank writes into the message body. Both halves of one
     * transfer quote the same value even when the two texts arrive an hour apart, which
     * makes it the only trustworthy way to tell "these are one movement" from "these are
     * two coincidences".
     */
    private fun findBankStamp(body: String): String? =
        bankStampPattern.find(body)?.let { m ->
            // Normalised: some messages run the date into the next word or double-space it.
            m.groupValues[1] + " " + m.groupValues[2]
        }

    /** The digits a message quotes, and whether they name a card or an account. */
    private data class Tail(val digits: String, val fromCard: Boolean, val head: String? = null)

    private fun findAccountTail(body: String): Tail? {
        var card: Tail? = null
        var best: Tail? = null
        var bestScore = -1
        val rtl = readsRightToLeft(body)
        for (m in accountTailPattern.findAll(body)) {
            val written = m.groupValues[1].trimEnd { !it.isDigit() && !it.isLetter() }
            // "رصيدك في الحساب 12.500" would otherwise read the balance as an account.
            if (amountLike.matches(written)) continue
            // "00210######": the end is hidden, and what is left is the branch.
            val rest = body.substring(m.groups[1]!!.range.first + written.length)
            if (written.all { it.isDigit() } && hiddenEnd.containsMatchIn(rest)) continue
            val token = asDisplayed(written, rtl)
            val digits = token.takeLastWhile { it.isDigit() }
            if (digits.length < 3) continue
            val keyword = m.groupValues[0].trimStart().lowercase()
            // A message that quotes both ("purchase on card *4321 from a/c ...0019")
            // is about the account; the card is just how it was reached.
            if (keyword.startsWith("card")) {
                if (card == null) card = Tail(digits.takeLast(4), fromCard = true)
                continue
            }
            // A message can quote two accounts: yours, and the one the money went to or
            // came from. Banks call yours "your account" and mask it - "00210######001" -
            // while the other party's is written plainly, so the number that reads most
            // like yours is the one the message is about. Ties go to the first.
            val masked = token.any { it in MASK_CHARS }
            val score = (if (isYours(body, m.range.first, keyword)) 2 else 0) + (if (masked) 1 else 0)
            if (score > bestScore) {
                val head = token.takeWhile { it.isDigit() }.takeIf { masked && it.length >= 3 }
                best = Tail(digits.takeLast(4), fromCard = false, head = head)
                bestScore = score
            }
        }
        return best ?: card
    }

    /**
     * Whether the phone lays the message out right to left: its first letter is Arabic,
     * which is how Android picks a paragraph's direction.
     */
    private fun readsRightToLeft(body: String): Boolean {
        for (c in body) {
            when (Character.getDirectionality(c)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
            }
        }
        return false
    }

    /**
     * An account number in the order the customer sees it.
     *
     * In a right-to-left message, groups of digits separated by spaces are laid out right
     * to left, one group after another. Bank Nizwa sends "001 ###### 00210" so that the
     * phone shows "00210 ###### 001", which is the number the customer knows. Read in the
     * order it was sent, its end was "00210", and that became the account "0210". A number
     * with no spaces ("00210######001") is one run, shown as sent.
     */
    private fun asDisplayed(token: String, rtl: Boolean): String {
        if (!rtl || token.none { it.isWhitespace() }) return token
        return token.split(Regex("[\\s\\u00A0]+")).filter { it.isNotEmpty() }.reversed().joinToString(" ")
    }

    /** "your a/c", "ur account", "حسابك", "حسابكم": the customer's own account. */
    private fun isYours(body: String, at: Int, keyword: String): Boolean {
        val word = keyword.takeWhile { it.isLetter() }
        if (word.startsWith("حساب")) return word.endsWith("ك") || word.endsWith("كم")
        val before = body.substring(max(0, at - 8), at).lowercase().trimEnd()
        return before.endsWith("your") || before == "ur" || before.endsWith(" ur")
    }

    private fun tokenToIso(token: String): String {
        val t = token.uppercase().replace(dotOrSpace, "")
        return when (t) {
            "RO", "رع" -> "OMR"
            "SR", "رس" -> "SAR"
            "DHS", "دإ" -> "AED"
            "RS", "₹" -> "INR"
            "$", "US$" -> "USD"
            "€" -> "EUR"
            "£" -> "GBP"
            else -> t
        }
    }

    private fun findMerchant(body: String, keywordPos: Int, direction: Direction, lower: String): String? {
        // English first, so nothing about an English message changes; an Arabic one
        // falls through to the same prepositions in its own script.
        val patterns = if (direction == Direction.INCOME) {
            listOf(fromPattern, viaPattern, atPattern, fromPatternAr, viaPatternAr, atPatternAr)
        } else {
            listOf(atPattern, toPattern, viaPattern, atPatternAr, toPatternAr, viaPatternAr)
        }
        for (rx in patterns) {
            val sorted = rx.findAll(body).sortedBy { abs(it.range.first - keywordPos) }
            for (m in sorted) {
                cleanMerchant(m.groupValues[1])?.let { return it }
            }
        }
        // An Arabic message writes the shop in Latin letters, and not every bank puts a
        // preposition in front of it: "تم خصم ... من حسابك رقم LULU HYPERMARKET\BOWSHAR\OM
        // في ...". In an Arabic sentence the Latin words are the names, so the first that
        // is a name is the shop.
        if (lower.any(::isArabicLetter)) {
            for (m in latinRun.findAll(body)) {
                if (m.value.length < 3) continue
                cleanMerchant(m.value)?.let { return it }
            }
        }
        return if ("atm" in lower) "ATM" else null
    }

    private fun isArabicLetter(c: Char) = c in '؀'..'ۿ'

    private fun cleanMerchant(raw: String): String? {
        // Card payments carry the card network's own field: name, city and country,
        // split by backslashes ("IKEA - FOOD SALES\\AL KHUWAIR\OM"). The name is the
        // first part; the rest would make every branch a different shop.
        var s = raw.substringBefore('\\').trim()
        s = cutTail.replace(s, "")
        s = cutTailAr.replace(s, "")
        // "5842-Al Fatah Food Com LLC" - the terminal number is the till, not the shop,
        // and leaving it in makes every branch of one shop a different merchant.
        s = s.replace(tillPrefix, "")
        s = s.replace(digitTail, "")
        s = s.replace(doubleSpace, " ")
        s = s.trim { it.isWhitespace() || it in TRIM_CHARS }
        // Whatever the cuts above removed took its introducing word's meaning with it:
        // "ALIS SALIM in a/c 0019" is cut at "a/c" and would otherwise be remembered as
        // "alis salim in", a second shop that is the same shop.
        s = UserRule.stripDangling(s)
        if (s.length < 2) return null
        if (notAName.matches(s)) return null
        if (s.lowercase().startsWith("a/c")) return null
        // "رصيدك الحالي في الحساب هو 150" - "في" is a preposition before a balance as
        // often as before a shop, so a name that turns out to be the account or the
        // balance is dropped and the next pattern gets its turn.
        if (s.removePrefix("ال").let { it.startsWith("حساب") || it.startsWith("رصيد") }) return null
        return s.take(40)
    }

    /**
     * Latin digits, without the invisible marks Arabic messages carry around their
     * numbers, and with full-width masks as plain ones. A direction mark between "00210"
     * and its mask was enough to end the account number at "00210", and its account
     * became "0210".
     */
    private fun normalize(s: String): String = Numerals.latin(s)
        .replace(invisibleMarks, "")
        .replace('＃', '#')
        .replace('＊', '*')
        .replace('●', '•')

    private fun wordIn(hay: String, phrase: String): Boolean {
        if (phrase.firstOrNull() !in 'a'..'z') return phrase in hay
        var from = 0
        while (from <= hay.length - phrase.length) {
            val at = hay.indexOf(phrase, from)
            if (at < 0) return false
            if (at == 0 || !hay[at - 1].isLetter()) return true
            from = at + 1
        }
        return false
    }

    companion object {
        /**
         * Bumped whenever anything in this file changes what a message parses to.
         *
         * Records are stored and reused between scans now, so an old record was produced
         * by an old parser. This number is what tells the scanner that everything it has
         * is out of date and the inbox must be read again. Forgetting to bump it after a
         * parser fix means the fix only reaches messages that arrive afterwards.
         */
        const val VERSION = 8

        /** A mask straight after the digits, with nothing after it: an account's hidden end. */
        private val hiddenEnd = Regex("^\\s*[Xx*#\\u2022]{2,}(?!\\s*\\d)")

        /** Direction marks, isolates, zero-width spaces and joiners: format, not text. */
        private val invisibleMarks = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\u061C\\uFEFF]")

        const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d{1,3})?|\d+(?:\.\d{1,3})?"""

        val CUR = listOf(
            "OMR", "R\\.?O\\.?", "ر\\.?\\s?ع\\.?",
            "SAR", "SR", "ر\\.?\\s?س\\.?",
            "AED", "DHS", "د\\.?\\s?إ\\.?",
            "KWD", "BHD", "QAR",
            "USD", "EUR", "GBP", "INR",
            // "US$" before "$", or the dollar sign alone would match and leave "US"
            // in front of it, where nothing reads it as part of the currency.
            "US\\$", "RS\\.?", "\\$", "€", "£", "₹",
        ).joinToString("|")

        val curFirst = Regex("(?i)(?:^|[\\s:(\\[])($CUR)\\s*($NUM)(?!\\d)")
        val curAfter = Regex("(?i)($NUM)\\s*($CUR)(?![\\p{L}\\d])")
        val bareAmount = Regex("""\b\d[\d,]*\.\d{2,3}\b""")

        val atPattern = Regex("(?i)\\b(?:at|@)\\s+([^.,;\\n]{2,48})")
        val toPattern = Regex("(?i)\\bto\\s+([^.,;\\n]{2,48})")
        val fromPattern = Regex("(?i)\\bfrom\\s+([^.,;\\n]{2,48})")
        val viaPattern = Regex("(?i)\\bvia\\s+([^.,;\\n]{2,48})")

        /**
         * The same four prepositions in Arabic, which is the half of the inbox that was
         * getting no merchant at all - and with no merchant there is no rule to save, so
         * the same shop was re-filed by hand every month.
         *
         * `\b` is useless here: Java's word boundary is built from `[a-zA-Z0-9_]`, so
         * between a space and an Arabic letter there is no boundary at all and the
         * pattern would never fire. "Not preceded by a letter" does the same job for
         * both scripts.
         */
        private const val NOT_LETTER = "(?<!\\p{L})"
        val atPatternAr = Regex("$NOT_LETTER(?:في|لدى)\\s+([^.,;\\n]{2,48})")
        val toPatternAr = Regex("$NOT_LETTER(?:إلى|الى)\\s+([^.,;\\n]{2,48})")
        val fromPatternAr = Regex("${NOT_LETTER}من\\s+([^.,;\\n]{2,48})")
        val viaPatternAr = Regex("$NOT_LETTER(?:عن\\s+طريق|بواسطة)\\s+([^.,;\\n]{2,48})")

        /**
         * Where a bank names the account a message is about. Both languages, because
         * an Omani bank sends both and the Arabic half carries most of the volume:
         *
         *  - "from your a/c 0427XXXXXXXX0019", "account ending 5678", "card *4321"
         *  - "من حسابك رقم 0630XXXXXXXX0001", "الحساب المنتهي ب 001"
         *
         * The Arabic word takes suffixes (حسابك = "your account") and the number often
         * runs straight into the next word with no space, so the keyword allows
         * trailing letters and the captured token simply stops at the first character
         * that cannot belong to an account number.
         */
        /** Digits, a run of mask, more digits, with spaces allowed around and inside the mask. */
        private const val MASKED_SPACED = "\\d*[\\s\\u00A0]*(?:[Xx*#\\u2022][\\s\\u00A0]*){2,}\\d{2,}"

        val accountTailPattern = Regex(
            "(?i)(?:\\ba/c|\\bacc(?:t|ount)?\\b|\\bcard\\b|حساب\\p{L}*)" +
                "[\\s:]*" +
                "(?:(?:no\\.?|number|#|ending(?:\\s+in)?|رقم|المنتهية?)\\s*(?:ب|في|in)?[\\s:]*)?" +
                // "#" masks too, and the mask can stand apart from the digits: Bank Nizwa
                // writes "00210 ###### 001". Read as one token up to the first space or
                // "#", that account was "0210" - the branch, not the account - and became
                // an account of its own beside the real one.
                "($MASKED_SPACED|[0-9Xx*#\\u2022.\\u2026-]{3,32})"
        )

        /** What a bank writes in place of the digits it hides. */
        val MASK_CHARS = setOf('X', 'x', '*', '#', '•', '…', '.')

        /** A plain decimal is an amount, never an account number. */
        val amountLike = Regex("""^\d+\.\d+$""")

        /**
         * A run of Latin words, as a name is written inside an Arabic message - up to the
         * card network's backslash, the Arabic that follows, or the end of a clause.
         */
        val latinRun = Regex("[A-Za-z][A-Za-z0-9&'.\\- ]*[A-Za-z0-9]")

    /**
     * "…debited from your A/C 0372XXXXXXXX0038 and credited to your A/C 0372XXXXXXXX0022…"
     * and its Arabic equivalent: one message carrying both sides of a transfer.
     */
    private val SELF_TRANSFER = Regex(
        "(?i)(?:debited|خصم).{0,80}?(?:credited to (?:your )?a/?c|إلى حساب\\p{L}*)" +
            "[\\s:.]*(?:no\\.?|number|#|رقم)?[\\s:.]*($MASKED_SPACED|[0-9Xx*#\\u2022.-]{3,32})",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** "2026/07/23 10:21:53", with any spacing the banks actually use around it. */
    private val bankStampPattern =
        Regex("""(\d{4}[/-]\d{2}[/-]\d{2})\s*(\d{2}:\d{2}:\d{2})""")

        /**
         * Wording that only appears in marketing. Every one of these is a phrase a bank
         * uses to sell something, never to report money that moved - the test is whether
         * a real debit alert could contain it, and none of these can.
         */
        /** Runs of the mask a bank writes a card number with: "xxxx7777". */
        val maskRun = Regex("x{2,}\\d*", RegexOption.IGNORE_CASE)

        /**
         * Words that are in every bank message and mean nothing on their own, dropped
         * before deciding what to offer as a keyword.
         */
        val NOISE_WORDS = setOf(
            "your", "you", "the", "this", "that", "and", "has", "have", "been",
            "omr", "rial", "rials", "riyal", "usd", "aed", "sar",
            "dear", "customer", "bank", "muscat", "meethaq", "info", "alert",
            "ريال", "عميلنا", "عميل", "بنك", "مسقط", "ميثاق",
        )

        /**
         * Real words, but far too broad to be a gate by themselves: "for" would match
         * nearly every message ever sent. Offered inside a phrase, never alone.
         */
        val WEAK_ALONE = setOf(
            "for", "was", "were", "is", "are", "of", "on", "at", "to", "from",
            "with", "by", "in", "an", "as", "card", "account", "amount", "transaction",
            "من", "إلى", "الى", "على", "في", "فى", "مع", "بطاقة", "حساب", "مبلغ",
        )

        val ADVERT_WORDS = listOf(
            "t&c", "t & c", "terms and conditions", "apply now", "click here",
            "be among the winners", "win ", "prize", "cash bonus", "lucky draw",
            "enter the draw", "offer valid", "valid till", "unsubscribe", "stop:",
            "دخول السحب", "اربح", "جائزة", "الفائزين", "اشترك الآن", "العرض ساري",
            "لالغاء الاشتراك", "لإلغاء الاشتراك",
            // Prize draws and competitions: a broadcaster quoting "cash prizes up to
            // 60,000 OMR" is not a bank telling you 60,000 left your account.
            "جوائز", "فرصة الفوز", "للمشاركة", "السحب أرسل", "بيسة للرسالة",
            "chance to win", "cash prizes", "to participate", "sms ",
        )

        /**
         * Wording that means the money stayed where it was. A payment *request* is an
         * invitation to pay, a deleted standing order is an instruction withdrawn, and
         * a declined card moved nothing - all of them quote an amount.
         */
        val NOT_MOVED_WORDS = listOf(
            "payment request", "request of omr", "please login to accept",
            "declined", "failed", "unsuccessful", "insufficient funds",
            "has been cancelled", "was cancelled", "reversal", "reversed",
            "طلب دفع", "حذف طلبك", "تم إلغاء", "تم الغاء", "لم تتم", "فشل",
            "رفض", "غير كافي", "غير كافية",
            // Service notices a bank sends about the account rather than about money:
            // switching mobile payments on or off, a card waiting at a branch. They say
            // "الدفع" or "Payment", which is enough to be read as a transaction, and
            // then land in Review with "no amount found" - a question with no answer,
            // asked of the user for something that was never a transaction.
            "تفعيل", "ready to collect", "ready for collection",
        )

        val TRANSFER_WORDS = listOf(
            "transfer", "transferred", "trf ", "own account", "between accounts",
            "تحويل", "حوالة", "حوّل",
        )

        val cutTail = Regex(
            "(?i)\\s+(?:on|dated|date|ref|reference|txn|trx|transaction|no|number|" +
                "a/c|acc|account|avl|available|bal|balance|ur|your|card|ending|has|was|is|" +
                "from|using)\\b.*"
        )

        /**
         * Where an Arabic message stops naming the counterparty and starts describing
         * the transaction: the date, the account, the balance, the channel. The two
         * "preposition + account" forms come first, or "في حسابك" would be cut at
         * "حسابك" and leave a dangling "في" on the end of the name.
         */
        val cutTailAr = Regex(
            "\\s+(?:في\\s+(?:ال)?حساب\\p{L}*|من\\s+(?:ال)?حساب\\p{L}*|إلى\\s+(?:ال)?حساب\\p{L}*|" +
                "(?:ال)?حساب\\p{L}*|بتاريخ|(?:ال)?رصيد\\p{L}*|ب[اإ]ستخدام|عن\\s+طريق|بواسطة|رقم|والتي).*",
            RegexOption.DOT_MATCHES_ALL,
        )

        /**
         * The rest of what cleanMerchant strips. Hoisted beside cutTail for the same
         * reason: a Regex built inside the function is recompiled for every message,
         * and a scan of a full inbox runs this thousands of times.
         */
        val dotOrSpace = Regex("[.\\s]")
        val tillPrefix = Regex("^\\d{2,}\\s*-\\s*")
        val digitTail = Regex("[\\d/:\\-]{4,}\\s*$")
        val doubleSpace = Regex("\\s{2,}")
        // Months too: "في 28-OCT, 12:55:24" is a date, and with its day cut off as a till
        // number it was being remembered as a shop called "OCT".
        val notAName = Regex(
            "(?i)^(?:x+\\d*|\\d+|you|yours?|jan|feb|mar|apr|may|jun|jul|aug|sept?|oct|nov|dec|" +
                "omr|r\\.?o|usd|aed|sar|otp|am|pm)$"
        )

        val TRIM_CHARS = ".,;:-_*'\"()".toSet()
    }
}
