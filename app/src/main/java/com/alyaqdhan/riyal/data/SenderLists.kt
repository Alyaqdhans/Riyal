package com.alyaqdhan.riyal.data

/**
 * Whether each sender in the inbox is read, the way the scan's sender gate decides it,
 * for Settings › Lists to show and change.
 */
object SenderLists {

    enum class Status {
        /** Ignored by the user: nothing it sends is read. */
        IGNORED,

        /** A bank by its name, or the sender of one of the user's accounts. Read. */
        BANK,

        /** Not a bank, but the user asked for it to be read. */
        ADDED,

        /** A phone number: a person, never read unless added. */
        PHONE,

        /** Not a bank and not added, so the gate leaves it out. */
        LEFT_OUT,
    }

    data class Row(
        val sender: String,
        val status: Status,
        /** A bank by name or by account: read unless ignored, so never merely left out. */
        val bank: Boolean,
    ) {
        val read: Boolean get() = status == Status.BANK || status == Status.ADDED
    }

    /** "+96891234567", "9123 4567"… anything that is just a phone number. */
    fun isPhoneNumber(sender: String): Boolean =
        sender.isNotBlank() && sender.all { it.isDigit() || it in "+ -()" }

    private fun key(sender: String) = sender.trim().lowercase()

    fun statusOf(
        sender: String,
        accounts: List<Account>,
        ignored: Set<String>,
        added: Set<String>,
    ): Status {
        val k = key(sender)
        return when {
            ignored.any { key(it) == k } -> Status.IGNORED
            added.any { key(it) == k } -> if (isBank(sender, accounts)) Status.BANK else Status.ADDED
            isPhoneNumber(sender) -> Status.PHONE
            isBank(sender, accounts) -> Status.BANK
            else -> Status.LEFT_OUT
        }
    }

    fun isBank(sender: String, accounts: List<Account>) =
        Banks.looksLikeBank(sender) || AccountDiscovery.isKnownSender(accounts, sender)

    /**
     * Every sender once, however its case and spacing varied, with what is read first,
     * then what is left out, then what is ignored; by name within each.
     */
    fun rows(
        senders: Collection<String>,
        accounts: List<Account>,
        ignored: Set<String>,
        added: Set<String>,
    ): List<Row> =
        (senders + ignored + added)
            .filter { it.isNotBlank() }
            .distinctBy { key(it) }
            .map { Row(it.trim(), statusOf(it, accounts, ignored, added), isBank(it, accounts)) }
            .sortedWith(
                compareBy<Row>(
                    {
                        when (it.status) {
                            Status.BANK, Status.ADDED -> 0
                            Status.LEFT_OUT, Status.PHONE -> 1
                            Status.IGNORED -> 2
                        }
                    },
                    { it.sender.lowercase() },
                ),
            )
}
