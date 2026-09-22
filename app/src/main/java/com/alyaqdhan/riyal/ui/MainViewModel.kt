package com.alyaqdhan.riyal.ui

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.content.Intent
import android.os.Environment
import androidx.core.net.toUri
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.RiyalApp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.AccountDiscovery
import com.alyaqdhan.riyal.data.BudgetPlan
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.ScanEngine
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.ScanSummary
import com.alyaqdhan.riyal.data.TransferProposal
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.data.UpdateApi
import com.alyaqdhan.riyal.data.Updates
import com.alyaqdhan.riyal.data.UserRule
import com.alyaqdhan.riyal.ui.compose.TimeSlice
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
        combine(store.txns, store.archivedIds, store.deferredIds) { txns, archived, deferred ->
            Stats.unfiled(txns, archived, deferred).size
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Records left under Other for now. They come back on the next scan. */
    val deferredIds = store.deferredIds

    /** Accounts were proposed from SMS but the user hasn't checked them yet. */
    private val _accountsConfirmed = MutableStateFlow(prefs.accountsConfirmed)
    val accountsNeedConfirming: StateFlow<Boolean> =
        combine(store.accounts, _accountsConfirmed) { accounts, confirmed ->
            accounts.isNotEmpty() && !confirmed
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ─────────────────────────── scanning ───────────────────────────

    sealed interface ScanState {
        data object Idle : ScanState
        data class Running(
            val processed: Int,
            val total: Int,
            @param:StringRes val phase: Int = R.string.scan_phase_reading,
            @param:StringRes val noun: Int = R.string.scan_noun_messages,
        ) : ScanState
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
                if (granted) R.string.log_perm_granted else R.string.log_perm_denied
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
            Verbose.info(R.string.log_scan_on_launch)
            Verbose.flush()
            startScan(showSheet = false)
        }
    }

    /**
     * @param full read the whole range again instead of only what is new. Reserved for
     *   the user asking for it in Settings: it is the pass that reconciles records with
     *   an inbox that has had messages deleted from it, and on a large inbox it is the
     *   slow one. Launch and pull-to-refresh are always incremental.
     */
    fun startScan(showSheet: Boolean = true, full: Boolean = false) {
        if (_scanState.value is ScanState.Running) {
            if (showSheet) scanSheetVisible.value = true
            return
        }
        refreshPermission()
        if (!_hasSmsPermission.value) {
            Verbose.fail(R.string.log_scan_no_permission)
            Verbose.flush()
            _scanState.value = ScanState.Failed("SMS reading permission is not granted")
            if (showSheet) scanSheetVisible.value = true
            return
        }
        _scanState.value = ScanState.Running(0, 0)
        // Set both ways. Leaving it alone let a request from a screen that no longer
        // renders the sheet sit there until some later screen did, which is a sheet
        // about a scan the user finished with minutes ago.
        scanSheetVisible.value = showSheet
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summary = ScanEngine(getApplication(), prefs, store).run(full = full) { p ->
                    _scanState.value = ScanState.Running(p.processed, p.total, p.phase, p.noun)
                }
                prefs.lastScanAt = summary.at
                _scanState.value = ScanState.Done(summary)
            } catch (e: SecurityException) {
                Verbose.fail(R.string.log_scan_aborted_permission)
                Verbose.flush()
                _scanState.value = ScanState.Failed(
                    getApplication<Application>().getString(R.string.scan_permission_refused)
                )
            } catch (e: Exception) {
                Verbose.fail(R.string.log_scan_failed, e.javaClass.simpleName, e.message)
                Verbose.flush()
                val unknown = getApplication<Application>().getString(R.string.scan_unknown_error)
                _scanState.value = ScanState.Failed(e.message ?: unknown)
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
            if (existing) R.string.log_account_updated else R.string.log_account_added,
            account.displayName,
            Money.format(account.openingBalanceMinor, account.currency),
        )
        Verbose.flush()
    }

    fun deleteAccount(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val name = accounts.value.firstOrNull { it.id == id }?.displayName ?: id
        store.deleteAccount(id)
        Verbose.info(R.string.log_account_deleted, name)
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
            Verbose.ok(
                R.string.log_accounts_confirmed,
                Verbose.count(R.plurals.count_accounts, accounts.value.size),
            )
            Verbose.flush()
        }
    }

    fun setTxnAccount(txn: Txn, accountId: String) = viewModelScope.launch(Dispatchers.IO) {
        store.setTxnAccount(txn.id, accountId)
        val name = accounts.value.firstOrNull { it.id == accountId }?.displayName ?: accountId
        Verbose.ok(
            R.string.log_account_set,
            Money.format(txn.amountMinor, txn.currency),
            name,
        )
        Verbose.flush()
    }

    // ─────────────────────────── transfers ───────────────────────────

    fun acceptTransfer(proposal: TransferProposal) = viewModelScope.launch(Dispatchers.IO) {
        store.acceptTransfer(proposal)
        Verbose.ok(
            R.string.log_transfer_confirmed,
            Money.format(proposal.amountMinor, proposal.currency),
        )
        Verbose.flush()
    }

    fun rejectTransfer(proposal: TransferProposal) = viewModelScope.launch(Dispatchers.IO) {
        store.rejectTransfer(proposal)
        Verbose.info(R.string.log_transfer_rejected)
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
                    if (v) R.string.log_auto_confirm_on else R.string.log_auto_confirm_off
                )
                Verbose.flush()
            }
        }

    private val _autoConfirmOn = MutableStateFlow(prefs.autoConfirmTransfers)
    val autoConfirmOn: StateFlow<Boolean> = _autoConfirmOn

    fun splitTransfer(txn: Txn) = viewModelScope.launch(Dispatchers.IO) {
        store.splitTransfer(txn)
        Verbose.info(R.string.log_transfer_split)
        Verbose.flush()
    }

    fun markAsTransfer(txn: Txn, fromAccountId: String?, toAccountId: String?) =
        viewModelScope.launch(Dispatchers.IO) {
            store.markAsTransfer(txn, fromAccountId, toAccountId)
            Verbose.ok(
                R.string.log_marked_transfer,
                Money.format(txn.amountMinor, txn.currency),
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

    // ─────────────────────────── updates ───────────────────────────

    /**
     * What the last look at GitHub found.
     *
     * The first version of this kept only a release that was newer and threw away every
     * other answer, which made the row look broken in the two cases that are not rare at
     * all: tapping Check now while already on the latest did nothing visible, and the
     * release notes were unreachable unless an update happened to be waiting. The answer
     * is worth keeping whichever way it came out.
     */
    sealed interface UpdateState {
        /** Nothing has been asked yet in this session, and nothing was remembered. */
        data object Unknown : UpdateState
        /** A request is in flight. Only ever true because someone tapped. */
        data object Checking : UpdateState
        /** GitHub has a newer one. [release] is what Download acts on. */
        data class Available(val release: Updates.Release) : UpdateState
        /** GitHub answered, and what it has is not newer. Kept for its notes. */
        data class UpToDate(val release: Updates.Release) : UpdateState
        /** Offline, rate-limited, no releases published, a body that would not parse. */
        data object Unreachable : UpdateState
    }

    private val _updateState = MutableStateFlow<UpdateState>(
        // A remembered answer means the notes are on the (i) the moment Settings opens,
        // rather than only after a check the user has no reason to know they must run.
        prefs.lastReleaseTag?.let { tag ->
            UpdateState.UpToDate(
                Updates.Release(
                    tag = tag,
                    notes = prefs.lastReleaseNotes,
                    apkUrl = null, apkName = null, apkBytes = 0L,
                )
            )
        } ?: UpdateState.Unknown
    )
    val updateState: StateFlow<UpdateState> = _updateState

    /** The release to download, which is only ever one that is actually newer. */
    val update: StateFlow<Updates.Release?> =
        _updateState.map { (it as? UpdateState.Available)?.release }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Asks GitHub whether there is a newer release, at most once a day.
     *
     * A check that fails - offline, no releases yet, a rate limit - writes a line in the
     * verbose log and changes nothing on screen, because an update check nobody asked
     * for is not something to interrupt anyone about. [force] is the user tapping "Check
     * now", which skips the throttle and is the only path that a person is waiting on.
     *
     * A check that finds a newer release does say so: Home shows a card once
     * [updateState] becomes [UpdateState.Available]. Until that existed the answer lived
     * only in Settings, so the way to find out a release was out was to go looking for
     * one. It is still only ever a card - nothing is downloaded, and nothing installs.
     */
    fun checkForUpdate(currentVersion: String, force: Boolean = false) =
        viewModelScope.launch(Dispatchers.IO) {
            if (!Updates.shouldCheck(System.currentTimeMillis(), prefs.lastUpdateCheckAt, force)) {
                return@launch
            }
            if (force) _updateState.value = UpdateState.Checking

            val release = UpdateApi.latestRelease()
            if (release == null) {
                // Nothing is stamped here on purpose, so the next launch tries again.
                // A background check that failed stays invisible, as before. One the
                // user asked for has someone waiting on it, and silence reads as a
                // broken button, so that one says it could not ask.
                if (force) _updateState.value = UpdateState.Unreachable
                Verbose.flush()
                return@launch
            }
            // The day starts from an answer, not from an attempt. Stamping it above
            // meant a launch with no signal - on a plane, on the way in to work - spent
            // the day's one check on nothing, and a release published that morning went
            // unnoticed until the next day.
            prefs.lastUpdateCheckAt = System.currentTimeMillis()
            prefs.lastReleaseTag = release.tag
            prefs.lastReleaseNotes = release.notes
            if (Updates.isNewer(release.tag, currentVersion)) {
                _updateState.value = UpdateState.Available(release)
                Verbose.ok(
                    if (release.hasApk) R.string.log_update_newer_apk
                    else R.string.log_update_newer_no_apk,
                    release.tag,
                    currentVersion,
                )
            } else {
                _updateState.value = UpdateState.UpToDate(release)
                Verbose.info(R.string.log_update_latest, currentVersion, release.tag)
            }
            Verbose.flush()
        }

    /**
     * Puts the release's APK in the phone's public Downloads folder and opens the
     * Downloads screen so the user can tap it themselves.
     *
     * DownloadManager does the work: progress, retries, its own notification, and no
     * storage permission at all for this destination. What it deliberately does not do
     * is install anything. The app asks for neither REQUEST_INSTALL_PACKAGES nor a
     * FileProvider, so the last step is a person choosing to install a file they can
     * see - which is also the step where they will be told, by Android, if the APK is
     * signed with a different key than the copy they already have.
     *
     * Returns false when there is nothing to download, so the caller can say so rather
     * than opening an empty Downloads screen.
     */
    fun downloadUpdate(): Boolean {
        val release = update.value ?: return false
        val url = release.apkUrl ?: run {
            Verbose.fail(R.string.log_update_no_apk, release.tag)
            Verbose.flush()
            return false
        }
        val context = getApplication<Application>()
        return try {
            val name = release.apkName ?: "Riyal-${release.tag}.apk"
            val request = DownloadManager.Request(url.toUri())
                .setTitle(name)
                .setDescription("Riyal ${release.tag}")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            val manager = context.getSystemService(DownloadManager::class.java)
            manager.enqueue(request)
            Verbose.ok(R.string.log_download_started, name)
            Verbose.flush()
            openDownloads(context)
            true
        } catch (e: Exception) {
            Verbose.fail(R.string.log_download_failed, e.javaClass.simpleName)
            Verbose.flush()
            false
        }
    }

    /** The system Downloads screen, where the file will appear. */
    private fun openDownloads(context: Application) {
        try {
            context.startActivity(
                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            // A phone with no Downloads UI. The file still arrives; the notification
            // DownloadManager posts is another way to reach it.
            Verbose.info(R.string.log_no_downloads_screen)
            Verbose.flush()
        }
    }

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
            Verbose.ok(R.string.log_budget_created, label)
            Verbose.flush()
        }

    fun setBudgetLine(planId: String, categoryId: String, minor: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            store.setBudgetLine(planId, categoryId, minor)
            if (minor > 0) {
                Verbose.info(R.string.log_budget_set, Categories.byId(categoryId).name, minor)
            } else {
                Verbose.info(R.string.log_budget_removed, Categories.byId(categoryId).name)
            }
            Verbose.flush()
        }

    fun deleteBudget(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val label = budgets.value.firstOrNull { it.id == id }?.label ?: id
        store.deleteBudget(id)
        Verbose.info(R.string.log_budget_deleted, label)
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
            Verbose.info(R.string.log_budget_period, plan.label, label)
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
            Verbose.ok(R.string.log_budget_copied, source.label, label)
            Verbose.flush()
        }

    // ─────────────────────────── user edits ───────────────────────────

    fun setCategory(txn: Txn, categoryId: String, alsoRulePattern: String?) =
        viewModelScope.launch(Dispatchers.IO) {
            store.setCategory(txn.id, categoryId)
            Verbose.ok(
                R.string.log_category_set,
                txn.merchant ?: txn.sender,
                Categories.byId(categoryId).name,
            )
            if (!alsoRulePattern.isNullOrBlank()) {
                val changed = store.addRule(UserRule(alsoRulePattern.trim().lowercase(), categoryId))
                Verbose.ok(
                    R.string.log_rule_saved_recat,
                    alsoRulePattern.trim().lowercase(),
                    Categories.byId(categoryId).name,
                    Verbose.count(R.plurals.count_past_transactions, changed),
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
    fun fileMerchant(
        group: Stats.MerchantGroup,
        categoryId: String,
        txnIds: Collection<String> = group.txnIds,
    ) =
        viewModelScope.launch(Dispatchers.IO) {
            if (txnIds.isEmpty()) return@launch
            val pattern = UserRule.patternOf(group.merchant)
            val filed = store.setCategories(txnIds, categoryId)
            Verbose.ok(
                R.string.log_filed,
                Verbose.count(R.plurals.count_records, txnIds.size),
                group.merchant,
                Categories.byId(categoryId).name,
            )
            // A rule is a statement about the name, so it may only be saved when the
            // answer was about the name. Picking four of a merchant's six records means
            // the other two are something else - a rule here would file them wrong, and
            // sweep in every future message besides.
            val wholeName = txnIds.toSet().containsAll(group.txnIds)
            when {
                !wholeName -> Verbose.info(
                    R.string.log_no_rule_partial,
                    pattern,
                    txnIds.size,
                    Verbose.count(R.plurals.count_records, group.count),
                )
                pattern in store.askEachTime.value ->
                    Verbose.info(R.string.log_no_rule_ask, pattern)
                else -> {
                    val byRule = store.addRule(UserRule(pattern, categoryId))
                    Verbose.ok(
                        R.string.log_rule_saved_filed,
                        pattern,
                        Categories.byId(categoryId).name,
                        Verbose.count(R.plurals.count_records, filed),
                        byRule,
                    )
                }
            }
            Verbose.flush()
        }

    /**
     * Clears the backlog without answering it. The records stay under the category they
     * fell back to, no rule is saved and nothing is marked as the user's answer, so the
     * next scan puts every one of them back - which is what the screen promises before
     * it does this.
     */
    fun deferAll(txnIds: Collection<String>) = viewModelScope.launch(Dispatchers.IO) {
        val set = store.deferCategory(txnIds)
        Verbose.info(
            R.string.log_left_under_other,
            Verbose.count(R.plurals.count_records, set),
        )
        Verbose.flush()
    }

    /**
     * The period Analysis is reading. It lives here rather than in the screen because
     * opening a category destroys that screen's composition, and coming back rebuilt it
     * on the current month - so the answer you had gone to look up was gone by the time
     * you returned to it.
     */
    private val _analysisSlice = MutableStateFlow(TimeSlice.thisMonth())
    val analysisSlice: StateFlow<TimeSlice> = _analysisSlice

    fun setAnalysisSlice(slice: TimeSlice) {
        _analysisSlice.value = slice
    }

    /**
     * The period Home is showing. Held here for the same reason the Analysis one is: a
     * rotation, or walking to another tab and back, should not silently return the
     * dashboard to this month while the user was reading an earlier one.
     *
     * Separate from [analysisSlice] on purpose. They are two different questions being
     * asked at once, and having one move because the other did was never wanted.
     */
    private val _homeSlice = MutableStateFlow(TimeSlice.thisMonth())
    val homeSlice: StateFlow<TimeSlice> = _homeSlice

    fun setHomeSlice(slice: TimeSlice) {
        _homeSlice.value = slice
    }

    val archivedIds = store.archivedIds

    fun archiveTxn(txn: Txn, archive: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        store.archiveTxn(txn, archive)
        Verbose.info(
            if (archive) R.string.log_archived else R.string.log_unarchived,
            Money.format(txn.amountMinor, txn.currency),
        )
        Verbose.flush()
    }

    fun ignoreTxn(txn: Txn) = viewModelScope.launch(Dispatchers.IO) {
        store.ignoreTxn(txn)
        Verbose.info(
            Verbose.str(
                R.string.log_removed,
                Money.format(txn.amountMinor, txn.currency),
                txn.sender,
            ) + if (!txn.manual) Verbose.str(R.string.log_removed_ignored) else ""
        )
        Verbose.flush()
    }

    fun addRule(pattern: String, categoryId: String) = viewModelScope.launch(Dispatchers.IO) {
        val key = UserRule.patternOf(pattern)
        // Reachable now that a keyword can be typed rather than only picked up from a
        // merchant: the store refuses a rule for a name marked "ask me every time", and
        // reporting "rule saved" for one it dropped would be a lie in the log.
        if (key in store.askEachTime.value) {
            Verbose.info(R.string.log_no_rule_ask_short, key)
            Verbose.flush()
            return@launch
        }
        val changed = store.addRule(UserRule(key, categoryId))
        Verbose.ok(
            R.string.log_rule_saved_recat,
            key,
            Categories.byId(categoryId).name,
            Verbose.count(R.plurals.count_transactions, changed),
        )
        Verbose.flush()
    }

    fun removeRule(pattern: String) = viewModelScope.launch(Dispatchers.IO) {
        val changed = store.removeRule(pattern)
        Verbose.info(
            Verbose.str(R.string.log_rule_removed, pattern) +
                if (changed > 0) {
                    Verbose.str(
                        R.string.log_rule_removed_undone,
                        Verbose.count(R.plurals.count_records, changed),
                    )
                } else {
                    ""
                }
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
                Verbose.str(R.string.log_ask_each_on, pattern) +
                    if (undone > 0) {
                        Verbose.str(
                            R.string.log_ask_each_undone,
                            Verbose.count(R.plurals.count_records, undone),
                        )
                    } else {
                        ""
                    }
            } else {
                Verbose.str(R.string.log_ask_each_off, pattern)
            }
        )
        Verbose.flush()
    }

    // ─────────────────────────── custom categories ───────────────────────────

    val categories = store.categories

    fun addCategory(name: String, income: Boolean, color: Int, icon: String) = viewModelScope.launch(Dispatchers.IO) {
        store.addCategory(name.trim(), income, color, icon)
        Verbose.ok(
            R.string.log_category_added,
            name.trim(),
            Verbose.str(if (income) R.string.log_word_in else R.string.log_word_out),
        )
        Verbose.flush()
    }

    fun updateCategory(id: String, name: String, color: Int, icon: String) = viewModelScope.launch(Dispatchers.IO) {
        store.updateCategory(id, name.trim(), color, icon)
        Verbose.info(R.string.log_category_updated, name.trim())
        Verbose.flush()
    }

    fun deleteCategory(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val name = Categories.byId(id).name
        store.deleteCategory(id)
        Verbose.info(R.string.log_category_deleted, name)
        Verbose.flush()
    }

    // ─────────────────────────── review ───────────────────────────

    fun dismissReview(item: ReviewItem, alsoSimilar: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val extra = store.dismissReview(item, smart = alsoSimilar)
        Verbose.info(
            Verbose.str(R.string.log_review_dismissed, item.sender, item.reason) +
                if (alsoSimilar) {
                    Verbose.str(
                        R.string.log_review_dismissed_smart,
                        Verbose.count(R.plurals.count_similar_messages, extra),
                    )
                } else {
                    ""
                }
        )
        Verbose.flush()
    }

    fun restoreReview(item: ReviewItem) = viewModelScope.launch(Dispatchers.IO) {
        val extra = store.restoreReview(item)
        Verbose.info(
            Verbose.str(R.string.log_review_restored) +
                if (extra > 0) {
                    Verbose.str(
                        R.string.log_review_restored_extra,
                        Verbose.count(R.plurals.count_similar_messages, extra),
                    )
                } else {
                    ""
                }
        )
        Verbose.flush()
    }

    /**
     * Adopts a word from a message as a gate keyword, so that kind of message is read
     * automatically from the next scan instead of being asked about again.
     *
     * Only ever called because the user tapped it. The gate decides what the app is
     * allowed to look at at all, and a word added to it silently would change how the
     * whole inbox is read on evidence of one message.
     */
    fun learnKeyword(word: String, direction: Direction) {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return
        if (direction == Direction.EXPENSE) {
            if (w in prefs.expenseKeywords) return
            prefs.expenseKeywords = prefs.expenseKeywords + w
        } else {
            if (w in prefs.incomeKeywords) return
            prefs.incomeKeywords = prefs.incomeKeywords + w
        }
        Verbose.ok(
            R.string.log_keyword_added,
            w,
            Verbose.str(
                if (direction == Direction.EXPENSE) R.string.log_word_out
                else R.string.log_word_in
            ),
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
            Verbose.str(
                R.string.log_review_resolved,
                Money.format(amountMinor, currency),
                Categories.byId(categoryId).name,
            ) + if (learnSimilar) Verbose.str(R.string.log_review_resolved_learn) else ""
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
            sender = getApplication<Application>().getString(R.string.manual_entry_sender),
            body = getApplication<Application>().getString(R.string.manual_entry_body),
            categoryId = categoryId,
            categorySource = "user",
            confidence = 100,
            manual = true,
        )
        store.addManual(txn)
        Verbose.ok(
            R.string.log_manual_added,
            Money.format(amountMinor, currency),
            Categories.byId(categoryId).name,
        )
        Verbose.flush()
    }

    fun wipeAll() = viewModelScope.launch(Dispatchers.IO) {
        store.wipe()
        prefs.wipe()
        _budgetsOn.value = prefs.budgetsEnabled
        _autoConfirmOn.value = prefs.autoConfirmTransfers
        store.setAutoConfirmTransfers(prefs.autoConfirmTransfers)
        _accountsConfirmed.value = prefs.accountsConfirmed
        Verbose.info(R.string.log_wiped)
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
            Verbose.ok(
                R.string.log_exported,
                Verbose.count(R.plurals.count_transactions, list.size),
            )
        } catch (e: Exception) {
            Verbose.fail(R.string.log_export_failed, e.message)
        }
        Verbose.flush()
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

}
