package com.alyaqdhan.riyal.data

enum class Direction { EXPENSE, INCOME }

data class Category(
    val id: String,
    val name: String,
    val income: Boolean = false,
)

object Categories {

    val ALL = listOf(
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

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String): Category = byId[id] ?: byId.getValue(DEFAULT_EXPENSE)

    fun forDirection(direction: Direction): List<Category> =
        ALL.filter { it.income == (direction == Direction.INCOME) }

    private val COLORS = mapOf(
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

    fun colorFor(id: String): Int = COLORS[id] ?: COLORS.getValue("other")
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
