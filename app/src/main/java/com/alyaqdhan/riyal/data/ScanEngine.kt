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
 * what was queried, which messages matched, how each one was parsed, and — loudly —
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
        Verbose.scan("mode: manual one-shot — this app has no background receiver")
        Verbose.scan("range: " + if (since == 0L) "entire inbox" else "last $months month(s), since ${fmtDate(since)}")
        Verbose.scan("expense keywords: ${prefs.expenseKeywords.joinToString(", ")}")
        Verbose.scan("income keywords: ${prefs.incomeKeywords.joinToString(", ")}")
        val allowlistOn = prefs.senderFilterEnabled
        val allowlist = prefs.senderAllowlist.map { it.lowercase() }.toSet()
        if (allowlistOn) {
            Verbose.scan("sender allowlist ON: only ${prefs.senderAllowlist.joinToString(", ")}")
        } else {
            Verbose.scan("sender allowlist OFF: every sender is considered (bodies are still keyword-gated)")
        }

        val messages = SmsReader.readInbox(context, since)
        val seenSenders = messages.mapTo(HashSet()) { it.sender }
        Verbose.scan("inbox query returned ${messages.size} message(s) from ${seenSenders.size} sender(s)")

        val parser = SmsParser(prefs.expenseKeywords, prefs.incomeKeywords, prefs.defaultCurrency)
        val rules = store.rules.value
        val txns = LinkedHashMap<String, Txn>()
        val reviews = ArrayList<ReviewItem>()
        var skipped = 0
        var matched = 0
        var needsReview = 0
        var duplicates = 0
        var skipLinesLogged = 0

        fun logSkip(text: String) {
            when {
                skipLinesLogged < MAX_SKIP_LINES -> {
                    Verbose.skip(text)
                    skipLinesLogged++
                }
                skipLinesLogged == MAX_SKIP_LINES -> {
                    Verbose.skip("…more skipped messages — muting further skip lines (counts still tallied)")
                    skipLinesLogged++
                }
            }
        }

        messages.forEachIndexed { index, msg ->
            if (index % 25 == 0) onProgress(Progress(index, messages.size))

            if (allowlistOn && msg.sender.lowercase() !in allowlist) {
                skipped++
                logSkip("${msg.sender} · skipped (sender not in your allowlist)")
                return@forEachIndexed
            }

            when (val result = parser.parse(msg.body)) {
                is SmsParser.Result.Skipped -> {
                    skipped++
                    logSkip("${msg.sender} · skipped (${result.reason}) — content not processed")
                }

                is SmsParser.Result.NeedsReview -> {
                    matched++
                    needsReview++
                    Verbose.fail("✉ ${msg.sender} · ${fmtDateTime(msg.atMillis)} → COULD NOT READ: ${result.reason}")
                    result.trace.forEach { Verbose.fail("    · $it") }
                    Verbose.fail("    → added to Review so you decide what it was")
                    reviews += ReviewItem(hashOf(msg), msg.atMillis, msg.sender, msg.body, result.reason)
                }

                is SmsParser.Result.Parsed -> {
                    matched++
                    val id = hashOf(msg)
                    if (txns.containsKey(id)) {
                        duplicates++
                        logSkip("${msg.sender} · exact duplicate message, ignored")
                        return@forEachIndexed
                    }
                    val cat = Categorizer.categorize(result.direction, result.merchant, msg.body, rules)
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
            Verbose.scan("→ ${needsReview} message(s) could not be read — they are waiting in the Review tab")
        }
        Verbose.scan("skipped messages were never stored; only their count was kept")
        Verbose.flush()
        return summary
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
        DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm")
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private companion object {
        const val MAX_SKIP_LINES = 400
    }
}
