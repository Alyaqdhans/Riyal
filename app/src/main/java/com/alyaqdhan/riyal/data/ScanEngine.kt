package com.alyaqdhan.riyal.data

import androidx.annotation.StringRes
import com.alyaqdhan.riyal.R

import android.content.Context
import com.alyaqdhan.riyal.core.Currencies
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.LogLine
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.core.Verbose
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
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

    /**
     * @param phase what is happening. Reading the messages is only part of a pass: on a
     *   large inbox the stages after it take seconds of their own, and while they ran
     *   the bar sat at 100% looking like a hang.
     */
    data class Progress(
        val processed: Int,
        val total: Int,
        @param:StringRes val phase: Int = R.string.scan_phase_reading,
        @param:StringRes val noun: Int = R.string.scan_noun_messages,
    )

    /** A message that parsed cleanly, held until accounts are known. */
    private data class ParsedMsg(
        val id: String,
        val msg: RawSms,
        val result: SmsParser.Result.Parsed,
    )

    /**
     * @param full read the whole range again rather than only what is new. This is the
     *   reconciliation pass: it is the only thing that notices a bank message deleted
     *   from the inbox, and the only thing that re-parses old records. Forced by the
     *   app when the settings that produced the stored records change, and available to
     *   the user as "Rescan everything".
     */
    suspend fun run(full: Boolean = false, onProgress: (Progress) -> Unit): ScanSummary {
        // Everything below reads what is already stored - the accounts, the rules, the
        // dismissed message kinds - and none of those reads takes the store's lock. On
        // app launch they used to happen while the file was still being read, so the
        // scan decided it was a first run and rediscovered every account, every time.
        store.awaitLoaded()
        val startedAt = System.currentTimeMillis()
        val months = prefs.scanRangeMonths
        val window = if (months <= 0) 0L
        else ZonedDateTime.now().minusMonths(months.toLong()).toInstant().toEpochMilli()
        // Two floors, and the later one wins: a rolling window keeps reaching further
        // back as time passes, which would quietly undo a fresh start.
        val freshStart = prefs.scanSinceMillis
        val since = maxOf(window, freshStart)

        // What the stored records were produced with. They are reused rather than
        // re-derived, so if any of it has changed they are stale by definition.
        val fingerprint = fingerprintOf()
        val plan = ScanWindow.plan(
            full = full,
            sinceMillis = since,
            highWaterMillis = prefs.scanHighWaterMillis,
            storedFingerprint = prefs.scanFingerprint,
            fingerprint = fingerprint,
            overlapMillis = Prefs.SCAN_OVERLAP_MILLIS,
        )
        val readEverything = plan.readEverything
        val from = plan.fromMillis

        Verbose.scan(R.string.scanlog_started)
        Verbose.scan(R.string.scanlog_mode)
        when {
            since == 0L -> Verbose.scan(R.string.scanlog_range_all)
            since == freshStart ->
                Verbose.scan(R.string.scanlog_range_fresh, fmtDate(freshStart))
            else -> Verbose.scan(
                R.string.scanlog_range_months,
                Verbose.count(R.plurals.count_months, months),
                fmtDate(since),
            )
        }
        Verbose.scan(R.string.scanlog_kw_out, prefs.expenseKeywords.joinToString(", "))
        Verbose.scan(R.string.scanlog_kw_in, prefs.incomeKeywords.joinToString(", "))
        val allowlistOn = prefs.senderFilterEnabled
        var allowlist = prefs.senderAllowlist.map { it.lowercase() }.toSet()
        val bankOnly = prefs.bankSendersOnly
        if (allowlistOn) {
            Verbose.scan(R.string.scanlog_allowlist_on, prefs.senderAllowlist.joinToString(", "))
        } else if (bankOnly) {
            Verbose.scan(R.string.scanlog_bank_first)
            if (allowlist.isNotEmpty()) {
                Verbose.scan(
                    R.string.scanlog_approved_senders,
                    prefs.senderAllowlist.joinToString(", "),
                )
            }
        } else {
            Verbose.scan(R.string.scanlog_filters_off)
        }

        when (plan.reason) {
            ScanWindow.Reason.ASKED -> Verbose.scan(R.string.scanlog_reason_asked)
            ScanWindow.Reason.RULES_CHANGED -> Verbose.scan(R.string.scanlog_reason_rules)
            ScanWindow.Reason.NOTHING_READ_YET -> Verbose.scan(R.string.scanlog_reason_first)
            ScanWindow.Reason.ONLY_WHAT_IS_NEW -> Verbose.scan(
                R.string.scanlog_reason_new,
                fmtDateTime(from),
                Prefs.SCAN_OVERLAP_MILLIS / 3_600_000,
            )
        }

        val tQuery0 = System.currentTimeMillis()
        val messages = SmsReader.readInbox(context, from)
        val queryMs = System.currentTimeMillis() - tQuery0
        val seenSenders = messages.mapTo(HashSet()) { it.sender }
        Verbose.scan(
            R.string.scanlog_inbox_query,
            Verbose.count(R.plurals.count_messages, messages.size),
            Verbose.count(R.plurals.count_senders, seenSenders.size),
            queryMs,
        )

        val parser = SmsParser(prefs.expenseKeywords, prefs.incomeKeywords, prefs.defaultCurrency)
        val rules = store.rules.value
        val mutedTemplates = store.muted.value.mapTo(HashSet()) { it.template }
        val neededTemplates = store.needed.value
        if (mutedTemplates.isNotEmpty()) {
            Verbose.scan(
                R.string.scanlog_muted_kinds,
                Verbose.count(R.plurals.count_dismissed_kinds, mutedTemplates.size),
            )
        }
        val parsedMsgs = LinkedHashMap<String, ParsedMsg>()
        // Messages of a kind the user taught are moves between their own accounts.
        val taughtTransfers = HashSet<String>()
        val reviews = ArrayList<ReviewItem>()
        var skipped = 0
        var matched = 0
        var needsReview = 0
        var autoDismissed = 0
        var duplicates = 0
        var skipLinesLogged = 0

        // The same idea as logSkip, for the per-record narration. A record is described
        // in about a dozen lines, so a large inbox wrote a quarter of a million of them
        // into a buffer that keeps four thousand: the app spent seconds building strings
        // it then dropped. Past the cap the records are still made and still counted,
        // they are just no longer each described.
        var recordLinesLogged = 0
        fun logRecord(text: String, kind: LogLine.Kind = LogLine.Kind.INFO) {
            when {
                recordLinesLogged < MAX_RECORD_LINES -> {
                    Verbose.log(kind, text)
                    recordLinesLogged++
                }
                recordLinesLogged == MAX_RECORD_LINES -> {
                    Verbose.scan(R.string.scanlog_more_records)
                    recordLinesLogged++
                }
            }
        }

        fun logSkip(text: String) {
            when {
                skipLinesLogged < MAX_SKIP_LINES -> {
                    Verbose.skip(text)
                    skipLinesLogged++
                }
                skipLinesLogged == MAX_SKIP_LINES -> {
                    Verbose.skip(R.string.scanlog_more_skips)
                    skipLinesLogged++
                }
            }
        }

        // The accounts already known, needed by the sender gate below: a sender that
        // holds one of them is a bank whatever its name looks like.
        val existingAccounts = store.accounts.value

        // An earlier version auto-approved any sender whose message happened to parse,
        // which put every telecom in the allowlist - and an allowlisted sender skips
        // the bank gate. Drop the ones that hold no account and look nothing like a
        // bank, or the gate below can never take effect on this device.
        if (allowlist.isNotEmpty()) {
            val bogus = allowlist.filterNot { name ->
                Banks.looksLikeBank(name) || AccountDiscovery.isKnownSender(existingAccounts, name)
            }
            if (bogus.isNotEmpty()) {
                prefs.senderAllowlist = prefs.senderAllowlist.filterNot { it.lowercase() in bogus }.toSet()
                allowlist = allowlist - bogus.toSet()
                Verbose.scan(
                    R.string.scanlog_removed_bogus,
                    Verbose.count(R.plurals.count_approved_senders, bogus.size),
                    bogus.joinToString(", "),
                )
            }
        }

        // ── stage 1: read every message ───────────────────────────────────
        //
        // Every message this pass looked at, whatever came of it. An incremental scan
        // is only the truth about the messages it actually read, so the store needs to
        // know which those were: a message inside the window that produces nothing this
        // time must lose the record it had, while everything outside the window must
        // keep its own. Left null for a full pass, which is the truth about everything.
        val examined = if (readEverything) null else HashSet<String>()
        val tParse0 = System.currentTimeMillis()
        val excluded = prefs.excludedSenders.mapTo(HashSet()) { it.trim().lowercase() }
        val reads = ArrayList<Read>()
        messages.forEachIndexed { index, msg ->
            if (index % 25 == 0) onProgress(Progress(index, messages.size))
            // Hashed once, here, rather than at each of the three places that used to
            // need it. It also has to happen before any filter below, or a message that
            // a changed sender rule now excludes would keep a record nothing re-read.
            val msgId = hashOf(msg)
            examined?.add(msgId)

            // The user said this sender is not their bank. Nothing it says is read.
            if (msg.sender.trim().lowercase() in excluded) {
                skipped++
                logSkip(Verbose.str(R.string.scanlog_skip_excluded, msg.sender))
                return@forEachIndexed
            }
            // Banks send from named sender IDs ("BankMuscat"), people send from phone
            // numbers. Numeric senders are never read unless explicitly approved.
            if (isPhoneNumber(msg.sender) && msg.sender.lowercase() !in allowlist) {
                skipped++
                logSkip(Verbose.str(R.string.scanlog_skip_numeric, msg.sender))
                return@forEachIndexed
            }
            if (allowlistOn && msg.sender.lowercase() !in allowlist) {
                skipped++
                logSkip(Verbose.str(R.string.scanlog_skip_not_allowlisted, msg.sender))
                return@forEachIndexed
            }
            // Bank gate. Only a bank can move money in a bank account, so a sender
            // that is not one is not read at all - not recorded, and not sent to
            // Review either. Telecoms text constantly about bills, packages and prize
            // draws, every one of them quoting an amount; letting them through is how
            // a TV channel's "cash prizes up to 60,000 OMR" became the biggest expense
            // in the history, and how Review filled with 218 items nobody wants.
            //
            // "Is it a bank" means: a bank-like name, a sender that already holds one
            // of the user's accounts, or one they approved during setup.
            val bankSender = Banks.looksLikeBank(msg.sender) ||
                msg.sender.lowercase() in allowlist ||
                AccountDiscovery.isKnownSender(existingAccounts, msg.sender)
            if (bankOnly && !bankSender) {
                skipped++
                logSkip(Verbose.str(R.string.scanlog_skip_not_bank, msg.sender))
                return@forEachIndexed
            }
            val result = parser.parse(msg.body)
            // Facts that do not depend on the direction - the amount, the balance, the
            // account - for a message whose words named no direction. They are what the
            // balance chain below needs, and what a taught or proven kind is applied to.
            val facts = when (result) {
                is SmsParser.Result.Parsed -> result
                is SmsParser.Result.NeedsReview ->
                    if (result.amountMinor != null) parser.parseAs(msg.body, Direction.EXPENSE) else null
                is SmsParser.Result.Skipped -> null
            }
            reads += Read(
                id = msgId,
                msg = msg,
                result = result,
                facts = facts,
                key = Skeleton.of(msg.sender, msg.body),
                trusted = !bankOnly || bankSender,
            )
        }

        // ── stage 1b: what the user taught, and what the inbox proves ─────
        //
        // The words a message uses are the weakest evidence there is, and they are what
        // every fixed rule was made of. Three stronger things come first: the user's own
        // answer about a kind of message, a balance that moved by exactly the amount,
        // and a kind of message whose balances have proven its direction several times.
        val labels = store.labels.value
        val anchors = if (readEverything) emptyList() else anchorsFor(reads, parser)
        val chains = BalanceChain.chain(
            reads.mapNotNull { it.point() } +
                anchors.mapNotNull { pointOf(it.txn.id, it.txn.sender, it.txn.atMillis, it.facts) }
        )
        val proven = chains.directions
        val templates = TemplateInference.infer(
            reads.map { r ->
                TemplateInference.Observation(
                    key = r.key,
                    sender = r.msg.sender,
                    messageId = r.id,
                    atMillis = r.msg.atMillis,
                    body = r.msg.body,
                    read = (r.result as? SmsParser.Result.Parsed)?.direction,
                    proven = proven[r.id],
                    selfTransfer = (r.result as? SmsParser.Result.Parsed)?.selfTransferTo != null,
                    skipped = r.result is SmsParser.Result.Skipped,
                    hasAmount = r.facts != null,
                )
            }
        )
        if (proven.isNotEmpty()) {
            Verbose.scan(R.string.scanlog_proven, Verbose.count(R.plurals.count_messages, proven.size))
        }
        val provenTemplates = templates.values.count { TemplateInference.isProven(it) }
        if (provenTemplates > 0) Verbose.scan(R.string.scanlog_proven_kinds, provenTemplates)
        if (labels.isNotEmpty()) Verbose.scan(R.string.scanlog_taught_kinds, labels.size)

        // A short pass sees a day or two of messages, too few to prove a template on its
        // own; what earlier passes proved still holds, so it is used when this one could
        // not prove anything itself.
        val earlier = store.templates.value.associateBy { it.key }
        for (r in reads) {
            val msg = r.msg
            val msgId = r.id
            val template = templates[r.key]?.takeIf { TemplateInference.isProven(it) }
                ?: earlier[r.key]?.takeIf { TemplateInference.isProven(it) }
                ?: templates[r.key]
            val result = decide(r, labels[r.key], proven[r.id], template, parser)
            if (labels[r.key]?.kind == MessageKind.TRANSFER && result is SmsParser.Result.Parsed) {
                taughtTransfers += msgId
            }
            when (result) {
                is SmsParser.Result.Skipped -> {
                    skipped++
                    logSkip(Verbose.str(R.string.scanlog_skip_reason, msg.sender, result.reason.text()))
                }

                is SmsParser.Result.NeedsReview -> {
                    val template = MsgTemplate.of(msg.sender, msg.body)
                    if (!r.trusted && template !in neededTemplates) {
                        skipped++
                        logSkip(Verbose.str(R.string.scanlog_skip_unparsable, msg.sender))
                        continue
                    }
                    matched++
                    if (template in mutedTemplates) {
                        autoDismissed++
                        logSkip(Verbose.str(R.string.scanlog_skip_auto_dismissed, msg.sender))
                        reviews += ReviewItem(
                            msgId, msg.atMillis, msg.sender, msg.body, result.reason,
                            state = ReviewItem.STATE_DISMISSED,
                            amountMinor = result.amountMinor,
                            currency = result.currency,
                            suggestedWords = result.suggestedWords,
                        )
                        continue
                    }
                    needsReview++
                    Verbose.fail(
                        R.string.scanlog_could_not_read,
                        msg.sender,
                        fmtDateTime(msg.atMillis),
                        reasonText(result.reason),
                    )
                    result.trace.forEach { Verbose.fail(R.string.scanlog_trace, it.text()) }
                    if (!r.trusted) Verbose.fail(R.string.scanlog_kept_by_history)
                    Verbose.fail(R.string.scanlog_added_to_review)
                    reviews += ReviewItem(
                        msgId, msg.atMillis, msg.sender, msg.body, result.reason,
                        amountMinor = result.amountMinor,
                        currency = result.currency,
                        suggestedWords = result.suggestedWords,
                    )
                }

                is SmsParser.Result.Parsed -> {
                    matched++
                    if (parsedMsgs.containsKey(msgId)) {
                        duplicates++
                        logSkip(Verbose.str(R.string.scanlog_skip_duplicate, msg.sender))
                        continue
                    }
                    parsedMsgs[msgId] = ParsedMsg(msgId, msg, result)
                }
            }
        }

        val parseMs = System.currentTimeMillis() - tParse0
        onProgress(Progress(messages.size, messages.size, R.string.scan_phase_accounts))

        val tAcc0 = System.currentTimeMillis()
        // ── stage 2: accounts ─────────────────────────────────────────────
        // Discovery runs on every scan, not just the first: a bank that has never
        // texted before owns no account yet, and after a fresh start that is every
        // bank you have. Accounts already known are left exactly as they are.
        var accounts = existingAccounts
        // Every parsed message, and the stored records read back beside them, each with
        // the balance line it is on: what decides which digits are an account.
        val observations = parsedMsgs.values.map { pm ->
            observationOf(pm.msg.sender, pm.msg.atMillis, pm.result, pm.result.direction, chains.lineOf[pm.id])
        } + anchors.map { a ->
            observationOf(
                a.txn.sender, a.txn.atMillis, a.facts,
                if (a.txn.type == TxnType.INCOME) Direction.INCOME else Direction.EXPENSE,
                chains.lineOf[a.txn.id], a.txn.accountId,
            )
        }
        // Accounts an earlier reading made out of a reference or the other party's
        // number. A pass over everything is the one that can see every message quoting
        // them sit on another account's balance line, and fold them back into it.
        if (readEverything && accounts.isNotEmpty() && observations.isNotEmpty()) {
            val foreign = AccountDiscovery.foreignAccounts(accounts, observations)
            for ((fromId, intoId) in foreign) {
                val from = accounts.first { it.id == fromId }
                val into = accounts.first { it.id == intoId }
                store.mergeAccount(fromId, intoId)
                Verbose.ok(R.string.scanlog_account_folded, from.displayName, into.displayName)
            }
            if (foreign.isNotEmpty()) accounts = store.accounts.value
            val renumbered = AccountDiscovery.renumbered(accounts, observations)
            for ((id, last4) in renumbered) {
                val account = accounts.first { it.id == id }
                store.renumberAccount(id, last4)
                Verbose.ok(R.string.scanlog_account_renumbered, account.displayName, last4)
            }
            if (renumbered.isNotEmpty()) accounts = store.accounts.value
        }
        if (parsedMsgs.isNotEmpty()) {
            val found = AccountDiscovery.proposeMissing(accounts, observations)
            if (found.isNotEmpty()) {
                val firstRun = accounts.isEmpty()
                accounts = accounts + found
                store.replaceAccounts(accounts)
                Verbose.scan(
                    if (firstRun) R.string.scanlog_accounts_found
                    else R.string.scanlog_new_accounts
                )
                found.forEach { a ->
                    val tail = a.last4?.let { Verbose.str(R.string.scanlog_account_tail, it) }.orEmpty()
                    if (a.needsBalance) {
                        Verbose.ok(
                            R.string.scanlog_account_needs_balance,
                            a.displayName,
                            a.currency,
                            tail,
                        )
                    } else {
                        Verbose.ok(
                            R.string.scanlog_account_opening,
                            a.displayName,
                            a.currency,
                            tail,
                            Money.format(a.openingBalanceMinor, a.currency),
                            fmtDateTime(a.openingAtMillis),
                        )
                    }
                }
                Verbose.scan(
                    if (firstRun) R.string.scanlog_check_on_home else R.string.scanlog_new_bank
                )
            }
        }

        val accMs = System.currentTimeMillis() - tAcc0
        val tRec0 = System.currentTimeMillis()
        onProgress(
            Progress(0, parsedMsgs.size, R.string.scan_phase_building, R.string.scan_noun_records)
        )
        // ── stage 3: build records, routed to an account ──────────────────
        val txns = ArrayList<Txn>(parsedMsgs.size)
        val hinted = HashSet<String>()
        // txnId -> the bank's own transaction time, the one thing that proves two
        // messages describe a single movement of money.
        val bankStamps = HashMap<String, String>()
        var unrouted = 0
        var skippedNonBank = 0
        var built = 0
        // Which account each balance line is, so a message quoting a number no account
        // answers to still lands where its balance says it belongs.
        val lineAccounts = AccountDiscovery.lineAccounts(accounts, observations)
        for (pm in parsedMsgs.values) {
            if (built % 200 == 0) {
                onProgress(
                    Progress(
                        built,
                        parsedMsgs.size,
                        R.string.scan_phase_building,
                        R.string.scan_noun_records,
                    )
                )
            }
            built++
            val result = pm.result
            // A sender that owns none of the user's accounts cannot have moved their
            // money, whatever its text says. Oman TV offering "cash prizes up to 60,000
            // OMR" was the single biggest expense in the whole history.
            if (accounts.isNotEmpty() && !AccountDiscovery.isKnownSender(accounts, pm.msg.sender)) {
                logRecord(
                    Verbose.str(
                        R.string.scanlog_msg_header,
                        pm.msg.sender,
                        fmtDateTime(pm.msg.atMillis),
                    )
                )
                logRecord(Verbose.str(R.string.scanlog_not_your_bank))
                skippedNonBank++
                continue
            }
            val accountId = result.accountTail?.takeUnless { result.tailIsCard }
                ?.let { AccountDiscovery.ownerOf(accounts, pm.msg.sender, it) }
                ?: chains.lineOf[pm.id]?.let { lineAccounts[it] }
                ?: AccountDiscovery.routeTo(accounts, pm.msg.sender, result.accountTail, result.tailIsCard)
            if (accountId == null) unrouted++
            if (result.transferHint) hinted += pm.id
            result.bankStamp?.let { bankStamps[pm.id] = pm.msg.sender.trim().lowercase() + "|" + it }
            // A message naming both ends is already a whole transfer: recording it as
            // a plain debit counts money you moved to yourself as money you spent.
            val selfTo = result.selfTransferTo?.let { tail ->
                AccountDiscovery.routeTo(accounts, pm.msg.sender, tail)
            }?.takeIf { it != accountId }
            val cat = Categorizer.categorize(result.direction, result.merchant, pm.msg.body, rules, pm.msg.sender)
            val taughtTransfer = pm.id in taughtTransfers
            val type = if (selfTo != null || taughtTransfer) TxnType.TRANSFER else TxnType.of(result.direction)
            // A taught transfer names only the account that texted; money arriving puts it
            // at the receiving end, money leaving at the sending one.
            val arriving = taughtTransfer && selfTo == null && result.direction == Direction.INCOME
            // Money spent abroad is counted in the currency the account holds, so it is
            // in the month's totals and moves the balance. What the bank charged stays on
            // the record beside it.
            val homeCurrency = accounts.firstOrNull { it.id == accountId }?.currency ?: prefs.defaultCurrency
            val converted = if (result.currency != homeCurrency) {
                Currencies.convert(result.amountMinor, result.currency, homeCurrency)
            } else {
                null
            }
            logRecord(
                Verbose.str(
                    R.string.scanlog_msg_header,
                    pm.msg.sender,
                    fmtDateTime(pm.msg.atMillis),
                )
            )
            result.trace.forEach { logRecord(Verbose.str(R.string.scanlog_trace, it.text())) }
            if (converted != null) {
                logRecord(
                    Verbose.str(
                        R.string.scanlog_converted,
                        Money.format(result.amountMinor, result.currency),
                        Money.format(converted, homeCurrency),
                    )
                )
            }
            logRecord(
                Verbose.str(
                    R.string.scanlog_category,
                    Categories.byId(cat.categoryId).name,
                    cat.source.text(),
                )
            )
            logRecord(
                Verbose.str(
                    R.string.scanlog_account,
                    accounts.firstOrNull { it.id == accountId }?.displayName
                        ?: Verbose.str(R.string.scanlog_account_unmatched),
                )
            )
            if (type == TxnType.TRANSFER) {
                logRecord(
                    Verbose.str(
                        R.string.scanlog_recorded_transfer,
                        Money.format(converted ?: result.amountMinor, homeCurrency.takeIf { converted != null } ?: result.currency),
                    ),
                    LogLine.Kind.OK,
                )
            } else {
                logRecord(
                    Verbose.str(
                        R.string.scanlog_recorded,
                        Money.formatSigned(
                            converted ?: result.amountMinor,
                            homeCurrency.takeIf { converted != null } ?: result.currency,
                            type == TxnType.EXPENSE,
                        ),
                        result.confidence,
                    ),
                    LogLine.Kind.OK,
                )
            }
            txns += Txn(
                id = pm.id,
                atMillis = pm.msg.atMillis,
                amountMinor = converted ?: result.amountMinor,
                currency = if (converted != null) homeCurrency else result.currency,
                type = type,
                fromAccountId = when {
                    type == TxnType.INCOME || arriving -> null
                    else -> accountId
                },
                toAccountId = when (type) {
                    TxnType.TRANSFER -> selfTo ?: accountId.takeIf { arriving }
                    TxnType.INCOME -> accountId
                    else -> null
                },
                merchant = result.merchant,
                sender = pm.msg.sender,
                body = pm.msg.body,
                categoryId = if (type == TxnType.TRANSFER) Categories.TRANSFER_ID else cat.categoryId,
                categorySource = "auto",
                confidence = result.confidence,
                originalAmountMinor = result.amountMinor.takeIf { converted != null },
                originalCurrency = result.currency.takeIf { converted != null },
            )
        }

        val recMs = System.currentTimeMillis() - tRec0
        val tTr0 = System.currentTimeMillis()
        onProgress(
            Progress(
                txns.size,
                txns.size,
                R.string.scan_phase_transfers,
                R.string.scan_noun_records,
            )
        )
        // ── stage 4: nominate transfers ───────────────────────────────────
        // Accounts go in so the matcher can tell a same-bank move (instant, 15 min)
        // from a bank-to-bank one, where the receiving bank texts once it settles.
        val proposals = TransferMatcher.propose(
            txns,
            hintedIds = hinted,
            accounts = accounts,
            bankStamps = bankStamps,
        )
        if (proposals.isNotEmpty()) {
            Verbose.scan(R.string.scanlog_transfers_header)
            proposals.forEach { p ->
                Verbose.scan(
                    R.string.scanlog_transfer_pair,
                    Money.format(p.amountMinor, p.currency),
                    accountName(accounts, p.fromAccountId),
                    accountName(accounts, p.toAccountId),
                    fmtDateTime(p.atMillis),
                )
            }
            Verbose.scan(
                if (prefs.autoConfirmTransfers) R.string.scanlog_transfers_auto
                else R.string.scanlog_transfers_manual
            )
        }

        val trMs = System.currentTimeMillis() - tTr0
        val summary = ScanSummary(
            at = System.currentTimeMillis(),
            // Filled in below, once the saving is done: it used to stop the clock before
            // the store was written, which on a quiet scan was most of the time spent.
            tookMs = 0L,
            scanned = messages.size,
            matched = matched,
            parsed = txns.size,
            review = needsReview,
            skipped = skipped,
            transfers = proposals.size,
        )
        onProgress(
            Progress(txns.size, txns.size, R.string.scan_phase_saving, R.string.scan_noun_records)
        )
        val tStore0 = System.currentTimeMillis()
        store.applyScan(
            txns, proposals, reviews, seenSenders,
            summary.copy(tookMs = System.currentTimeMillis() - startedAt),
            examinedIds = examined,
            templates = templates.values.toList(),
        )
        val storeMs = System.currentTimeMillis() - tStore0
        val took = System.currentTimeMillis() - startedAt

        // The high-water mark moves only after the store has taken the work. A scan that
        // threw on the way here leaves it where it was, so the next one reads the same
        // messages again rather than stepping over them.
        prefs.scanHighWaterMillis =
            maxOf(prefs.scanHighWaterMillis, messages.maxOfOrNull { it.atMillis } ?: 0L)
        prefs.scanFingerprint = fingerprint

        Verbose.scan(R.string.scanlog_finished, String.format(Locale.ROOT, "%.1f", took / 1000f))
        // Where the time went, because "the scan is slow" is not something anyone can
        // act on and these three numbers are.
        Verbose.scan(R.string.scanlog_time, queryMs, parseMs, storeMs)
        Verbose.scan(
            R.string.scanlog_summary,
            summary.scanned,
            summary.matched,
            summary.parsed,
            if (duplicates > 0) {
                Verbose.str(
                    R.string.scanlog_summary_dupes,
                    Verbose.count(R.plurals.count_duplicates, duplicates),
                )
            } else {
                ""
            },
            summary.review,
            summary.skipped,
        )
        if (needsReview > 0) {
            Verbose.scan(
                R.string.scanlog_needs_review,
                Verbose.count(R.plurals.count_messages, needsReview),
            )
        }
        if (skippedNonBank > 0) {
            Verbose.scan(
                R.string.scanlog_non_bank,
                Verbose.count(R.plurals.count_messages, skippedNonBank),
            )
        }
        if (unrouted > 0) {
            Verbose.scan(
                R.string.scanlog_unrouted,
                Verbose.count(R.plurals.count_records, unrouted),
            )
        }
        if (autoDismissed > 0) {
            Verbose.scan(
                R.string.scanlog_auto_dismissed,
                Verbose.count(R.plurals.home_unreadable_messages, autoDismissed),
            )
        }
        Verbose.scan(R.string.scanlog_skipped_note)
        Verbose.flush()
        return summary.copy(tookMs = took)
    }

    /**
     * Everything that decides what a stored record would say if it were parsed again.
     *
     * Records survive between scans now, so they carry the settings that made them. Any
     * change here means every stored record could now read differently, and the only
     * honest response is to read the inbox again. Deliberately not a hash: it is written
     * to preferences and read back, and when something goes wrong with scanning this is
     * the string worth being able to look at.
     */
    private fun fingerprintOf(): String = listOf(
        "v${SmsParser.VERSION}",
        prefs.expenseKeywords.sorted().joinToString(","),
        prefs.incomeKeywords.sorted().joinToString(","),
        prefs.defaultCurrency,
        "range=${prefs.scanRangeMonths}",
        "fresh=${prefs.scanSinceMillis}",
        "bankOnly=${prefs.bankSendersOnly}",
        "allowOnly=${prefs.senderFilterEnabled}",
        prefs.senderAllowlist.map { it.lowercase() }.sorted().joinToString(","),
        // A corrected exchange rate changes what every converted record is worth.
        "fx=" + Currencies.fingerprint(),
        "skeleton=${Skeleton.VERSION}",
        "accounts=${AccountDiscovery.VERSION}",
        "excluded=" + prefs.excludedSenders.map { it.lowercase() }.sorted().joinToString(","),
        // What the user taught. Hashed, because there can be hundreds of templates.
        "taught=" + store.labels.value.entries.sortedBy { it.key }
            .joinToString(",") { it.key + "=" + it.value.kind }.hashCode().toString(16),
    ).joinToString("|")

    /** One message the reader got to, with what is needed to decide what it was. */
    private data class Read(
        val id: String,
        val msg: RawSms,
        val result: SmsParser.Result,
        /** Amount, balance and account, whichever way the money went; null with no amount. */
        val facts: SmsParser.Result.Parsed?,
        val key: String,
        val trusted: Boolean,
    ) {
        /** This message as a point on its account's balance line, if it quotes a balance. */
        fun point(): BalanceChain.Point? = facts?.let { pointOf(id, msg.sender, msg.atMillis, it) }
    }

    /**
     * A stored record read back for its balance. A pass that reads only what is new sees
     * an account's latest message without the one before it, and that one is what puts
     * the new message on its account's balance line.
     */
    private data class Anchor(val txn: Txn, val facts: SmsParser.Result.Parsed)

    /** The newest stored records of every bank this pass heard from, read back. */
    private fun anchorsFor(reads: List<Read>, parser: SmsParser): List<Anchor> {
        val senders = reads.mapTo(HashSet()) { it.msg.sender.trim().lowercase() }
        val readIds = reads.mapTo(HashSet()) { it.id }
        return store.txns.value
            .filter { t ->
                !t.manual && t.legIds.isEmpty() && t.id !in readIds &&
                    t.sender.trim().lowercase() in senders
            }
            .groupBy { it.sender.trim().lowercase() }
            .flatMap { (_, list) -> list.sortedByDescending { it.atMillis }.take(ANCHORS_PER_SENDER) }
            .mapNotNull { t ->
                val direction = if (t.type == TxnType.INCOME) Direction.INCOME else Direction.EXPENSE
                parser.parseAs(t.body, direction)?.let { Anchor(t, it) }
            }
    }

    private fun observationOf(
        sender: String,
        at: Long,
        f: SmsParser.Result.Parsed,
        direction: Direction,
        line: Int?,
        accountId: String? = null,
    ) = AccountDiscovery.Observation(
        sender = sender,
        accountTail = f.accountTail,
        // The balance says what the account holds; a purchase abroad says only what the
        // shop charged.
        currency = f.balanceCurrency ?: f.currency,
        balanceMinor = f.balanceMinor,
        atMillis = at,
        signedMinor = if (direction == Direction.EXPENSE) -f.amountMinor else f.amountMinor,
        tailIsCard = f.tailIsCard,
        line = line,
        accountId = accountId,
        head = f.accountHead?.takeUnless { f.tailIsCard },
    )

    /**
     * What one message was, strongest evidence first: what the user taught about its
     * kind, then what its own balance proves, then what its kind's balances have proven,
     * and only then what its words say.
     */
    private fun decide(
        r: Read,
        label: TemplateLabel?,
        proven: Direction?,
        template: TemplateStat?,
        parser: SmsParser,
    ): SmsParser.Result {
        val read = r.result
        if (label != null) {
            val direction = when (label.kind) {
                MessageKind.NONE -> return SmsParser.Result.Skipped(ParseNote(R.string.skip_taught_none))
                MessageKind.OUT -> Direction.EXPENSE
                MessageKind.IN -> Direction.INCOME
                // A transfer has no direction of its own: which end of it this account
                // is comes from the balance, or the words, or failing both a debit.
                MessageKind.TRANSFER ->
                    proven ?: (read as? SmsParser.Result.Parsed)?.direction ?: Direction.EXPENSE
            }
            return reread(r, direction, parser, ParseNote(R.string.trace_taught, listOf(ParseNote(kindWord(label.kind)))))
        }
        if (proven != null) {
            return reread(r, proven, parser, ParseNote(R.string.trace_proven, listOf(ParseNote(kindWord(MessageKind.of(proven))))))
        }
        val spread = template?.takeIf { TemplateInference.isProven(it) }?.guess?.direction
        if (spread != null) {
            return reread(r, spread, parser, ParseNote(R.string.trace_kind_proven, listOf(ParseNote(kindWord(MessageKind.of(spread))))))
        }
        return read
    }

    /** [r] read as [direction], with [why] at the end of its trace. */
    private fun reread(r: Read, direction: Direction, parser: SmsParser, why: ParseNote): SmsParser.Result {
        val read = r.result
        if (read is SmsParser.Result.Parsed && read.direction == direction) {
            return read.copy(trace = read.trace + why, confidence = 100)
        }
        // A message with no amount stays what it was: knowing which way money went does
        // not say how much.
        val again = parser.parseAs(r.msg.body, direction) ?: return read
        if (read is SmsParser.Result.Parsed) {
            Verbose.info(R.string.scanlog_overruled, r.msg.sender, Verbose.str(kindWord(MessageKind.of(read.direction))))
        }
        return again.copy(trace = again.trace + why, confidence = 100)
    }

    private fun kindWord(kind: MessageKind): Int = when (kind) {
        MessageKind.OUT -> R.string.log_word_out
        MessageKind.IN -> R.string.log_word_in
        MessageKind.TRANSFER -> R.string.label_transfer
        MessageKind.NONE -> R.string.kind_none
    }

    private fun accountName(accounts: List<Account>, id: String?): String =
        accounts.firstOrNull { it.id == id }?.displayName
            ?: Verbose.str(R.string.review_unassigned_account)

    /** "+96891234567", "9123 4567"… anything that's just a phone number. */
    private fun isPhoneNumber(sender: String): Boolean =
        sender.isNotBlank() && sender.all { it.isDigit() || it in "+ -()" }

    /**
     * The identity of a message: same sender, same instant, same text, same record.
     *
     * Called once for every message in the inbox, so what it allocates matters. It used
     * to build a fresh SHA-256 instance per message and then run String.format sixteen
     * times to render the hex; both are gone. The digest is thread-local because
     * MessageDigest is not thread-safe and this is called from the scan's own thread.
     */
    private fun hashOf(m: RawSms): String {
        val md = digest.get()!!
        md.reset()
        md.update(m.sender.toByteArray())
        md.update(SEPARATOR)
        md.update(m.atMillis.toString().toByteArray())
        md.update(SEPARATOR)
        md.update(m.body.toByteArray())
        val bytes = md.digest()
        val out = CharArray(16)
        for (i in 0 until 8) {
            val b = bytes[i].toInt() and 0xff
            out[i * 2] = HEX[b ushr 4]
            out[i * 2 + 1] = HEX[b and 0x0f]
        }
        return String(out)
    }

    // Parsing a date pattern is not free, and these were re-parsed on every call - which
    // in stage 3 is once per record.
    private fun fmtDate(millis: Long): String =
        DATE_FMT.format(Instant.ofEpochMilli(millis).atZone(ZONE))

    private fun fmtDateTime(millis: Long): String =
        DATE_TIME_FMT.format(Instant.ofEpochMilli(millis).atZone(ZONE))

    private companion object {
        const val MAX_SKIP_LINES = 400

        /** How many lines of per-record narration a single pass may write. */
        const val MAX_RECORD_LINES = 1200

        /**
         * Stored records read back per bank on a pass that reads only what is new: enough
         * to reach the last message before the new ones, and to see a number come up
         * again, without re-reading a whole history on every launch.
         */
        const val ANCHORS_PER_SENDER = 40

        fun pointOf(id: String, sender: String, at: Long, f: SmsParser.Result.Parsed): BalanceChain.Point? {
            val balance = f.balanceMinor ?: return null
            return BalanceChain.Point(
                id = id,
                sender = sender,
                tail = f.accountTail?.takeUnless { f.tailIsCard },
                atMillis = at,
                amountMinor = f.amountMinor,
                currency = f.currency,
                balanceMinor = balance,
                balanceCurrency = f.balanceCurrency ?: f.currency,
            )
        }

        val DATE_FMT: DateTimeFormatter get() = Dates.pattern("dd MMM uuuu")
        val DATE_TIME_FMT: DateTimeFormatter get() = Dates.pattern("dd MMM uuuu h:mm a")
        val ZONE: ZoneId = ZoneId.systemDefault()

        val HEX = "0123456789abcdef".toCharArray()
        val SEPARATOR = "|".toByteArray()
        val digest: ThreadLocal<MessageDigest> =
            ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
    }
}
