package com.alyaqdhan.riyal.data

enum class Direction { EXPENSE, INCOME }

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
        Category("transfer", "Transfers"),
        Category("other", "Other"),
        Category("salary", "Salary", income = true),
        Category("income", "Other Income", income = true),
    )

    const val DEFAULT_EXPENSE = "other"
    const val DEFAULT_INCOME = "income"
    const val CUSTOM_ID_PREFIX = "u_"

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

    fun forDirection(direction: Direction): List<Category> =
        ALL.filter { it.income == (direction == Direction.INCOME) }

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
        "transfer" to 0xFFFFA726.toInt(),
        "other" to 0xFF9E9E9E.toInt(),
        "salary" to 0xFF43A047.toInt(),
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

data class Txn(
    val id: String,
    val atMillis: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val merchant: String?,
    val sender: String,
    val body: String,
    val categoryId: String,
    val categorySource: String, // "auto" | "user"
    val confidence: Int,        // 0..100, how sure the parser was
    val manual: Boolean = false,
)

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
    /**
     * Fingerprint of "the same kind of message": the sender plus the body with every
     * number blanked out, so two balance alerts or two promos from one sender look
     * identical even though their digits differ.
     */
    fun of(sender: String, body: String): String {
        val norm = body.lowercase()
            .replace(Regex("[0-9٠-٩][0-9٠-٩.,:/-]*"), "#")
            .replace(Regex("\\s+"), " ")
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
)
