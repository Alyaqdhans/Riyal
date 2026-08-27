package com.alyaqdhan.riyal.data

import com.alyaqdhan.riyal.core.Money
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
            val trace: List<String>,
            /** The balance the bank quoted in this message, if it quoted one. */
            val balanceMinor: Long? = null,
            /** Last digits of the account this message is about ("a/c XXXX1234"). */
            val accountTail: String? = null,
            /** The wording suggests moving money between accounts rather than spending. */
            val transferHint: Boolean = false,
        ) : Result()

        data class NeedsReview(val reason: String, val trace: List<String>) : Result()

        data class Skipped(val reason: String) : Result()
    }

    private val expense = expenseKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    private val income = incomeKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun parse(rawBody: String): Result {
        val trace = mutableListOf<String>()
        val body = normalize(rawBody)
        if (body != rawBody) trace += "normalized text (digits, separators, whitespace)"
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
        if (hits.isEmpty()) return Result.Skipped("no withdraw/deposit keyword")
        hits.sortBy { it.pos }
        val hit = hits.first()
        var confidence = 100
        trace += "keyword \"${hit.keyword}\" found → ${if (hit.direction == Direction.EXPENSE) "money out" else "money in"}"
        if (hits.any { it.direction != hit.direction }) {
            confidence -= 20
            trace += "both directions mentioned; trusting the earliest keyword"
        }

        // 2 ── amount
        val candidates = findAmounts(body, lower)
        trace += if (candidates.isEmpty()) {
            "no amount candidates found"
        } else {
            "amount candidates: " + candidates.joinToString("; ") { c ->
                buildString {
                    append(c.raw)
                    c.currencyToken?.let { append(" [").append(it).append("]") }
                    if (c.nearBalance) append(" ← looks like a balance, ignored")
                }
            }
        }
        if (candidates.isEmpty()) return Result.NeedsReview("no amount found", trace)
        val usable = candidates.filter { !it.nearBalance }
        if (usable.isEmpty()) return Result.NeedsReview("only balance-like amounts found", trace)
        val chosen = usable.minByOrNull { abs(it.pos - hit.pos) }!!

        var currency = chosen.currencyToken?.let { tokenToIso(it) }
        if (currency == null) {
            currency = defaultCurrency
            confidence -= 15
            trace += "no currency token; assuming default $defaultCurrency"
        }
        val value = try {
            BigDecimal(chosen.raw.replace(",", ""))
        } catch (e: NumberFormatException) {
            return Result.NeedsReview("could not parse amount \"${chosen.raw}\"", trace)
        }
        if (value.signum() <= 0) return Result.NeedsReview("amount is zero", trace)
        val minor = Money.toMinor(value, currency)
        trace += "amount = $currency ${value.toPlainString()} (candidate closest to keyword)"

        // 3 ── merchant / counterparty
        val merchant = findMerchant(body, hit.pos, hit.direction, lower)
        if (merchant != null) {
            trace += "merchant/counterparty: \"$merchant\""
        } else {
            confidence -= 10
            trace += "no merchant pattern matched"
        }

        // 4 ── account context: the balance-like numbers the amount step rejected are
        // exactly what an account needs, so keep the last one instead of dropping it.
        val balanceMinor = candidates.lastOrNull { it.nearBalance }?.let { c ->
            runCatching { Money.toMinor(BigDecimal(c.raw.replace(",", "")), currency) }.getOrNull()
        }
        if (balanceMinor != null) {
            trace += "balance quoted: ${Money.format(balanceMinor, currency)} (used to set up your account, not counted)"
        }
        val accountTail = findAccountTail(body)
        if (accountTail != null) trace += "account ending $accountTail"
        val transferHint = TRANSFER_WORDS.any { it in lower }
        if (transferHint) trace += "wording suggests a transfer between accounts"

        return Result.Parsed(
            amountMinor = minor,
            currency = currency,
            direction = hit.direction,
            merchant = merchant,
            confidence = confidence.coerceIn(5, 100),
            trace = trace,
            balanceMinor = balanceMinor,
            accountTail = accountTail,
            transferHint = transferHint,
        )
    }

    // ───────────────────────────── internals ─────────────────────────────

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
    private fun findAccountTail(body: String): String? {
        for (m in accountTailPattern.findAll(body)) {
            val token = m.groupValues[1].trimEnd { !it.isDigit() && !it.isLetter() }
            // "رصيدك في الحساب 12.500" would otherwise read the balance as an account.
            if (amountLike.matches(token)) continue
            val digits = token.takeLastWhile { it.isDigit() }
            if (digits.length >= 3) return digits.takeLast(4)
        }
        return null
    }

    private fun tokenToIso(token: String): String {
        val t = token.uppercase().replace(Regex("[.\\s]"), "")
        return when (t) {
            "RO", "رع" -> "OMR"
            "SR", "رس" -> "SAR"
            "DHS", "دإ" -> "AED"
            "RS", "₹" -> "INR"
            "$" -> "USD"
            "€" -> "EUR"
            "£" -> "GBP"
            else -> t
        }
    }

    private fun findMerchant(body: String, keywordPos: Int, direction: Direction, lower: String): String? {
        val patterns = if (direction == Direction.INCOME) {
            listOf(fromPattern, viaPattern, atPattern)
        } else {
            listOf(atPattern, toPattern, viaPattern)
        }
        for (rx in patterns) {
            val sorted = rx.findAll(body).sortedBy { abs(it.range.first - keywordPos) }
            for (m in sorted) {
                cleanMerchant(m.groupValues[1])?.let { return it }
            }
        }
        return if ("atm" in lower) "ATM" else null
    }

    private fun cleanMerchant(raw: String): String? {
        var s = raw.trim()
        s = cutTail.replace(s, "")
        s = s.replace(Regex("[\\d/:\\-]{4,}\\s*$"), "")
        s = s.replace(Regex("\\s{2,}"), " ")
        s = s.trim { it.isWhitespace() || it in TRIM_CHARS }
        if (s.length < 2) return null
        if (Regex("(?i)^(?:x+\\d*|\\d+|you|yours?)$").matches(s)) return null
        if (s.lowercase().startsWith("a/c")) return null
        return s.take(40)
    }

    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '٠'..'٩' -> sb.append('0' + (ch - '٠')) // Arabic-Indic digits
                in '۰'..'۹' -> sb.append('0' + (ch - '۰')) // Eastern Arabic-Indic digits
                '٫' -> sb.append('.')                       // Arabic decimal separator
                '٬' -> sb.append(',')                       // Arabic thousands separator
                ' ', ' ', ' ', '​', '‎', '‏' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private companion object {
        const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d{1,3})?|\d+(?:\.\d{1,3})?"""

        val CUR = listOf(
            "OMR", "R\\.?O\\.?", "ر\\.?\\s?ع\\.?",
            "SAR", "SR", "ر\\.?\\s?س\\.?",
            "AED", "DHS", "د\\.?\\s?إ\\.?",
            "KWD", "BHD", "QAR",
            "USD", "EUR", "GBP", "INR",
            "RS\\.?", "\\$", "€", "£", "₹",
        ).joinToString("|")

        val curFirst = Regex("(?i)(?:^|[\\s:(\\[])($CUR)\\s*($NUM)(?!\\d)")
        val curAfter = Regex("(?i)($NUM)\\s*($CUR)(?![\\p{L}\\d])")
        val bareAmount = Regex("""\b\d[\d,]*\.\d{2,3}\b""")

        val atPattern = Regex("(?i)\\b(?:at|@)\\s+([^.,;\\n]{2,48})")
        val toPattern = Regex("(?i)\\bto\\s+([^.,;\\n]{2,48})")
        val fromPattern = Regex("(?i)\\bfrom\\s+([^.,;\\n]{2,48})")
        val viaPattern = Regex("(?i)\\bvia\\s+([^.,;\\n]{2,48})")

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
        val accountTailPattern = Regex(
            "(?i)(?:\\ba/c|\\bacc(?:t|ount)?\\b|\\bcard\\b|حساب\\p{L}*)" +
                "[\\s:]*" +
                "(?:(?:no\\.?|number|#|ending(?:\\s+in)?|رقم|المنتهية?)\\s*(?:ب|في|in)?[\\s:]*)?" +
                "([0-9Xx*\\u2022.\\u2026-]{3,32})"
        )

        /** A plain decimal is an amount, never an account number. */
        val amountLike = Regex("""^\d+\.\d+$""")

        val TRANSFER_WORDS = listOf(
            "transfer", "transferred", "trf ", "own account", "between accounts",
            "تحويل", "حوالة", "حوّل",
        )

        val cutTail = Regex(
            "(?i)\\s+(?:on|dated|date|ref|reference|txn|trx|transaction|no|number|" +
                "a/c|acc|account|avl|available|bal|balance|ur|your|card|ending|has|was|is)\\b.*"
        )

        val TRIM_CHARS = ".,;:-_*'\"()".toSet()
    }
}
