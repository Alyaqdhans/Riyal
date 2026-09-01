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
    ) {
        /** The digits only when they name an account, which is what an account is keyed by. */
        val accountNumberTail: String? get() = accountTail?.takeUnless { tailIsCard }
    }

    fun propose(observations: List<Observation>, colorOffset: Int = 0): List<Account> {
        if (observations.isEmpty()) return emptyList()

        val bySender = observations.groupBy { it.sender.trim() }
            .filterKeys { it.isNotBlank() }
            .filter { (sender, group) -> holdsAnAccount(sender, group) }
        val out = ArrayList<Account>()

        for ((sender, all) in bySender) {
            // Card digits are not an account number, so they never define an account -
            // otherwise every card you carry becomes a bank account you don't have.
            val tails = all.mapNotNull { it.accountNumberTail }.distinct()
            // What to do with messages that never quote an account number:
            //  · sender names no account anywhere → it has one account, best effort
            //  · sender names exactly one → they belong to it
            //  · sender names several → they cannot be attributed, and inventing an
            //    extra "Main" for them would invent an account the user doesn't have
            //    (Bank Muscat's three accounts became four this way). They are left
            //    unassigned instead, and can be placed by hand from the row.
            val groups: Map<String?, List<Observation>> = when (tails.size) {
                0 -> mapOf(null to all)
                1 -> mapOf(tails[0] as String? to all)
                else -> all.filter { it.accountNumberTail != null }.groupBy { it.accountNumberTail }
            }
            for ((tail, group) in groups) {
                out += accountFor(sender, tail, group, indexHint = out.size + colorOffset)
            }
        }
        return out.sortedWith(compareBy({ it.bankName }, { it.last4 ?: "" }))
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

        val unplaced = observations.filter {
            routeTo(existing, it.sender, it.accountTail, it.tailIsCard) == null
        }
        if (unplaced.isEmpty()) return emptyList()

        val knownSenders = existing.flatMap { it.senderIds }.mapTo(HashSet()) { it.trim().lowercase() }
        val existingIds = existing.mapTo(HashSet()) { it.id }
        return propose(unplaced, colorOffset = existing.size).filter { candidate ->
            candidate.id !in existingIds &&
                (candidate.last4 != null ||
                    candidate.senderIds.none { it.trim().lowercase() in knownSenders })
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
            accounts.firstOrNull { it.last4 == tail && ownsSender(it) }?.let { return it.id }
            accounts.firstOrNull { it.last4 == tail }?.let { return it.id }
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
