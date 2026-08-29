package com.alyaqdhan.riyal.data

/**
 * Which way money moved *in one message*. This is the parser's vocabulary only:
 * [SmsParser] reports it and [Categorizer] picks a category from it. Stored records
 * speak [TxnType] instead, because a transfer is money out AND money in at once.
 */
enum class Direction { EXPENSE, INCOME }

/**
 * The three operations a record can be. A TRANSFER moves money between two of the
 * user's own accounts, so it must never land in an income or an expense total - that
 * double-counting is exactly what this type exists to prevent.
 */
enum class TxnType {
    INCOME, EXPENSE, TRANSFER;

    val isFlow: Boolean get() = this != TRANSFER

    companion object {
        fun of(direction: Direction): TxnType =
            if (direction == Direction.INCOME) INCOME else EXPENSE
    }
}

data class Category(
    val id: String,
    val name: String,
    val income: Boolean = false,
    /** ARGB color; 0 means "use the built-in/default color for this id". */
    val color: Int = 0,
    /** True for user-created categories, which can be renamed, recolored, deleted. */
    val custom: Boolean = false,
)

/**
 * The category registry: fixed built-ins plus whatever the user added. It is a
 * plain in-memory snapshot so pure code ([Categorizer], the UI) can resolve a
 * category id synchronously; [Store] owns the user list and calls [setCustom]
 * whenever it loads or changes, keeping this registry in sync.
 */
object Categories {

    val BUILTIN = listOf(
        Category("food", "Food & Dining"),
        Category("groceries", "Groceries"),
        Category("transport", "Transport & Fuel"),
        Category("bills", "Bills & Telecom"),
        Category("shopping", "Shopping"),
        Category("health", "Health"),
        Category("entertainment", "Entertainment"),
        Category("travel", "Travel"),
        Category("education", "Education"),
        Category("fees", "Fees & Charges"),
        Category("cash", "Cash & ATM"),
        Category("sending", "Money Sent"),
        Category("transfer", "Transfers"),
        Category("other", "Other"),
        Category("salary", "Salary", income = true),
        Category("business", "Business & Freelance", income = true),
        Category("investment", "Investment & Interest", income = true),
        Category("refund", "Refunds", income = true),
        Category("gift", "Gifts & Support", income = true),
        Category("income", "Other Income", income = true),
    )

    const val DEFAULT_EXPENSE = "other"
    const val DEFAULT_INCOME = "income"
    const val CUSTOM_ID_PREFIX = "u_"

    /**
     * The category every [TxnType.TRANSFER] record carries. It still resolves through
     * [byId] (so transfer rows keep a name, color and badge) but it is deliberately kept
     * out of [forDirection] / [forType]: moving your own money is an operation type now,
     * not something you file under a spending category.
     *
     * Money sent to *someone else* is a different thing entirely - real spending - and
     * is filed under "sending" instead.
     */
    const val TRANSFER_ID = "transfer"

    @Volatile
    private var custom: List<Category> = emptyList()

    /** Called by [Store] on load and on every change to the user's categories. */
    fun setCustom(list: List<Category>) {
        custom = list.map { it.copy(custom = true) }
    }

    /** Built-ins first (their order is meaningful), then user categories. */
    val ALL: List<Category> get() = BUILTIN + custom

    private val builtinById = BUILTIN.associateBy { it.id }

    fun byId(id: String): Category =
        builtinById[id] ?: custom.firstOrNull { it.id == id } ?: builtinById.getValue(DEFAULT_EXPENSE)

    /** Pickable categories for one direction of money, transfers excluded. */
    fun forDirection(direction: Direction): List<Category> =
        ALL.filter { it.income == (direction == Direction.INCOME) && it.id != TRANSFER_ID }

    /** Pickable categories for a record type; a transfer has no choice to make. */
    fun forType(type: TxnType): List<Category> = when (type) {
        TxnType.INCOME -> forDirection(Direction.INCOME)
        TxnType.EXPENSE -> forDirection(Direction.EXPENSE)
        TxnType.TRANSFER -> emptyList()
    }

    fun defaultFor(type: TxnType): String = when (type) {
        TxnType.INCOME -> DEFAULT_INCOME
        TxnType.EXPENSE -> DEFAULT_EXPENSE
        TxnType.TRANSFER -> TRANSFER_ID
    }

    private val BUILTIN_COLORS = mapOf(
        "food" to 0xFFFF7043.toInt(),
        "groceries" to 0xFF26A69A.toInt(),
        "transport" to 0xFF42A5F5.toInt(),
        "bills" to 0xFFFFB300.toInt(),
        "shopping" to 0xFFEC407A.toInt(),
        "health" to 0xFF66BB6A.toInt(),
        "entertainment" to 0xFFAB47BC.toInt(),
        "travel" to 0xFF29B6F6.toInt(),
        "education" to 0xFF7E57C2.toInt(),
        "fees" to 0xFF8D6E63.toInt(),
        "cash" to 0xFF78909C.toInt(),
        "sending" to 0xFFFF8A65.toInt(),
        "transfer" to 0xFFFFA726.toInt(),
        "other" to 0xFF9E9E9E.toInt(),
        "salary" to 0xFF43A047.toInt(),
        "business" to 0xFF00897B.toInt(),
        "investment" to 0xFF5C6BC0.toInt(),
        "refund" to 0xFF26C6DA.toInt(),
        "gift" to 0xFFD4A017.toInt(),
        "income" to 0xFF9CCC65.toInt(),
    )

    /** The palette offered to user categories (also used to auto-assign on create). */
    val PALETTE = listOf(
        0xFFEF5350.toInt(), 0xFFEC407A.toInt(), 0xFFAB47BC.toInt(), 0xFF7E57C2.toInt(),
        0xFF5C6BC0.toInt(), 0xFF42A5F5.toInt(), 0xFF29B6F6.toInt(), 0xFF26A69A.toInt(),
        0xFF66BB6A.toInt(), 0xFF9CCC65.toInt(), 0xFFFFB300.toInt(), 0xFFFF7043.toInt(),
        0xFF8D6E63.toInt(), 0xFF78909C.toInt(),
    )

    fun colorFor(id: String): Int {
        BUILTIN_COLORS[id]?.let { return it }
        val c = custom.firstOrNull { it.id == id }?.color ?: 0
        return if (c != 0) c else BUILTIN_COLORS.getValue("other")
    }
}

/**
 * One bank account. [openingBalanceMinor] is the money already in it as of
 * [openingAtMillis]; every record after that instant moves it, so the live balance is
 * always `opening + (money in) - (money out)` and never needs storing.
 *
 * First-run accounts are proposed by [AccountDiscovery] from the inbox (bank sender,
 * account tail, latest balance the bank quoted) and then confirmed or corrected by the
 * user, which is why [needsBalance] exists: it marks an account whose balance could not
 * be read from any message, so the confirmation screen knows to ask.
 */
data class Account(
    val id: String,
    val name: String,
    val bankName: String,
    /** Last 3-6 digits the bank quotes ("a/c XXXX1234"), used to route messages here. */
    val last4: String?,
    val currency: String,
    val openingBalanceMinor: Long,
    val openingAtMillis: Long,
    /** SMS sender ids whose messages belong to this account. */
    val senderIds: Set<String> = emptySet(),
    val color: Int = 0,
    val archived: Boolean = false,
    val needsBalance: Boolean = false,
) {
    /**
     * How the account is named everywhere in the app: "Bank Muscat · 0019" - the bank
     * plus the digits it quotes, which is how a bank account is actually recognised.
     * [name] is an optional nickname; when the user sets one it wins, and when they
     * clear it the name follows whatever bank and last-4 the account now carries.
     */
    val displayName: String
        get() = name.trim().ifEmpty { defaultNameOf(bankName, last4) }

    companion object {
        const val ID_PREFIX = "acc_"

        /** The name an account carries when the user hasn't nicknamed it. */
        fun defaultNameOf(bankName: String, last4: String?): String {
            val bank = bankName.trim()
            val tail = last4?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                bank.isNotEmpty() && tail != null -> "$bank · $tail"
                bank.isNotEmpty() -> bank
                tail != null -> "Account · $tail"
                else -> "Account"
            }
        }
    }
}

/**
 * One budget plan: a period plus a spending cap per expense category. The period is
 * free-form (a month by default, but any range the user picks), so a plan is stored
 * with explicit bounds rather than a month key.
 */
data class BudgetPlan(
    val id: String,
    val label: String,
    val startMillis: Long,
    val endExclusiveMillis: Long,
    /** categoryId -> cap in minor units. Only positive caps are kept. */
    val lines: Map<String, Long> = emptyMap(),
) {
    val totalMinor: Long get() = lines.values.sum()

    fun covers(millis: Long): Boolean = millis in startMillis until endExclusiveMillis

    /** True when this plan's period overlaps [start] .. [endExclusive] at all. */
    fun overlaps(start: Long, endExclusive: Long): Boolean =
        startMillis < endExclusive && start < endExclusiveMillis

    companion object {
        const val ID_PREFIX = "bp_"
    }
}

/**
 * A candidate internal transfer: an expense on one account and an income on another,
 * same amount, close in time. Never applied silently - the user says yes or no, and the
 * answer is remembered by [id] so a rescan replays it instead of asking again.
 */
data class TransferProposal(
    val id: String,
    val outTxnId: String,
    val inTxnId: String,
    val fromAccountId: String?,
    val toAccountId: String?,
    val amountMinor: Long,
    val currency: String,
    val atMillis: Long,
    val state: String = STATE_PENDING,
) {
    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_ACCEPTED = "accepted"
        const val STATE_REJECTED = "rejected"

        /** Stable across rescans, because transaction ids hash the message itself. */
        fun idFor(outTxnId: String, inTxnId: String) = "$outTxnId|$inTxnId"
    }
}

/**
 * One record. [fromAccountId] and [toAccountId] are what make the three types uniform:
 * an expense only has a source, income only a destination, and a transfer has both - so
 * an account's balance is one sum over every record that names it, whatever its type.
 */
data class Txn(
    val id: String,
    val atMillis: Long,
    val amountMinor: Long,
    val currency: String,
    val type: TxnType,
    /** EXPENSE: the account it left · TRANSFER: the source · INCOME: null. */
    val fromAccountId: String? = null,
    /** INCOME: the account it landed in · TRANSFER: the destination · EXPENSE: null. */
    val toAccountId: String? = null,
    val merchant: String?,
    val sender: String,
    /** For a scanned record this is the SMS text itself; it is the row's description. */
    val body: String,
    val categoryId: String,
    val categorySource: String, // "auto" | "user"
    val confidence: Int,        // 0..100, how sure the parser was
    val manual: Boolean = false,
    /** TRANSFER only: the ids of the two messages that were merged into this record. */
    val legIds: List<String> = emptyList(),
) {
    val isExpense: Boolean get() = type == TxnType.EXPENSE
    val isIncome: Boolean get() = type == TxnType.INCOME
    val isTransfer: Boolean get() = type == TxnType.TRANSFER

    /** The single account a flow record belongs to (null for an unassigned one). */
    val accountId: String? get() = fromAccountId ?: toAccountId

    /** True when this record touches [accountId] at either end. */
    fun touches(accountId: String): Boolean =
        fromAccountId == accountId || toAccountId == accountId

    /** What this record does to [accountId]'s balance: negative out, positive in. */
    fun signedFor(accountId: String): Long = when (accountId) {
        fromAccountId -> -amountMinor
        toAccountId -> amountMinor
        else -> 0L
    }
}

data class ReviewItem(
    val id: String,
    val atMillis: Long,
    val sender: String,
    val body: String,
    val reason: String,
    val state: String = STATE_PENDING,
) {
    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_DISMISSED = "dismissed"
        const val STATE_RESOLVED = "resolved"
    }
}

data class UserRule(val pattern: String, val categoryId: String)

/**
 * A kind of message the user dismissed from Review: similar future messages are
 * auto-dismissed on scan (still stored, restorable from the Review page).
 */
data class MutedTemplate(
    val template: String,
    val sender: String,
    val sample: String,
    val at: Long,
)

object MsgTemplate {

    // Compiled once: a scan fingerprints every message in the inbox.
    private val ANY_NUMBER = Regex("[0-9٠-٩][0-9٠-٩.,:/-]*")
    private val RUN_OF_SPACE = Regex("\\s+")
    /**
     * Fingerprint of "the same kind of message": the sender plus the body with every
     * number blanked out, so two balance alerts or two promos from one sender look
     * identical even though their digits differ.
     */
    fun of(sender: String, body: String): String {
        val norm = body.lowercase()
            .replace(ANY_NUMBER, "#")
            .replace(RUN_OF_SPACE, " ")
            .trim()
        return sender.trim().lowercase() + "|" + norm
    }
}

data class ScanSummary(
    val at: Long,
    val tookMs: Long,
    val scanned: Int,
    val matched: Int,
    val parsed: Int,
    val review: Int,
    val skipped: Int,
    val transfers: Int = 0,
)
