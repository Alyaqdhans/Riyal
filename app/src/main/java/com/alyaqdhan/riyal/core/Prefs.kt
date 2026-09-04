package com.alyaqdhan.riyal.core

import android.content.Context
import android.content.SharedPreferences

/**
 * The first-run answer to "how much of my inbox should this read?".
 *
 * [FROM_NOW] is the one that changes the shape of the app rather than its size: the
 * history starts empty and fills in as the banks text, so accounts appear one at a
 * time, on the day their bank first says something.
 */
enum class ScanHistory(val label: String, val months: Int) {
    ALL("All", 0),
    YEAR("1 year", 12),
    QUARTER("3 months", 3),
    FROM_NOW("From today", 0),
}

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

    /**
     * A fixed floor on what any scan reads: no message older than this is queried at
     * all. 0, the default, means no floor.
     *
     * This is what "start fresh" writes on first run - the moment the user chose it.
     * Unlike [scanRangeMonths] it does not slide: a rolling window keeps picking up
     * older messages as time passes, which is the opposite of starting fresh.
     */
    var scanSinceMillis: Long
        get() = sp.getLong("scan_since_millis", 0L)
        set(v) = sp.edit().putLong("scan_since_millis", v).apply()

    /** Applies a first-run history choice to the two settings that carry it. */
    fun applyScanHistory(choice: ScanHistory) {
        scanRangeMonths = choice.months
        scanSinceMillis = if (choice == ScanHistory.FROM_NOW) System.currentTimeMillis() else 0L
    }

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

    /**
     * Whether budget planning is switched on. Off by default: the budget section only
     * appears on Home once the user asks for it, so the dashboard stays about what
     * actually happened until they decide to plan against it. The plans themselves are
     * real records in [com.alyaqdhan.riyal.data.Store], not settings.
     */
    var budgetsEnabled: Boolean
        get() = sp.getBoolean("budgets_enabled", false)
        set(v) = sp.edit().putBoolean("budgets_enabled", v).apply()

    /**
     * Whether screens write their explanation onto the page as well as keeping it
     * behind the (i) in the title bar. Off by default: a screen that explains itself
     * at rest has to be read before it can be used, and the explanation is the same
     * every time while the list under it is the reason for opening the screen.
     */
    var showHelpText: Boolean
        get() = sp.getBoolean("help_on_page", false)
        set(v) = sp.edit().putBoolean("help_on_page", v).apply()

    /**
     * Set once the user has checked the accounts the first scan proposed. Until then
     * Home shows the confirmation prompt, because balances read out of SMS are a good
     * first guess and nothing more.
     */
    var accountsConfirmed: Boolean
        get() = sp.getBoolean("accounts_confirmed", false)
        set(v) = sp.edit().putBoolean("accounts_confirmed", v).apply()

    /**
     * Whether a matched pair is treated as a transfer without being asked. On by
     * default: the matcher only pairs an expense and an income of the same amount and
     * currency, on two different accounts, minutes apart - a coincidence that would
     * have to be engineered - and the alternative is a queue of thousands of identical
     * yes/no questions. Every auto-confirmed pair is still listed and reversible, and
     * turning this off puts the undecided ones back in Review.
     */
    var autoConfirmTransfers: Boolean
        get() = sp.getBoolean("auto_confirm_transfers", true)
        set(v) = sp.edit().putBoolean("auto_confirm_transfers", v).apply()

    /**
     * Whether the Activity list has already explained that its rows can be swiped. The
     * gesture is the only way to archive or remove a record and nothing on screen says
     * so, which leaves it undiscoverable until someone swipes by accident.
     */
    var swipeHintSeen: Boolean
        get() = sp.getBoolean("swipe_hint_seen", false)
        set(v) = sp.edit().putBoolean("swipe_hint_seen", v).apply()

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

    /**
     * When GitHub was last asked about a release. At most once a day: a new version
     * appears a few times a year, and a request on every launch would be a daily habit
     * of talking to a server for an answer that is nearly always "no".
     */
    var lastUpdateCheckAt: Long
        get() = sp.getLong("last_update_check_at", 0L)
        set(v) = sp.edit().putLong("last_update_check_at", v).apply()

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
            // "لقد استلمت OMR 2.000 من فلان" - money arriving. Without it the message
            // was gated in by "الدفع" inside "خدمات الدفع" further along, and since the
            // earliest keyword decides the direction, every one of these was recorded
            // as spending: money coming in, counted as money going out.
            "إيداع", "ايداع", "راتب", "استلمت",
        )
    }
}
