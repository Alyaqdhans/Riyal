package com.alyaqdhan.riyal.core

import android.content.Context
import android.content.SharedPreferences

/**
 * User-controlled settings. Everything the scanner does is driven from here:
 * which keywords gate a message, which senders are allowed, how far back to look.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("riyal_prefs", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = sp.edit().putBoolean("onboarding_done", v).apply()

    /**
     * 0 means "everything in the inbox", the default: even a few thousand messages
     * parse in seconds, so there is no reason to silently ignore older history.
     */
    var scanRangeMonths: Int
        get() = sp.getInt("scan_range_months", 0)
        set(v) = sp.edit().putInt("scan_range_months", v).apply()

    var defaultCurrency: String
        get() = sp.getString("default_currency", "OMR") ?: "OMR"
        set(v) = sp.edit().putString("default_currency", v).apply()

    /** Only read senders whose name looks like a bank (contains bank/بنك/مصرف) or is allowlisted. */
    var bankSendersOnly: Boolean
        get() = sp.getBoolean("bank_senders_only", true)
        set(v) = sp.edit().putBoolean("bank_senders_only", v).apply()

    /** Kick off a scan automatically when the app opens (still one-shot, still verbose). */
    var scanOnLaunch: Boolean
        get() = sp.getBoolean("scan_on_launch", true)
        set(v) = sp.edit().putBoolean("scan_on_launch", v).apply()

    /**
     * Learn from corrections: when the user fixes a category on a transaction with a
     * merchant, the category picker's "Always" switch starts ON, so a rule is saved
     * and applied to past and future messages unless the user opts out per edit.
     */
    var smartRules: Boolean
        get() = sp.getBoolean("smart_rules", true)
        set(v) = sp.edit().putBoolean("smart_rules", v).apply()

    /** Monthly spending budget in minor units of the default currency. 0 = off. */
    var monthlyBudgetMinor: Long
        get() = sp.getLong("monthly_budget_minor", 0L)
        set(v) = sp.edit().putLong("monthly_budget_minor", v).apply()

    /**
     * Per-category monthly budgets, in minor units of the default currency, keyed by
     * category id. Absent or 0 means "no budget for this category". Stored as one
     * `id=amount` line per entry so it survives without a schema migration.
     */
    var categoryBudgets: Map<String, Long>
        get() {
            val raw = sp.getStringSet("category_budgets", emptySet()) ?: emptySet()
            return raw.mapNotNull { line ->
                val i = line.lastIndexOf('=')
                if (i <= 0) return@mapNotNull null
                val id = line.substring(0, i)
                val amt = line.substring(i + 1).toLongOrNull() ?: return@mapNotNull null
                if (amt > 0) id to amt else null
            }.toMap()
        }
        set(v) = sp.edit().putStringSet(
            "category_budgets",
            v.filterValues { it > 0 }.map { (id, amt) -> "$id=$amt" }.toSet(),
        ).apply()

    fun setCategoryBudget(categoryId: String, minor: Long) {
        categoryBudgets = categoryBudgets.toMutableMap().apply {
            if (minor > 0) this[categoryId] = minor else remove(categoryId)
        }
    }

    var senderFilterEnabled: Boolean
        get() = sp.getBoolean("sender_filter_enabled", false)
        set(v) = sp.edit().putBoolean("sender_filter_enabled", v).apply()

    var senderAllowlist: Set<String>
        get() = sp.getStringSet("sender_allowlist", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("sender_allowlist", v.toSet()).apply()

    var expenseKeywords: Set<String>
        get() = sp.getStringSet("kw_expense", null)?.toSet() ?: DEFAULT_EXPENSE_KEYWORDS
        set(v) = sp.edit().putStringSet("kw_expense", v.toSet()).apply()

    var incomeKeywords: Set<String>
        get() = sp.getStringSet("kw_income", null)?.toSet() ?: DEFAULT_INCOME_KEYWORDS
        set(v) = sp.edit().putStringSet("kw_income", v.toSet()).apply()

    var lastScanAt: Long
        get() = sp.getLong("last_scan_at", 0L)
        set(v) = sp.edit().putLong("last_scan_at", v).apply()

    fun resetKeywords() {
        sp.edit().remove("kw_expense").remove("kw_income").apply()
    }

    fun wipe() {
        val onboarding = onboardingDone
        sp.edit().clear().apply()
        onboardingDone = onboarding
    }

    companion object {
        /**
         * The gate: nothing is processed unless it contains one of these. The original
         * withdraw/deposit pair missed real bank wording ("debited", "credited",
         * "purchase"…), so the defaults now cover the phrasings Omani banks actually
         * send, still fully editable in Settings.
         */
        val DEFAULT_EXPENSE_KEYWORDS = setOf(
            "withdraw", "withdrawal", "withdrawn",
            "debited", "purchase", "paid", "payment",
            "سحب", "خصم", "شراء", "دفع",
        )
        val DEFAULT_INCOME_KEYWORDS = setOf(
            "deposit", "deposited",
            "credited", "received", "refund", "salary",
            "إيداع", "ايداع", "راتب",
        )
    }
}
