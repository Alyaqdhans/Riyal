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
 *
 * The pass is in two stages, because a record cannot name its account until the
 * accounts exist: everything is parsed first, then accounts are discovered from those
 * parses (on a first run), then each record is routed to one and paired transfers are
 * nominated.
 */
class ScanEngine(
    private val context: Context,
    private val prefs: Prefs,
    private val store: Store,
) {

    data class Progress(val processed: Int, val total: Int)

    /** A message that parsed cleanly, held until accounts are known. */
    private data class ParsedMsg(
        val id: String,
        val msg: RawSms,
        val result: SmsParser.Result.Parsed,
    )

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
        val parsedMsgs = LinkedHashMap<String, ParsedMsg>()
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

        // ── stage 1: read every message ───────────────────────────────────
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
            val trustedSender = !bankOnly || allowlistOn || Banks.looksLikeBank(msg.sender) ||
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
                    if (parsedMsgs.containsKey(id)) {
                        duplicates++
                        logSkip("${msg.sender} · exact duplicate message, ignored")
                        return@forEachIndexed
                    }
                    parsedMsgs[id] = ParsedMsg(id, msg, result)
                }
            }
        }

        onProgress(Progress(messages.size, messages.size))

        if (learned.isNotEmpty()) {
            prefs.senderAllowlist = prefs.senderAllowlist + learned
            Verbose.scan("learned ${learned.size} new bank sender(s): ${learned.joinToString(", ")}")
        }

        // ── stage 2: accounts ─────────────────────────────────────────────
        var accounts = store.accounts.value
        if (accounts.isEmpty() && parsedMsgs.isNotEmpty()) {
            accounts = AccountDiscovery.propose(
                parsedMsgs.values.map { pm ->
                    AccountDiscovery.Observation(
                        sender = pm.msg.sender,
                        accountTail = pm.result.accountTail,
                        currency = pm.result.currency,
                        balanceMinor = pm.result.balanceMinor,
                        atMillis = pm.msg.atMillis,
                        signedMinor = if (pm.result.direction == Direction.EXPENSE) {
                            -pm.result.amountMinor
                        } else {
                            pm.result.amountMinor
                        },
                    )
                }
            )
            if (accounts.isNotEmpty()) {
                store.replaceAccounts(accounts)
                Verbose.scan("──────── accounts found ────────")
                accounts.forEach { a ->
                    Verbose.ok(
                        "✦ ${a.displayName} (${a.currency})" +
                            (a.last4?.let { " ···$it" } ?: "") + " · " +
                            if (a.needsBalance) {
                                "no balance quoted in any message, please enter it"
                            } else {
                                "opening balance ${Money.format(a.openingBalanceMinor, a.currency)} " +
                                    "as of ${fmtDateTime(a.openingAtMillis)}"
                            }
                    )
                }
                Verbose.scan("→ check these on Home before trusting the balances")
            }
        }

        // ── stage 3: build records, routed to an account ──────────────────
        val txns = ArrayList<Txn>(parsedMsgs.size)
        val hinted = HashSet<String>()
        var unrouted = 0
        for (pm in parsedMsgs.values) {
            val result = pm.result
            val accountId = AccountDiscovery.routeTo(accounts, pm.msg.sender, result.accountTail)
            if (accountId == null) unrouted++
            if (result.transferHint) hinted += pm.id
            val cat = Categorizer.categorize(result.direction, result.merchant, pm.msg.body, rules, pm.msg.sender)
            val type = TxnType.of(result.direction)
            Verbose.info("✉ ${pm.msg.sender} · ${fmtDateTime(pm.msg.atMillis)}")
            result.trace.forEach { Verbose.info("    · $it") }
            val catNote = cat.pattern?.let { "${cat.source} match \"$it\"" } ?: cat.source
            Verbose.info("    · category: ${Categories.byId(cat.categoryId).name} ($catNote)")
            Verbose.info(
                "    · account: " + (accounts.firstOrNull { it.id == accountId }?.displayName
                    ?: "not matched, assign it from the transaction row")
            )
            Verbose.ok(
                "    ✓ recorded ${Money.formatSigned(result.amountMinor, result.currency, type == TxnType.EXPENSE)}" +
                    " · confidence ${result.confidence}%"
            )
            txns += Txn(
                id = pm.id,
                atMillis = pm.msg.atMillis,
                amountMinor = result.amountMinor,
                currency = result.currency,
                type = type,
                fromAccountId = if (type == TxnType.EXPENSE) accountId else null,
                toAccountId = if (type == TxnType.INCOME) accountId else null,
                merchant = result.merchant,
                sender = pm.msg.sender,
                body = pm.msg.body,
                categoryId = cat.categoryId,
                categorySource = "auto",
                confidence = result.confidence,
            )
        }

        // ── stage 4: nominate transfers ───────────────────────────────────
        // Accounts go in so the matcher can tell a same-bank move (instant, 15 min)
        // from a bank-to-bank one, where the receiving bank texts once it settles.
        val proposals = TransferMatcher.propose(txns, hintedIds = hinted, accounts = accounts)
        if (proposals.isNotEmpty()) {
            Verbose.scan("──────── possible transfers ────────")
            proposals.forEach { p ->
                Verbose.scan(
                    "⇄ ${Money.format(p.amountMinor, p.currency)} left " +
                        "${accountName(accounts, p.fromAccountId)} and arrived in " +
                        "${accountName(accounts, p.toAccountId)} around ${fmtDateTime(p.atMillis)}"
                )
            }
            Verbose.scan(
                if (prefs.autoConfirmTransfers) {
                    "→ each pair is merged into one transfer straight away and stops counting " +
                        "as spending and income; open a row in Activity to split it back apart"
                } else {
                    "→ nothing was merged: confirm each one in Review, and only then does it " +
                        "stop counting as spending and income"
                }
            )
        }

        val summary = ScanSummary(
            at = System.currentTimeMillis(),
            tookMs = System.currentTimeMillis() - startedAt,
            scanned = messages.size,
            matched = matched,
            parsed = txns.size,
            review = needsReview,
            skipped = skipped,
            transfers = proposals.size,
        )
        store.replaceScanned(txns, proposals, reviews, seenSenders, summary)

        Verbose.scan("──────── scan finished in ${"%.1f".format(summary.tookMs / 1000f)}s ────────")
        Verbose.scan(
            "scanned ${summary.scanned} · keyword matches ${summary.matched} · recorded ${summary.parsed}" +
                (if (duplicates > 0) " ($duplicates duplicate(s) ignored)" else "") +
                " · needs review ${summary.review} · skipped ${summary.skipped}"
        )
        if (needsReview > 0) {
            Verbose.scan("→ ${needsReview} message(s) could not be read, they are waiting in the Review tab")
        }
        if (unrouted > 0) {
            Verbose.scan(
                "→ $unrouted record(s) couldn't be matched to an account (the bank didn't quote " +
                    "one); assign them by tapping the row in Activity"
            )
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

    private fun accountName(accounts: List<Account>, id: String?): String =
        accounts.firstOrNull { it.id == id }?.displayName ?: "an unassigned account"

    /** "+96891234567", "9123 4567"… anything that's just a phone number. */
    private fun isPhoneNumber(sender: String): Boolean =
        sender.isNotBlank() && sender.all { it.isDigit() || it in "+ -()" }

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
    }
}
