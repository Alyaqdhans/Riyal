package com.alyaqdhan.riyal.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alyaqdhan.riyal.RiyalApp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.AccountDiscovery
import com.alyaqdhan.riyal.data.BudgetPlan
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.ScanEngine
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.ScanSummary
import com.alyaqdhan.riyal.data.TransferProposal
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.data.UserRule
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val riyal = app as RiyalApp
    val prefs get() = riyal.prefs
    val store get() = riyal.store

    val txns = store.txns
    val reviews = store.reviews
    val rules = store.rules

    /** Counterparties the user asked to be asked about every time, never rule-able. */
    val askEachTime = store.askEachTime
    val senders = store.senders
    val lastSummary = store.lastSummary
    val accounts = store.accounts
    val balances = store.balances
    val budgets = store.budgets
    val transfers = store.transfers

    val pendingTransfers: StateFlow<List<TransferProposal>> = store.transfers
        .map { list -> list.filter { it.state == TransferProposal.STATE_PENDING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * What the Home card and the toolbar dot count: unreadable messages plus transfer
     * pairs waiting on an answer. Both are "the app needs you to decide something".
     */
    val pendingReviewCount: StateFlow<Int> =
        combine(store.reviews, store.transfers) { reviews, transfers ->
            reviews.count { it.state == ReviewItem.STATE_PENDING } +
                transfers.count { it.state == TransferProposal.STATE_PENDING }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * Records the scan could not place and the user has not answered for. Counted for
     * the Home card the same way review items are: something the app needs a decision
     * on, said once, where it will be seen.
     */
    /**
     * How often the user has filed into each category. Every category picker reads it
     * to put the chips they actually use first; each picker freezes it for its own
     * lifetime, so this updating mid-edit never moves a chip under a finger.
     */
    val categoryUse: StateFlow<Map<String, Int>> = store.txns
        .map { Stats.categoryUse(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val needsCategoryCount: StateFlow<Int> =
        combine(store.txns, store.archivedIds) { txns, archived ->
            Stats.unfiled(txns, archived).size
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Accounts were proposed from SMS but the user hasn't checked them yet. */
    private val _accountsConfirmed = MutableStateFlow(prefs.accountsConfirmed)
    val accountsNeedConfirming: StateFlow<Boolean> =
        combine(store.accounts, _accountsConfirmed) { accounts, confirmed ->
            accounts.isNotEmpty() && !confirmed
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ─────────────────────────── scanning ───────────────────────────

    sealed interface ScanState {
        data object Idle : ScanState
        data class Running(val processed: Int, val total: Int) : ScanState
        data class Done(val summary: ScanSummary) : ScanState
        data class Failed(val message: String) : ScanState
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    val scanSheetVisible = MutableStateFlow(false)

    /**
     * The permanent Add FAB (in the bottom toolbar island) opens the manual-entry
     * dialog from any screen, so the request lives here rather than in one screen.
     */
    val manualAddVisible = MutableStateFlow(false)
    fun requestManualAdd() { manualAddVisible.value = true }
    fun dismissManualAdd() { manualAddVisible.value = false }

    private val _hasSmsPermission = MutableStateFlow(false)
    val hasSmsPermission: StateFlow<Boolean> = _hasSmsPermission
    private var lastLoggedPermission: Boolean? = null

    init {
        refreshPermission()
    }

    fun refreshPermission() {
        val granted = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        if (lastLoggedPermission != granted) {
            Verbose.info(
                if (granted) "READ_SMS permission: granted, you can revoke it anytime in system settings"
                else "READ_SMS permission: not granted, the app cannot and will not read anything"
            )
            Verbose.flush()
            lastLoggedPermission = granted
        }
        _hasSmsPermission.value = granted
    }

    private var launchScanDone = false

    /** Auto-scan when the app opens (Settings toggle, on by default). Quiet: no sheet pops up. */
    fun autoScanOnLaunch() {
        if (launchScanDone) return
        launchScanDone = true
        refreshPermission()
        if (prefs.onboardingDone && prefs.scanOnLaunch && _hasSmsPermission.value) {
            Verbose.info("scan on app open is ON (Settings → Scanning to turn it off)")
            Verbose.flush()
            startScan(showSheet = false)
        }
    }

    fun startScan(showSheet: Boolean = true) {
        if (_scanState.value is ScanState.Running) {
            if (showSheet) scanSheetVisible.value = true
            return
        }
        refreshPermission()
        if (!_hasSmsPermission.value) {
            Verbose.fail("scan requested, but READ_SMS is not granted, nothing was read")
            Verbose.flush()
            _scanState.value = ScanState.Failed("SMS reading permission is not granted")
            if (showSheet) scanSheetVisible.value = true
            return
        }
        _scanState.value = ScanState.Running(0, 0)
        if (showSheet) scanSheetVisible.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summary = ScanEngine(getApplication(), prefs, store).run { p ->
                    _scanState.value = ScanState.Running(p.processed, p.total)
                }
                prefs.lastScanAt = summary.at
                _scanState.value = ScanState.Done(summary)
            } catch (e: SecurityException) {
                Verbose.fail("scan aborted: the system refused the SMS read (permission revoked?)")
                Verbose.flush()
                _scanState.value = ScanState.Failed("The system refused the SMS read, check the permission")
            } catch (e: Exception) {
                Verbose.fail("scan failed: ${e.javaClass.simpleName}: ${e.message}")
                Verbose.flush()
                _scanState.value = ScanState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun closeScanSheet() {
        scanSheetVisible.value = false
        if (_scanState.value !is ScanState.Running) _scanState.value = ScanState.Idle
    }

    // ─────────────────────────── accounts ───────────────────────────

    fun balanceOf(account: Account): Long = AccountDiscovery.balanceOf(account, txns.value)

    fun saveAccount(account: Account) = viewModelScope.launch(Dispatchers.IO) {
        val existing = accounts.value.any { it.id == account.id }
        if (existing) store.updateAccount(account) else store.addAccount(account)
        Verbose.ok(
            (if (existing) "account updated by you: " else "account added by you: ") +
                "${account.displayName} · opening ${Money.format(account.openingBalanceMinor, account.currency)}"
        )
        Verbose.flush()
    }

    fun deleteAccount(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val name = accounts.value.firstOrNull { it.id == id }?.displayName ?: id
        store.deleteAccount(id)
        Verbose.info("account deleted by you: \"$name\" · its records kept, now unassigned")
        Verbose.flush()
    }

    fun confirmAccounts() {
        prefs.accountsConfirmed = true
        _accountsConfirmed.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // Confirming means the user vouched for these figures, so the "we never saw
            // a balance for this one" flag has served its purpose and comes off.
            accounts.value.filter { it.needsBalance }.forEach {
                store.updateAccount(it.copy(needsBalance = false))
            }
            Verbose.ok("accounts confirmed by you: ${accounts.value.size} account(s) are now the source of your balances")
            Verbose.flush()
        }
    }

    fun setTxnAccount(txn: Txn, accountId: String) = viewModelScope.launch(Dispatchers.IO) {
        store.setTxnAccount(txn.id, accountId)
        val name = accounts.value.firstOrNull { it.id == accountId }?.displayName ?: accountId
        Verbose.ok("account set by you: ${Money.format(txn.amountMinor, txn.currency)} → $name")
        Verbose.flush()
    }

    // ─────────────────────────── transfers ───────────────────────────

    fun acceptTransfer(proposal: TransferProposal) = viewModelScope.launch(Dispatchers.IO) {
        store.acceptTransfer(proposal)
        Verbose.ok(
            "transfer confirmed by you: ${Money.format(proposal.amountMinor, proposal.currency)} " +
                "moved between your accounts · it no longer counts as spending or income"
        )
        Verbose.flush()
    }

    fun rejectTransfer(proposal: TransferProposal) = viewModelScope.launch(Dispatchers.IO) {
        store.rejectTransfer(proposal)
        Verbose.info(
            "transfer rejected by you: both messages stay a real expense and a real income, " +
                "and this pair won't be suggested again"
        )
        Verbose.flush()
    }

    /**
     * The Settings switch: whether a matched pair becomes a transfer without being
     * asked. Flipping it re-answers only the pairs the user never answered themselves.
     */
    var autoConfirmTransfers: Boolean
        get() = prefs.autoConfirmTransfers
        set(v) {
            prefs.autoConfirmTransfers = v
            _autoConfirmOn.value = v
            viewModelScope.launch(Dispatchers.IO) {
                store.setAutoConfirmTransfers(v)
                Verbose.info(
                    if (v) {
                        "auto-confirm transfers on: matched pairs stop asking and stop " +
                            "counting as spending or income"
                    } else {
                        "auto-confirm transfers off: every matched pair waits for your answer in Review"
                    }
                )
                Verbose.flush()
            }
        }

    private val _autoConfirmOn = MutableStateFlow(prefs.autoConfirmTransfers)
    val autoConfirmOn: StateFlow<Boolean> = _autoConfirmOn

    fun splitTransfer(txn: Txn) = viewModelScope.launch(Dispatchers.IO) {
        store.splitTransfer(txn)
        Verbose.info("transfer split by you: both sides count again")
        Verbose.flush()
    }

    fun markAsTransfer(txn: Txn, fromAccountId: String?, toAccountId: String?) =
        viewModelScope.launch(Dispatchers.IO) {
            store.markAsTransfer(txn, fromAccountId, toAccountId)
            Verbose.ok(
                "marked as a transfer by you: ${Money.format(txn.amountMinor, txn.currency)} " +
                    "no longer counts as spending or income"
            )
            Verbose.flush()
        }

    // ─────────────────────────── budgets ───────────────────────────

    var budgetsEnabled: Boolean
        get() = prefs.budgetsEnabled
        set(v) {
            prefs.budgetsEnabled = v
            _budgetsOn.value = v
        }

    private val _budgetsOn = MutableStateFlow(prefs.budgetsEnabled)
    val budgetsOn: StateFlow<Boolean> = _budgetsOn

    fun addBudget(label: String, startMillis: Long, endExclusiveMillis: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            store.addBudget(
                BudgetPlan(
                    id = BudgetPlan.ID_PREFIX + UUID.randomUUID().toString().take(8),
                    label = label,
                    startMillis = startMillis,
                    endExclusiveMillis = endExclusiveMillis,
                )
            )
            Verbose.ok("budget plan created by you: \"$label\"")
            Verbose.flush()
        }

    fun setBudgetLine(planId: String, categoryId: String, minor: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            store.setBudgetLine(planId, categoryId, minor)
            Verbose.info(
                if (minor > 0) "budget set by you: ${Categories.byId(categoryId).name} capped at $minor (minor units)"
                else "budget removed by you: ${Categories.byId(categoryId).name}"
            )
            Verbose.flush()
        }

    fun deleteBudget(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val label = budgets.value.firstOrNull { it.id == id }?.label ?: id
        store.deleteBudget(id)
        Verbose.info("budget plan deleted by you: \"$label\"")
        Verbose.flush()
    }

    /**
     * Moves a plan to a different period, which is how a plan stops being a calendar
     * month: the screens step months, the plan carries its own bounds.
     */
    fun setBudgetPeriod(planId: String, label: String, startMillis: Long, endExclusiveMillis: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            val plan = budgets.value.firstOrNull { it.id == planId } ?: return@launch
            store.updateBudget(
                plan.copy(
                    label = label,
                    startMillis = startMillis,
                    endExclusiveMillis = endExclusiveMillis,
                )
            )
            Verbose.info("budget period changed by you: \"${plan.label}\" → \"$label\"")
            Verbose.flush()
        }

    /** Copies an existing plan's caps into a new period, the usual month-to-month move. */
    fun copyBudget(source: BudgetPlan, label: String, startMillis: Long, endExclusiveMillis: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            store.addBudget(
                BudgetPlan(
                    id = BudgetPlan.ID_PREFIX + UUID.randomUUID().toString().take(8),
                    label = label,
                    startMillis = startMillis,
                    endExclusiveMillis = endExclusiveMillis,
                    lines = source.lines,
                )
            )
            Verbose.ok("budget plan copied by you: \"${source.label}\" → \"$label\"")
            Verbose.flush()
        }

    // ─────────────────────────── user edits ───────────────────────────

    fun setCategory(txn: Txn, categoryId: String, alsoRulePattern: String?) =
        viewModelScope.launch(Dispatchers.IO) {
            store.setCategory(txn.id, categoryId)
            Verbose.ok(
                "category set by you: \"${txn.merchant ?: txn.sender}\" → ${Categories.byId(categoryId).name}"
            )
            if (!alsoRulePattern.isNullOrBlank()) {
                val changed = store.addRule(UserRule(alsoRulePattern.trim().lowercase(), categoryId))
                Verbose.ok(
                    "rule saved: \"${alsoRulePattern.trim().lowercase()}\" → ${Categories.byId(categoryId).name}" +
                        " · re-categorized $changed past transaction(s)"
                )
            }
            Verbose.flush()
        }

    /**
     * Files every record of one merchant, and remembers the answer. The rule is what
     * makes it stick to messages that have not arrived yet; the explicit filing covers
     * the records in front of the user right now, so nothing they just answered for can
     * come back on the next scan.
     *
     * A name marked "ask me every time" is filed just the same and remembered not at
     * all: those records still need a category today, and answering four of them in one
     * tap is worth as much for a person as for a shop. Only the rule is skipped.
     */
    fun fileMerchant(group: Stats.MerchantGroup, categoryId: String) =
        viewModelScope.launch(Dispatchers.IO) {
            val pattern = UserRule.patternOf(group.merchant)
            val filed = store.setCategories(group.txnIds, categoryId)
            Verbose.ok(
                "filed by you: ${group.count} record(s) from \"${group.merchant}\" → " +
                    Categories.byId(categoryId).name
            )
            if (pattern in store.askEachTime.value) {
                Verbose.info(
                    "no rule saved for \"$pattern\": you asked to be asked about this name " +
                        "every time, so the next message from it comes back to you"
                )
            } else {
                val byRule = store.addRule(UserRule(pattern, categoryId))
                Verbose.ok(
                    "rule saved: \"$pattern\" → ${Categories.byId(categoryId).name} · " +
                        "$filed record(s) filed now, $byRule more re-categorized, and anything " +
                        "matching from now on lands there without being asked"
                )
            }
            Verbose.flush()
        }

    val archivedIds = store.archivedIds

    fun archiveTxn(txn: Txn, archive: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        store.archiveTxn(txn, archive)
        Verbose.info(
            if (archive) {
                "archived by you: ${Money.format(txn.amountMinor, txn.currency)} is out of the " +
                    "lists but still counts towards your balance"
            } else {
                "unarchived by you: ${Money.format(txn.amountMinor, txn.currency)} is back in the lists"
            }
        )
        Verbose.flush()
    }

    fun ignoreTxn(txn: Txn) = viewModelScope.launch(Dispatchers.IO) {
        store.ignoreTxn(txn)
        Verbose.info(
            "transaction removed by you: ${Money.format(txn.amountMinor, txn.currency)} (${txn.sender})" +
                if (!txn.manual) " · this message will stay ignored on future scans" else ""
        )
        Verbose.flush()
    }

    fun addRule(pattern: String, categoryId: String) = viewModelScope.launch(Dispatchers.IO) {
        val changed = store.addRule(UserRule(pattern.trim().lowercase(), categoryId))
        Verbose.ok("rule saved: \"${pattern.trim().lowercase()}\" → ${Categories.byId(categoryId).name} · re-categorized $changed transaction(s)")
        Verbose.flush()
    }

    fun removeRule(pattern: String) = viewModelScope.launch(Dispatchers.IO) {
        val changed = store.removeRule(pattern)
        Verbose.info(
            "rule removed: \"$pattern\"" +
                if (changed > 0) " · $changed record(s) it had filed were answered again" else ""
        )
        Verbose.flush()
    }

    /**
     * Marks a counterparty as one to be asked about every time, or lets it be
     * remembered again. Marking is also an undo: whatever rule was already saved for
     * that name goes with it, along with the filings it made.
     */
    fun setAskEachTime(merchant: String, on: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val pattern = UserRule.patternOf(merchant)
        if (pattern.isBlank()) return@launch
        val undone = store.setAskEachTime(pattern, on)
        Verbose.info(
            if (on) {
                "\"$pattern\" will be asked about every time, no rule will be saved for it" +
                    if (undone > 0) {
                        " · its rule is gone and $undone record(s) it had filed were answered again"
                    } else ""
            } else {
                "\"$pattern\" can be remembered by a rule again"
            }
        )
        Verbose.flush()
    }

    // ─────────────────────────── custom categories ───────────────────────────

    val categories = store.categories

    fun addCategory(name: String, income: Boolean, color: Int, icon: String) = viewModelScope.launch(Dispatchers.IO) {
        store.addCategory(name.trim(), income, color, icon)
        Verbose.ok("category added by you: \"${name.trim()}\" (${if (income) "income" else "expense"})")
        Verbose.flush()
    }

    fun updateCategory(id: String, name: String, color: Int, icon: String) = viewModelScope.launch(Dispatchers.IO) {
        store.updateCategory(id, name.trim(), color, icon)
        Verbose.info("category updated by you: \"${name.trim()}\"")
        Verbose.flush()
    }

    fun deleteCategory(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val name = Categories.byId(id).name
        store.deleteCategory(id)
        Verbose.info("category deleted by you: \"$name\" · its transactions moved to the default category")
        Verbose.flush()
    }

    // ─────────────────────────── review ───────────────────────────

    fun dismissReview(item: ReviewItem, alsoSimilar: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val extra = store.dismissReview(item, smart = alsoSimilar)
        Verbose.info(
            "review item dismissed by you (${item.sender}, ${item.reason})" +
                if (alsoSimilar) {
                    " · remembered: $extra similar message(s) dismissed with it, future ones " +
                        "will be auto-dismissed (restore any time in Review)"
                } else ""
        )
        Verbose.flush()
    }

    fun restoreReview(item: ReviewItem) = viewModelScope.launch(Dispatchers.IO) {
        val extra = store.restoreReview(item)
        Verbose.info(
            "review item restored by you" +
                if (extra > 0) " together with $extra similar message(s), that kind is no longer auto-dismissed" else ""
        )
        Verbose.flush()
    }

    fun resolveReview(
        item: ReviewItem,
        amountMinor: Long,
        currency: String,
        type: TxnType,
        merchant: String?,
        categoryId: String,
        fromAccountId: String?,
        toAccountId: String?,
        learnSimilar: Boolean = true,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val txn = Txn(
            id = "man-${item.id}",
            atMillis = item.atMillis,
            amountMinor = amountMinor,
            currency = currency,
            type = type,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            merchant = merchant,
            sender = item.sender,
            body = item.body,
            categoryId = categoryId,
            categorySource = "user",
            confidence = 100,
            manual = true,
        )
        store.resolveReview(item.id, txn, learn = learnSimilar)
        Verbose.ok(
            "review resolved by you: recorded ${Money.format(amountMinor, currency)} (${Categories.byId(categoryId).name})" +
                if (learnSimilar) " · remembered: similar messages will always reach Review" else ""
        )
        Verbose.flush()
    }

    fun addManual(
        amountMinor: Long,
        currency: String,
        type: TxnType,
        merchant: String?,
        categoryId: String,
        fromAccountId: String?,
        toAccountId: String?,
        atMillis: Long = System.currentTimeMillis(),
    ) = viewModelScope.launch(Dispatchers.IO) {
        val txn = Txn(
            id = "man-${UUID.randomUUID().toString().take(8)}",
            atMillis = atMillis,
            amountMinor = amountMinor,
            currency = currency,
            type = type,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            merchant = merchant,
            sender = "manual entry",
            body = "Added by you",
            categoryId = categoryId,
            categorySource = "user",
            confidence = 100,
            manual = true,
        )
        store.addManual(txn)
        Verbose.ok("manual transaction added: ${Money.format(amountMinor, currency)} (${Categories.byId(categoryId).name})")
        Verbose.flush()
    }

    fun wipeAll() = viewModelScope.launch(Dispatchers.IO) {
        store.wipe()
        prefs.wipe()
        _budgetsOn.value = prefs.budgetsEnabled
        _autoConfirmOn.value = prefs.autoConfirmTransfers
        store.setAutoConfirmTransfers(prefs.autoConfirmTransfers)
        _accountsConfirmed.value = prefs.accountsConfirmed
        Verbose.info("all data and settings wiped by you")
        Verbose.flush()
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val list = txns.value
            val accountsById = accounts.value.associateBy { it.id }
            fun accountName(id: String?) = id?.let { accountsById[it]?.displayName ?: it } ?: ""
            getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                w.appendLine(
                    "id,datetime,type,amount,currency,from_account,to_account,merchant," +
                        "category,sender,description,confidence,manual"
                )
                val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                for (t in list) {
                    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(t.atMillis), ZoneId.systemDefault()).format(fmt)
                    w.appendLine(
                        listOf(
                            t.id, dt, t.type.name,
                            Money.toMajor(t.amountMinor, t.currency).toPlainString(), t.currency,
                            csv(accountName(t.fromAccountId)), csv(accountName(t.toAccountId)),
                            csv(t.merchant ?: ""), csv(Categories.byId(t.categoryId).name),
                            csv(t.sender), csv(t.body), t.confidence.toString(), t.manual.toString(),
                        ).joinToString(",")
                    )
                }
            }
            Verbose.ok("exported ${list.size} transaction(s) to the CSV file you picked, stayed on your device")
        } catch (e: Exception) {
            Verbose.fail("CSV export failed: ${e.message}")
        }
        Verbose.flush()
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
}
