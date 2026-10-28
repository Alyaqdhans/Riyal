package com.alyaqdhan.riyal.data

/**
 * What a sender id tells us about a bank. Kept apart from [ScanEngine] because two
 * different jobs need it: the scanner, to decide whether an unreadable message is worth
 * showing you, and [AccountDiscovery], to put a real bank name on a proposed account.
 */
object Banks {

    /**
     * Omani (and common regional) bank sender brands that don't say "bank", mapped to
     * how the bank actually writes its own name. Anything not listed is still picked up
     * by sender learning once one of its messages parses as a real transaction.
     */
    private val BRANDS = linkedMapOf(
        "bankmuscat" to "Bank Muscat",
        "meethaq" to "Meethaq Islamic",
        "nbo" to "National Bank of Oman",
        "muzn" to "Muzn Islamic",
        "soharintl" to "Sohar International",
        "soharisl" to "Sohar Islamic",
        "dhofar" to "Bank Dhofar",
        "maisarah" to "Maisarah Islamic",
        "alizz" to "Alizz Islamic Bank",
        "izzbank" to "Alizz Islamic Bank",
        "nizwa" to "Bank Nizwa",
        "omanarab" to "Oman Arab Bank",
        "oab" to "Oman Arab Bank",
        "ahli" to "Ahli Bank",
        "hsbc" to "HSBC",
        "qnb" to "QNB",
        "cbd" to "Commercial Bank of Dubai",
        "sib" to "Sharjah Islamic Bank",
        "sbi" to "State Bank of India",
        "muscat" to "Bank Muscat",
    )

    private fun squash(sender: String) =
        sender.lowercase().replace(" ", "").replace("-", "").replace("_", "")

    // Compiled once. looksLikeBank asks matches() about every brand it knows, and a
    // scan asks that about every message, so a Regex built per call was compiled
    // thousands of times over an inbox.
    private val CAMEL_BREAK = Regex("(?<=[a-z])(?=[A-Z])")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val RUN_OF_SPACE = Regex("\\s+")

    /** The sender split into words, with camelCase treated as a word break. */
    private fun words(sender: String): List<String> =
        sender.replace(CAMEL_BREAK, " ")
            .lowercase()
            .split(NON_WORD)
            .filter { it.isNotEmpty() }

    /**
     * Whether [key] names this sender's bank. Short keys are acronyms ("nbo", "sib",
     * "oab") and must be a whole word: as substrings they fire inside ordinary names -
     * "Makasib" contains "sib" - and mislabel an account with the wrong bank entirely.
     */
    private fun matches(key: String, sender: String): Boolean =
        if (key.length <= 4) key in words(sender) else key in squash(sender)

    /** Every bank this list can name, in the order a picker shows them. */
    val KNOWN: List<String> get() = BRANDS.values.distinct().sorted()

    /** "BankMuscat", "Bank Dhofar", "بنك نزوى"… plus brands that omit the word "bank". */
    fun looksLikeBank(sender: String): Boolean {
        val s = squash(sender)
        if ("bank" in s || "بنك" in s || "مصرف" in s) return true
        return BRANDS.keys.any { matches(it, sender) }
    }

    /**
     * The bank's own name for a sender id, for display on an account. Falls back to the
     * sender itself with camel-case split ("BankMuscat" → "Bank Muscat"), which reads
     * far better than the raw id and is still recognisably what the message came from.
     */
    fun displayName(sender: String): String {
        BRANDS.entries.firstOrNull { matches(it.key, sender) }?.let { return it.value }
        return sender.trim()
            .replace(CAMEL_BREAK, " ")
            .replace(RUN_OF_SPACE, " ")
            .ifBlank { sender }
    }
}

/**
 * Turns a scanned inbox into proposed [Account]s, so first-run setup starts from what
 * the bank already told you rather than from an empty form.
 *
 * The key trick is that banks quote a running balance in almost every message
 * ("Avl Bal OMR 1,240.500"). [SmsParser] captures the newest one it sees per account;
 * from there the opening balance is just that figure, valid from that moment on, and
 * every later message moves it. Accounts whose balance was never quoted come back with
 * [Account.needsBalance] set so the confirmation screen knows to ask for it.
 */
object AccountDiscovery {

    /** One parsed message, reduced to what account setup cares about. */
    data class Observation(
        val sender: String,
        val accountTail: String?,
        val currency: String,
        val balanceMinor: Long?,
        val atMillis: Long,
        /** Effect on the account: positive for money in, negative for money out. */
        val signedMinor: Long,
        /** The tail names a card, which is not an account number. */
        val tailIsCard: Boolean = false,
        /** The balance line it is on ([BalanceChain]); null when it quoted no balance. */
        val line: Int? = null,
        /** The account it already belongs to: a stored record, read back for context. */
        val accountId: String? = null,
        /** The digits before the mask of a masked account number ([SmsParser.Result.Parsed.accountHead]). */
        val head: String? = null,
    ) {
        /** The digits only when they name an account, which is what an account is keyed by. */
        val accountNumberTail: String? get() = accountTail?.takeUnless { tailIsCard }
    }

    /**
     * The number each balance line quotes most: the account that line is. Ties go to the
     * number quoted most recently.
     */
    fun lineTails(observations: List<Observation>): Map<Int, String> =
        observations.filter { it.line != null && it.accountNumberTail != null }
            .groupBy { it.line!! }
            .mapValues { (_, group) ->
                group.groupBy { it.accountNumberTail!! }
                    .maxWith(compareBy({ it.value.size }, { e -> e.value.maxOf { it.atMillis } }))
                    .key
            }

    /**
     * Which of the digits one sender's messages quote are account numbers.
     *
     * A bank does not only quote your account. A transfer quotes the other party's, and
     * some messages put a reference where the account usually goes; taken at their word,
     * one Bank Nizwa account became seven. The balances decide instead: a number is an
     * account when it is what some balance line mostly quotes, and it has to come up more
     * than once - a reference never repeats. Messages that quote no balance keep the old
     * reading, every number an account, because nothing says otherwise.
     *
     * A sender whose numbers all appear once still has an account; its best-quoted
     * number names it.
     */
    private fun accountTails(all: List<Observation>, tops: Map<Int, String>): List<String> {
        val counts = all.mapNotNull { it.accountNumberTail }.groupingBy { it }.eachCount()
        val onLines = all.mapNotNull { o -> o.line?.let { tops[it] } }.distinct()
        val unlined = all.filter { it.line == null }.mapNotNull { it.accountNumberTail }.distinct()
        val tails = (onLines.filter { (counts[it] ?: 0) >= 2 } + unlined).distinct()
        if (tails.isNotEmpty() || onLines.isEmpty()) return tails
        return listOf(onLines.maxWith(compareBy { counts[it] ?: 0 }))
    }

    fun propose(observations: List<Observation>, colorOffset: Int = 0): List<Account> {
        if (observations.isEmpty()) return emptyList()

        // Banks are not consistent about how they spell their own sender id: the same
        // bank arrives as "BankMuscat", "BANKMUSCAT" and "Bank Muscat". Grouping on the
        // exact text gave every spelling an account of its own - a tester's two banks
        // came back as six - and since the id below is built from the lowercased sender,
        // those extra accounts all claimed the same id. Two rows sharing a key is what
        // brought the accounts screen down.
        val bySender = observations.groupBy { it.sender.trim().lowercase() }
            .filterKeys { it.isNotBlank() }
        val tops = lineTails(observations)
        val out = ArrayList<Account>()

        for ((_, all) in bySender) {
            // The spelling the bank used most often speaks for the group; it is what the
            // account is named after and keyed by.
            val sender = all.groupingBy { it.sender.trim() }.eachCount()
                .maxByOrNull { it.value }?.key ?: continue
            if (!holdsAnAccount(sender, all)) continue
            // Card digits are not an account number, so they never define an account -
            // otherwise every card you carry becomes a bank account you don't have.
            val tails = accountTails(all, tops)
            // What to do with messages that never quote an account number:
            //  · sender names no account anywhere → it has one account, best effort
            //  · sender names exactly one → they belong to it
            //  · sender names several → a message goes where its own number or its
            //    balance line says. One that neither places cannot be attributed, and
            //    inventing an extra "Main" for it would invent an account the user
            //    doesn't have (Bank Muscat's three accounts became four this way). It is
            //    left unassigned instead, and can be placed by hand from the row.
            val groups: Map<String?, List<Observation>> = when (tails.size) {
                0 -> mapOf(null to all)
                1 -> mapOf(tails[0] as String? to all)
                else -> all.mapNotNull { o ->
                    val tail = o.accountNumberTail?.takeIf { it in tails }
                        ?: o.line?.let { tops[it] }?.takeIf { it in tails }
                    tail?.let { it to o }
                }.groupBy({ it.first }, { it.second })
            }
            for ((tail, group) in groups) {
                out += accountFor(sender, tail, group, indexHint = out.size + colorOffset)
            }
        }
        return out.sortedWith(compareBy({ it.bankName }, { it.last4 ?: "" }))
    }

    /**
     * The account each balance line belongs to, where something says: a stored record on
     * the line, or the number the line quotes most being one an account answers to.
     * Lines whose evidence points two ways are left out.
     */
    fun lineAccounts(accounts: List<Account>, observations: List<Observation>): Map<Int, String> {
        val tops = lineTails(observations)
        val out = HashMap<Int, String>()
        for ((line, group) in observations.filter { it.line != null }.groupBy { it.line!! }) {
            val sender = group.first().sender
            val byTail = tops[line]?.let { tail -> ownerOf(accounts, sender, tail) }
            val stored = group.mapNotNull { it.accountId }.distinct()
            val id = when {
                byTail != null && (stored.isEmpty() || stored == listOf(byTail)) -> byTail
                byTail == null && stored.size == 1 -> stored[0]
                else -> null
            }
            if (id != null) out[line] = id
        }
        return out
    }

    /** The account of [sender] that answers to [tail], by its own number or an alias. */
    fun ownerOf(accounts: List<Account>, sender: String, tail: String): String? {
        val s = sender.trim().lowercase()
        return accounts.firstOrNull { a ->
            a.answersTo(tail) && a.senderIds.any { it.trim().lowercase() == s }
        }?.id
    }

    /**
     * Accounts that the balances show were never accounts: every message quoting their
     * number sits on a line that another of the sender's accounts is mostly quoted on, so
     * the number was a reference or the other party's. Each comes back with the account
     * it really is, to be folded into it. Only a pass that read everything can say
     * "every message", and one the user named is left alone; a name means they meant it.
     */
    fun foreignAccounts(accounts: List<Account>, observations: List<Observation>): Map<String, String> {
        val tops = lineTails(observations)
        val lines = lineAccounts(accounts, observations)
        val out = HashMap<String, String>()
        for (account in accounts) {
            val tail = account.last4 ?: continue
            val senders = account.senderIds.mapTo(HashSet()) { it.trim().lowercase() }
            val mine = observations.filter { it.sender.trim().lowercase() in senders }
            val quoting = mine.filter { it.accountNumberTail == tail }
            // Its digits are where a masked number starts, not where it ends: "0210" from
            // "00210 ###### 001". That is the branch, so the account is whichever the
            // number's end names - even one the user has named since, because it never
            // was an account. Nothing may quote it as an end of its own.
            if (quoting.isEmpty()) {
                val real = mine.firstNotNullOfOrNull { o ->
                    o.accountNumberTail?.takeIf { o.head?.endsWith(tail) == true }
                        ?.let { ownerOf(accounts, o.sender, it) }
                        ?.takeIf { it != account.id }
                }
                if (real != null) {
                    out[account.id] = real
                    continue
                }
            }
            if (account.name.isNotBlank()) continue
            val owner = if (quoting.isEmpty()) {
                // Nothing quotes its number any more: an earlier reading took it from a
                // message that is now read better ("00210 ###### 001" was "0210"). The
                // message the account was opened from - the one whose balance it started
                // with - says which account that really was.
                if (account.needsBalance) continue
                val founding = mine.firstOrNull {
                    it.atMillis + 1 == account.openingAtMillis && it.balanceMinor == account.openingBalanceMinor
                } ?: continue
                founding.accountNumberTail?.let { ownerOf(accounts, founding.sender, it) }
                    ?: founding.line?.let { lines[it] }
                    ?: continue
            } else {
                if (quoting.any { it.line == null }) continue
                val owners = quoting.map { o ->
                    tops[o.line]?.takeIf { it != tail }?.let { ownerOf(accounts, o.sender, it) }
                }
                owners.distinct().singleOrNull() ?: continue
            }
            if (owner != account.id) out[account.id] = owner
        }
        return out
    }

    /**
     * The accounts these messages describe that [existing] does not cover yet - so an
     * account can appear on the day its bank first texts, rather than only on a first
     * run. That is the whole of "start fresh": the history begins empty and the banks
     * introduce themselves one at a time.
     *
     * The test for "already covered" is [routeTo]: if a message would land in an
     * account we have, it describes that account and nothing new. On top of that, a
     * sender we already know only earns another account when the bank quoted a number
     * for it - otherwise every message that happens to omit the account number would
     * mint a duplicate of a bank you already have.
     */
    fun proposeMissing(existing: List<Account>, observations: List<Observation>): List<Account> {
        if (observations.isEmpty()) return emptyList()
        if (existing.isEmpty()) return propose(observations)

        // A message on a balance line that already belongs to an account describes that
        // account, whatever digits it quotes: that is the reference or the other party's
        // number in a message about money that moved in yours.
        val placedLines = lineAccounts(existing, observations).keys
        val unplaced = observations.filter {
            it.accountId == null &&
                (it.line == null || it.line !in placedLines) &&
                routeTo(existing, it.sender, it.accountTail, it.tailIsCard) == null
        }
        if (unplaced.isEmpty()) return emptyList()

        val knownSenders = existing.flatMap { it.senderIds }.mapTo(HashSet()) { it.trim().lowercase() }
        val existingIds = existing.mapTo(HashSet()) { it.id }
        // At a bank we already know, a number quoted once is not an account yet. It is as
        // likely a reference as a new account, and a new account quotes its number again
        // soon enough; until then its message waits unassigned rather than founding one.
        val quoted = observations.mapNotNull { o ->
            o.accountNumberTail?.let { o.sender.trim().lowercase() to it }
        }.groupingBy { it }.eachCount()
        return propose(unplaced, colorOffset = existing.size).filter { candidate ->
            val known = candidate.senderIds.filter { it.trim().lowercase() in knownSenders }
            candidate.id !in existingIds &&
                (known.isEmpty() ||
                    candidate.last4 != null &&
                    known.any { (quoted[it.trim().lowercase() to candidate.last4] ?: 0) >= 2 })
        }
    }

    /**
     * Whether this sender's messages describe an account at all. Being wrong here is
     * expensive in both directions, so it takes either a recognisable bank name or the
     * bank-like habit of quoting a balance; a user whose bank does neither can still
     * add the account by hand.
     */
    private fun holdsAnAccount(sender: String, group: List<Observation>): Boolean =
        Banks.looksLikeBank(sender) || group.any { it.balanceMinor != null }

    private fun accountFor(
        sender: String,
        tail: String?,
        group: List<Observation>,
        indexHint: Int,
    ): Account {
        val currency = group.groupingBy { it.currency }.eachCount()
            .maxByOrNull { it.value }?.key ?: "OMR"
        val quoted = group.filter { it.balanceMinor != null && it.currency == currency }
            .maxByOrNull { it.atMillis }

        // A quoted balance already includes its own message's amount, so the opening
        // moment starts just after it and only later messages move the number.
        val openingBalance = quoted?.balanceMinor ?: 0L
        val openingAt = quoted?.let { it.atMillis + 1 }
            ?: (group.minOfOrNull { it.atMillis } ?: System.currentTimeMillis())

        return Account(
            id = Account.ID_PREFIX + Integer.toHexString((sender.lowercase() + "|" + tail).hashCode()),
            // No nickname: the account names itself from the bank and the digits the
            // bank quotes, and keeps following them if either is corrected later.
            name = "",
            bankName = Banks.displayName(sender),
            last4 = tail,
            currency = currency,
            openingBalanceMinor = openingBalance,
            openingAtMillis = openingAt,
            senderIds = setOf(sender),
            color = Categories.PALETTE[indexHint % Categories.PALETTE.size],
            needsBalance = quoted == null,
        )
    }

    /**
     * Which account a message belongs to, strongest signal first: the tail the bank
     * quoted, then the sender's only account, then - for a message that named no
     * account at all - the sender's one untailed account, which [propose] creates for
     * exactly these messages.
     *
     * A sender with several tailed accounts and no untailed one stays deliberately
     * unrouted: guessing would put the money in the wrong balance, and an unassigned
     * row is one tap away from being placed by hand.
     */
    /**
     * Whether this sender is one of the user's banks at all. A telecom, a broadcaster or
     * a shop can text about money - a bill, a prize draw, a package price - but it can
     * never move money in an account, so nothing it says belongs in a spending total.
     */
    fun isKnownSender(accounts: List<Account>, sender: String): Boolean {
        val s = sender.trim().lowercase()
        if (s.isEmpty()) return false
        return accounts.any { account ->
            account.senderIds.any { it.trim().lowercase() == s } ||
                account.bankName.trim().lowercase().let { it.isNotEmpty() && it == s }
        }
    }

    fun routeTo(
        accounts: List<Account>,
        sender: String,
        tail: String?,
        tailIsCard: Boolean = false,
    ): String? {
        if (accounts.isEmpty()) return null
        val s = sender.trim().lowercase()
        fun ownsSender(a: Account) = a.senderIds.any { it.trim().lowercase() == s }

        if (tail != null) {
            accounts.firstOrNull { it.answersTo(tail) && ownsSender(it) }?.let { return it.id }
            accounts.firstOrNull { it.answersTo(tail) }?.let { return it.id }
        }
        val bySender = accounts.filter { ownsSender(it) }
        // The bank named an account number and none of ours carries it. Dropping the
        // money into "the sender's only account" would put it in the wrong balance and
        // hide an account you actually have, so only an account whose number we never
        // learned may take it. A card's digits carry no such claim: the card belongs to
        // some account of that bank, so the old best-effort routing still applies.
        if (tail != null && !tailIsCard && bySender.any { it.last4 != null }) {
            return bySender.singleOrNull { it.last4 == null }?.id
        }
        if (bySender.size == 1) return bySender[0].id
        val untailed = bySender.filter { it.last4 == null }
        return if (untailed.size == 1) untailed[0].id else null
    }

    /**
     * Live balance of [account]: what it opened with, plus everything that touched it
     * from that moment on. Records before the opening moment are already baked into the
     * opening figure and must not be counted twice.
     */
    fun balanceOf(account: Account, txns: List<Txn>): Long {
        var balance = account.openingBalanceMinor
        for (t in txns) {
            if (t.atMillis < account.openingAtMillis) continue
            if (t.currency != account.currency) continue
            balance += t.signedFor(account.id)
        }
        return balance
    }
}
