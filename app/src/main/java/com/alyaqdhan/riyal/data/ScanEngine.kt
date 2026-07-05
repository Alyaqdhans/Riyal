package com.alyaqdhan.riyal.data

import android.content.Context
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.core.Verbose
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * One user-initiated pass over the inbox. Narrates every decision to [Verbose]:
 * what was queried, which messages matched, how each one was parsed, and, loudly -
 * which ones could not be read and why (those land in the Review tab).
 */
class ScanEngine(
    private val context: Context,
    private val prefs: Prefs,
    private val store: Store,
) {

    data class Progress(val processed: Int, val total: Int)

    suspend fun run(onProgress: (Progress) -> Unit): ScanSummary {
        val startedAt = System.currentTimeMillis()
        val months = prefs.scanRangeMonths
        val since = if (months <= 0) 0L
        else ZonedDateTime.now().minusMonths(months.toLong()).toInstant().toEpochMilli()

        Verbose.scan("──────── scan started ────────")
        Verbose.scan("mode: manual one-shot, this app has no background receiver")
        Verbose.scan("range: " + if (since == 0L) "entire inbox" else "last $months month(s), since ${fmtDate(since)}")
        Verbose.scan("expense keywords: ${prefs.expenseKeywords.joinToString(", ")}")
        Verbose.scan("income keywords: ${prefs.incomeKeywords.joinToString(", ")}")
        val allowlistOn = prefs.senderFilterEnabled
        val allowlist = prefs.senderAllowlist.map { it.lowercase() }.toSet()
        val bankOnly = prefs.bankSendersOnly
        if (allowlistOn) {
            Verbose.scan("sender allowlist ON: only ${prefs.senderAllowlist.joinToString(", ")}")
        } else if (bankOnly) {
            Verbose.scan(
                "bank senders first: known bank names are read directly; other senders are " +
                    "recorded only when a message parses as a real transaction, and such " +
                    "senders are then auto-approved (your second bank gets learned)"
            )
            if (allowlist.isNotEmpty()) {
                Verbose.scan("approved/learned senders: ${prefs.senderAllowlist.joinToString(", ")}")
            }
        } else {
            Verbose.scan("sender filters OFF: every sender is considered (bodies are still keyword-gated)")
        }

        val messages = SmsReader.readInbox(context, since)
        val seenSenders = messages.mapTo(HashSet()) { it.sender }
        Verbose.scan("inbox query returned ${messages.size} message(s) from ${seenSenders.size} sender(s)")

        val parser = SmsParser(prefs.expenseKeywords, prefs.incomeKeywords, prefs.defaultCurrency)
        val rules = store.rules.value
        val mutedTemplates = store.muted.value.mapTo(HashSet()) { it.template }
        val neededTemplates = store.needed.value
        if (mutedTemplates.isNotEmpty()) {
            Verbose.scan(
                "${mutedTemplates.size} dismissed message kind(s): similar messages are " +
                    "auto-dismissed, restore them any time in Review"
            )
        }
        val txns = LinkedHashMap<String, Txn>()
        val reviews = ArrayList<ReviewItem>()
        var skipped = 0
        var matched = 0
        var needsReview = 0
        var autoDismissed = 0
        var duplicates = 0
        var skipLinesLogged = 0

        fun logSkip(text: String) {
            when {
                skipLinesLogged < MAX_SKIP_LINES -> {
                    Verbose.skip(text)
                    skipLinesLogged++
                }
                skipLinesLogged == MAX_SKIP_LINES -> {
                    Verbose.skip("…more skipped messages, muting further skip lines (counts still tallied)")
                    skipLinesLogged++
                }
            }
        }

        val learned = HashSet<String>()

        messages.forEachIndexed { index, msg ->
            if (index % 25 == 0) onProgress(Progress(index, messages.size))

            // Banks send from named sender IDs ("BankMuscat"), people send from phone
            // numbers. Numeric senders are never read unless explicitly approved in
            // Settings → Senders (that's where a bank texting from a number is added).
            if (isPhoneNumber(msg.sender) && msg.sender.lowercase() !in allowlist) {
                skipped++
                logSkip("${msg.sender} · skipped (numeric sender = personal contact; approve it in Settings if it's really a bank)")
                return@forEachIndexed
            }
            if (allowlistOn && msg.sender.lowercase() !in allowlist) {
                skipped++
                logSkip("${msg.sender} · skipped (sender not in your allowlist)")
                return@forEachIndexed
            }
            // Bank gate, self-teaching: senders that don't look like a bank are still
            // keyword-gated and parsed, but only a fully parsed transaction is kept,
            // and the sender is then learned as a bank. Their unreadable messages are
            // NOT sent to Review, so promo senders can't spam it.
            val trustedSender = !bankOnly || allowlistOn || looksLikeBank(msg.sender) ||
                msg.sender.lowercase() in allowlist || msg.sender.lowercase() in learned

            when (val result = parser.parse(msg.body)) {
                is SmsParser.Result.Skipped -> {
                    skipped++
                    logSkip("${msg.sender} · skipped (${result.reason}), content not processed")
                }

                is SmsParser.Result.NeedsReview -> {
                    val template = MsgTemplate.of(msg.sender, msg.body)
                    if (!trustedSender && template !in neededTemplates) {
                        skipped++
                        logSkip("${msg.sender} · matched keywords but unparsable and sender isn't a known bank, skipped")
                        return@forEachIndexed
                    }
                    matched++
                    if (template in mutedTemplates) {
                        autoDismissed++
                        logSkip(
                            "${msg.sender} · unreadable, but you dismissed this kind of message " +
                                "before → auto-dismissed (restore it in Review)"
                        )
                        reviews += ReviewItem(
                            hashOf(msg), msg.atMillis, msg.sender, msg.body, result.reason,
                            state = ReviewItem.STATE_DISMISSED,
                        )
                        return@forEachIndexed
                    }
                    needsReview++
                    Verbose.fail("✉ ${msg.sender} · ${fmtDateTime(msg.atMillis)} → COULD NOT READ: ${result.reason}")
                    result.trace.forEach { Verbose.fail("    · $it") }
                    if (!trustedSender) {
                        Verbose.fail("    · sender isn't a known bank, kept because you recorded a message like this before")
                    }
                    Verbose.fail("    → added to Review so you decide what it was")
                    reviews += ReviewItem(hashOf(msg), msg.atMillis, msg.sender, msg.body, result.reason)
                }

                is SmsParser.Result.Parsed -> {
                    matched++
                    if (!trustedSender && msg.sender.lowercase() !in learned) {
                        learned += msg.sender.lowercase()
                        Verbose.ok(
                            "✦ learned sender: \"${msg.sender}\" sends real transactions, " +
                                "auto-approved (remove it in Settings → Senders)"
                        )
                    }
                    val id = hashOf(msg)
                    if (txns.containsKey(id)) {
                        duplicates++
                        logSkip("${msg.sender} · exact duplicate message, ignored")
                        return@forEachIndexed
                    }
                    val cat = Categorizer.categorize(result.direction, result.merchant, msg.body, rules, msg.sender)
                    Verbose.info("✉ ${msg.sender} · ${fmtDateTime(msg.atMillis)}")
                    result.trace.forEach { Verbose.info("    · $it") }
                    val catNote = cat.pattern?.let { "${cat.source} match \"$it\"" } ?: cat.source
                    Verbose.info("    · category: ${Categories.byId(cat.categoryId).name} ($catNote)")
                    Verbose.ok(
                        "    ✓ recorded ${Money.formatSigned(result.amountMinor, result.currency, result.direction == Direction.EXPENSE)}" +
                            " · confidence ${result.confidence}%"
                    )
                    txns[id] = Txn(
                        id = id,
                        atMillis = msg.atMillis,
                        amountMinor = result.amountMinor,
                        currency = result.currency,
                        direction = result.direction,
                        merchant = result.merchant,
                        sender = msg.sender,
                        body = msg.body,
                        categoryId = cat.categoryId,
                        categorySource = "auto",
                        confidence = result.confidence,
                    )
                }
            }
        }

        onProgress(Progress(messages.size, messages.size))

        if (learned.isNotEmpty()) {
            prefs.senderAllowlist = prefs.senderAllowlist + learned
            Verbose.scan("learned ${learned.size} new bank sender(s): ${learned.joinToString(", ")}")
        }

        val summary = ScanSummary(
            at = System.currentTimeMillis(),
            tookMs = System.currentTimeMillis() - startedAt,
            scanned = messages.size,
            matched = matched,
            parsed = txns.size,
            review = needsReview,
            skipped = skipped,
        )
        store.replaceScanned(txns.values.toList(), reviews, seenSenders, summary)

        Verbose.scan("──────── scan finished in ${"%.1f".format(summary.tookMs / 1000f)}s ────────")
        Verbose.scan(
            "scanned ${summary.scanned} · keyword matches ${summary.matched} · recorded ${summary.parsed}" +
                (if (duplicates > 0) " ($duplicates duplicate(s) ignored)" else "") +
                " · needs review ${summary.review} · skipped ${summary.skipped}"
        )
        if (needsReview > 0) {
            Verbose.scan("→ ${needsReview} message(s) could not be read, they are waiting in the Review tab")
        }
        if (autoDismissed > 0) {
            Verbose.scan(
                "→ $autoDismissed unreadable message(s) auto-dismissed because you dismissed " +
                    "that kind before, restore them in Review"
            )
        }
        Verbose.scan("skipped messages were never stored; only their count was kept")
        Verbose.flush()
        return summary
    }

    /**
     * "BankMuscat", "Bank Dhofar", "بنك نزوى"… plus Omani banks that brand without the
     * word "bank" (NBO, Sohar International, Meethaq, Muzn, Maisarah, Alizz…).
     * Anything not listed here is still picked up by sender learning once one of its
     * messages parses as a real transaction.
     */
    /** "+96891234567", "9123 4567"… anything that's just a phone number. */
    private fun isPhoneNumber(sender: String): Boolean =
        sender.isNotBlank() && sender.all { it.isDigit() || it in "+ -()" }

    private fun looksLikeBank(sender: String): Boolean {
        val s = sender.lowercase().replace(" ", "").replace("-", "").replace("_", "")
        if ("bank" in s || "بنك" in s || "مصرف" in s) return true
        return KNOWN_BANK_BRANDS.any { it in s }
    }

    private fun hashOf(m: RawSms): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${m.sender}|${m.atMillis}|${m.body}".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    private fun fmtDate(millis: Long): String =
        DateTimeFormatter.ofPattern("dd MMM uuuu")
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private fun fmtDateTime(millis: Long): String =
        DateTimeFormatter.ofPattern("dd MMM uuuu h:mm a")
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private companion object {
        const val MAX_SKIP_LINES = 400

        // Omani (and common regional) bank sender brands that don't say "bank".
        val KNOWN_BANK_BRANDS = listOf(
            "nbo",          // National Bank of Oman
            "soharintl", "soharisl", // Sohar International / Islamic
            "meethaq",      // Bank Muscat Islamic
            "muzn",         // NBO Islamic
            "maisarah",     // Bank Dhofar Islamic
            "alizz", "izzbank", // Alizz Islamic
            "ahli",         // Ahli Bank
            "hsbc",
            "oab", "omanarab",  // Oman Arab Bank
            "nizwa",
            "dhofar",
            "muscat",       // BankMuscat variants like "Muscat"
            "cbd", "sib", "qnb", "sbi",
        )
    }
}
