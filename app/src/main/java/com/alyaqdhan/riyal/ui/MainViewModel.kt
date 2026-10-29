package com.alyaqdhan.riyal.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.RiyalApp
import com.alyaqdhan.riyal.core.Currencies
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.ApkInstaller
import com.alyaqdhan.riyal.data.reasonText
import com.alyaqdhan.riyal.data.AccountDiscovery
import com.alyaqdhan.riyal.data.BudgetPlan
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.MessageKind
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.ScanEngine
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.ScanSummary
import com.alyaqdhan.riyal.data.Skeleton
import com.alyaqdhan.riyal.data.TransferProposal
import com.alyaqdhan.riyal.data.Tutor
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.data.UpdateApi
import com.alyaqdhan.riyal.data.Updates
import com.alyaqdhan.riyal.data.UserRule
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    /**
     * Auto-scan when the app opens (Settings toggle, on by default). Quiet: no sheet pops up.
     *
     * @param fresh the app was opened, rather than its screen rebuilt (rotated, or the
     *   language changed). This view model lives as long as the app does, so leaving the
     *   app and opening it again is a launch it has to be told about.
     */
    fun autoScanOnLaunch(fresh: Boolean) {
        if (launchScanDone && !fresh) return
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
            _scanState.value = ScanState.Failed(
                getApplication<Application>().getString(R.string.scan_permission_missing)
            )
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
                // The figures roll again, changed or not: a refresh should be seen to land.
                com.alyaqdhan.riyal.ui.compose.Rolls.replay()
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

    /** The two were one account all along: [from] and its records fold into [into]. */
    fun mergeAccount(from: Account, into: Account) = viewModelScope.launch(Dispatchers.IO) {
        store.mergeAccount(from.id, into.id)
        Verbose.ok(R.string.log_account_merged, from.displayName, into.displayName)
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
     * Every answer is kept, not only a newer release: tapping Check now while already on
     * the latest has to say so, and the notes of the version you have are worth reading
     * whether or not anything is waiting.
     */
    sealed interface UpdateState {
        /** Nothing has been asked yet, and nothing was remembered. */
        data object Unknown : UpdateState
        /** A request is in flight. Only ever shown because someone tapped. */
        data object Checking : UpdateState
        /** A newer release on the chosen channel, not skipped. */
        data class Available(val release: Updates.Release) : UpdateState
        /** GitHub answered, and nothing on the channel is newer. */
        data class UpToDate(val release: Updates.Release?) : UpdateState
        /** Offline, rate-limited, nothing published, a body that would not parse. */
        data object Unreachable : UpdateState
    }

    private val _updateState = MutableStateFlow<UpdateState>(
        // A remembered answer means the notes are there the moment Settings opens.
        prefs.lastReleaseTag?.let { tag ->
            UpdateState.UpToDate(Updates.Release(tag = tag, notes = prefs.lastReleaseNotes))
        } ?: UpdateState.Unknown
    )
    val updateState: StateFlow<UpdateState> = _updateState

    private val _lastUpdateCheck = MutableStateFlow(prefs.lastUpdateCheckAt)
    val lastUpdateCheck: StateFlow<Long> = _lastUpdateCheck

    /** Every release GitHub listed on its last answer: the changelog. Null until asked. */
    private val _releases = MutableStateFlow<List<Updates.Release>?>(null)
    val releases: StateFlow<List<Updates.Release>?> = _releases

    private val _releasesFailed = MutableStateFlow(false)
    val releasesFailed: StateFlow<Boolean> = _releasesFailed

    /**
     * The release the update dialog is open on. A check finds a new version and opens it
     * once for that version; after that the update waits in Settings until it is wanted.
     */
    private val _updatePrompt = MutableStateFlow<Updates.Release?>(null)
    val updatePrompt: StateFlow<Updates.Release?> = _updatePrompt

    fun openUpdatePrompt(release: Updates.Release) {
        _updatePrompt.value = release
    }

    fun dismissUpdatePrompt() {
        _updatePrompt.value = null
    }

    private val _checkOnStart = MutableStateFlow(prefs.checkUpdatesOnStart)
    val checkOnStart: StateFlow<Boolean> = _checkOnStart

    fun setCheckOnStart(on: Boolean) {
        prefs.checkUpdatesOnStart = on
        _checkOnStart.value = on
        Verbose.info(
            Verbose.str(
                R.string.log_setting_changed,
                Verbose.str(if (on) R.string.log_note_update_check_on else R.string.log_note_update_check_off),
            )
        )
        Verbose.flush()
    }

    private val _includeBeta = MutableStateFlow(prefs.updateIncludeBeta)
    val includeBeta: StateFlow<Boolean> = _includeBeta

    private val _skipped = MutableStateFlow(prefs.skippedUpdates)
    val skippedUpdates: StateFlow<Set<String>> = _skipped

    /** Switching channels re-decides from the answer already in hand; no new request. */
    fun setIncludeBeta(on: Boolean, currentVersion: String) {
        prefs.updateIncludeBeta = on
        _includeBeta.value = on
        Verbose.info(
            Verbose.str(
                R.string.log_setting_changed,
                Verbose.str(if (on) R.string.log_note_beta_on else R.string.log_note_beta_off),
            )
        )
        _releases.value?.let { decide(it, currentVersion, prompt = false) }
        Verbose.flush()
    }

    /**
     * Asks GitHub for its releases, at most once a day unless [force]d by a tap.
     *
     * A failed check that nobody asked for changes nothing on screen: it is a line in the
     * log, and the next launch tries again. [onStart] is the launch's own check, which
     * the "check on start" switch can turn off entirely.
     */
    fun checkForUpdate(currentVersion: String, force: Boolean = false, onStart: Boolean = false) =
        viewModelScope.launch(Dispatchers.IO) {
            if (onStart && !prefs.checkUpdatesOnStart) return@launch
            if (!Updates.shouldCheck(System.currentTimeMillis(), prefs.lastUpdateCheckAt, force)) {
                return@launch
            }
            if (force) _updateState.value = UpdateState.Checking
            val askedAt = SystemClock.uptimeMillis()

            val list = UpdateApi.releases()
            // A tap is answered after a second at least: a check that came back at once
            // flashed its spinner too quickly to read as having checked anything.
            if (force) delay(MIN_CHECK_MS - (SystemClock.uptimeMillis() - askedAt))
            if (list == null) {
                // Nothing is stamped on purpose, so the next launch tries again.
                if (force) _updateState.value = UpdateState.Unreachable
                Verbose.flush()
                return@launch
            }
            // The day starts from an answer, not from an attempt.
            val now = System.currentTimeMillis()
            prefs.lastUpdateCheckAt = now
            _lastUpdateCheck.value = now
            _releases.value = list
            _releasesFailed.value = false
            decide(list, currentVersion, prompt = true, asked = force)
            Verbose.flush()
        }

    private fun decide(
        list: List<Updates.Release>,
        currentVersion: String,
        prompt: Boolean,
        asked: Boolean = false,
    ) {
        val newest = Updates.newest(list, prefs.updateIncludeBeta)
        newest?.let {
            prefs.lastReleaseTag = it.tag
            prefs.lastReleaseNotes = it.notes
        }
        val offer = Updates.pick(list, currentVersion, prefs.updateIncludeBeta, prefs.skippedUpdates)
        if (offer == null) {
            _updateState.value = UpdateState.UpToDate(newest)
            Verbose.info(R.string.log_update_latest, currentVersion, newest?.tag ?: "-")
            return
        }
        _updateState.value = UpdateState.Available(offer)
        Verbose.ok(
            if (offer.hasApk) R.string.log_update_newer_apk else R.string.log_update_newer_no_apk,
            offer.tag,
            currentVersion,
        )
        // The dialog opens once per version on its own, and every time someone asks.
        val seen = prefs.updatePromptedTag?.let { Updates.sameVersion(it, offer.tag) } == true
        if (prompt && (asked || !seen)) {
            prefs.updatePromptedTag = offer.tag
            _updatePrompt.value = offer
        }
    }

    /** The changelog page asking for the list when no check has fetched it yet. */
    fun loadReleases() = viewModelScope.launch(Dispatchers.IO) {
        _releasesFailed.value = false
        val list = UpdateApi.releases()
        if (list == null) _releasesFailed.value = true else _releases.value = list
        Verbose.flush()
    }

    /** "Not this version." A later release is still offered. */
    fun skipUpdate(release: Updates.Release) {
        prefs.skippedUpdates = prefs.skippedUpdates + release.tag
        _skipped.value = prefs.skippedUpdates
        cancelDownload()
        _updatePrompt.value = null
        _updateState.value = UpdateState.UpToDate(release)
        Verbose.info(R.string.log_update_skipped, release.tag)
        Verbose.flush()
    }

    fun clearSkippedUpdates() {
        prefs.skippedUpdates = emptySet()
        _skipped.value = emptySet()
    }

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val tag: String, val fraction: Float) : DownloadState
        data class Ready(val tag: String, val file: File) : DownloadState
        data class Failed(val tag: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download
    private var downloadJob: Job? = null

    /**
     * Fetches the release's APK into the app's own cache, with the progress on screen.
     * Nothing installs at the end of it: [install] hands the file to Android, which asks.
     */
    fun startDownload(release: Updates.Release) {
        if (_download.value is DownloadState.Downloading) return
        val asset = release.apk ?: run {
            Verbose.fail(R.string.log_update_no_apk, release.tag)
            Verbose.flush()
            _download.value = DownloadState.Failed(release.tag)
            return
        }
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _download.value = DownloadState.Downloading(release.tag, 0f)
            val context = getApplication<Application>()
            ApkInstaller.clearOld(context)
            val name = asset.name.replace(UNSAFE_FILE_CHARS, "_").ifBlank { "Riyal.apk" }
            val dest = File(ApkInstaller.updatesDir(context), name)
            val ok = UpdateApi.download(asset, dest) { fraction ->
                _download.value = DownloadState.Downloading(release.tag, fraction)
            }
            _download.value = if (ok) DownloadState.Ready(release.tag, dest) else DownloadState.Failed(release.tag)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        if (_download.value is DownloadState.Downloading) _download.value = DownloadState.Idle
    }

    enum class InstallResult { STARTED, NEEDS_PERMISSION, FAILED }

    /**
     * Opens Android's installer on a finished download. Without the "install unknown
     * apps" permission it opens nothing and says so, so the caller can take the user to
     * the switch and back.
     */
    fun install(file: File): InstallResult {
        val context = getApplication<Application>()
        if (!ApkInstaller.canInstall(context)) return InstallResult.NEEDS_PERMISSION
        val started = ApkInstaller.install(context, file)
        Verbose.info(if (started) R.string.log_install_opened else R.string.log_install_failed)
        Verbose.flush()
        return if (started) InstallResult.STARTED else InstallResult.FAILED
    }

    fun openInstallPermission() = ApkInstaller.openInstallPermission(getApplication())

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
            Verbose.str(R.string.log_review_dismissed, item.sender, reasonText(item.reason)) +
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
        val counted = countedIn(amountMinor, currency, if (type == TxnType.INCOME) toAccountId else fromAccountId)
        val txn = Txn(
            id = "man-${item.id}",
            atMillis = item.atMillis,
            amountMinor = counted.amountMinor,
            currency = counted.currency,
            originalAmountMinor = counted.originalAmountMinor,
            originalCurrency = counted.originalCurrency,
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

    // ─────────────────────────── setup ───────────────────────────

    /**
     * The setup's steps: read the inbox, check the banks and accounts it found, answer a
     * few questions about how their messages were read, done.
     */
    enum class SetupStep { READING, ACCOUNTS, QUESTIONS, DONE }

    /** One question: a real message, and what the app made of it. */
    data class SetupQuestion(
        val key: String,
        val sender: String,
        val body: String,
        val atMillis: Long,
        val guess: MessageKind,
        /** The record the message became, when it became one. */
        val txn: Txn?,
        val amountMinor: Long?,
        val currency: String?,
        /** Other messages of the same kind, which the answer decides as well. */
        val similar: Int,
    )

    private val _setupStep = MutableStateFlow(SetupStep.READING)
    val setupStep: StateFlow<SetupStep> = _setupStep

    private val _setupQuestion = MutableStateFlow<SetupQuestion?>(null)
    val setupQuestion: StateFlow<SetupQuestion?> = _setupQuestion

    private val _setupProgress = MutableStateFlow(0f)
    val setupProgress: StateFlow<Float> = _setupProgress

    private val _questionsLeft = MutableStateFlow(0)
    val questionsLeft: StateFlow<Int> = _questionsLeft

    private val _questionsAsked = MutableStateFlow(0)
    val questionsAsked: StateFlow<Int> = _questionsAsked

    private val _taughtCount = MutableStateFlow(0)
    val taughtCount: StateFlow<Int> = _taughtCount

    private var tutor: Tutor? = null
    private var setupJob: Job? = null

    /** One answered question, kept so the user can step back to it and change it. */
    data class SetupAnswer(
        val question: SetupQuestion,
        val kind: MessageKind,
        /** The category the record was filed under, for money in or out. */
        val categoryId: String?,
        /** "Yes", as asked. */
        val confirmed: Boolean,
    )

    // The tutor is rebuilt from these whenever an earlier answer changes: replaying the
    // answers in order gives exactly the state they would have produced.
    private var tutorCandidates: List<Tutor.Candidate> = emptyList()
    private var tutorRight = 0
    private var tutorWrong = 0
    private val setupAnswers = ArrayList<SetupAnswer>()

    /** Which answer is on screen; [setupAnswers]' size when it is a question not yet asked. */
    private var setupCursor = 0

    private val _setupAnswer = MutableStateFlow<SetupAnswer?>(null)

    /** What was answered for the question on screen, when the user stepped back to it. */
    val setupAnswer: StateFlow<SetupAnswer?> = _setupAnswer

    private val _canStepBack = MutableStateFlow(false)
    val canStepBack: StateFlow<Boolean> = _canStepBack

    /**
     * Whether a setup has been started in this process. A page restored after the
     * system reclaimed the app has a fresh view model with nothing under way, and has
     * to start again rather than wait on a read that is not happening.
     */
    val setupActive: Boolean get() = setupJob != null

    /** Whether the setup should be offered on Home: never finished, and messages can be read. */
    private val _setupDone = MutableStateFlow(prefs.setupDone)
    val setupDone: StateFlow<Boolean> = _setupDone

    /**
     * Starts the setup from the top: a full read of the inbox, so the accounts and the
     * questions are about everything, not only what arrived since the last scan.
     */
    fun startSetup() {
        setupJob?.cancel()
        tutor = null
        setupAnswers.clear()
        setupCursor = 0
        _setupAnswer.value = null
        _setupQuestion.value = null
        _taughtCount.value = 0
        _setupStep.value = SetupStep.READING
        setupJob = viewModelScope.launch {
            // Whatever scan is already running finishes first; then the full one.
            scanState.first { it !is ScanState.Running }
            startScan(showSheet = false, full = true)
            scanState.first { it !is ScanState.Running }
            _setupStep.value = SetupStep.ACCOUNTS
        }
    }

    /** The accounts step's "this is right": the questions come next. */
    fun confirmSetupAccounts() {
        confirmAccounts()
        val labels = store.labels.value
        val candidates = store.templates.value
            .filter { it.key !in labels && it.guess != null && it.count > 0 }
            .map {
                // A bank's codes and offers are often its most frequent message, but a
                // misreading there costs no money; the questions start with what does.
                val weight = if (it.guess == MessageKind.NONE) (it.count / 4).coerceAtLeast(1) else it.count
                Tutor.Candidate(it.key, it.sender, weight, it.confidence)
            }
        tutorCandidates = candidates
        tutorRight = prefs.teachRight
        tutorWrong = prefs.teachWrong
        setupAnswers.clear()
        setupCursor = 0
        tutor = Tutor(candidates, tutorRight, tutorWrong)
        _setupStep.value = SetupStep.QUESTIONS
        showSetupQuestion()
    }

    /** Back to the question before the one on screen, with its answer, to change it. */
    fun stepBackSetup() {
        if (setupCursor == 0) return
        setupCursor--
        showSetupQuestion()
    }

    private fun showSetupQuestion() {
        val t = tutor ?: return
        _setupProgress.value = t.progress
        _questionsAsked.value = setupCursor
        _canStepBack.value = setupCursor > 0
        if (setupCursor < setupAnswers.size) {
            val earlier = setupAnswers[setupCursor]
            _setupAnswer.value = earlier
            _setupQuestion.value = earlier.question
            _questionsLeft.value = t.questionsLeft() + (setupAnswers.size - setupCursor)
            return
        }
        _setupAnswer.value = null
        val next = t.next()
        if (next == null) {
            finishSetup()
            return
        }
        _questionsLeft.value = t.questionsLeft()
        val stat = store.templates.value.first { it.key == next.key }
        val txn = txns.value.firstOrNull { it.id == stat.sampleId || stat.sampleId in it.legIds }
        val review = reviews.value.firstOrNull { it.id == stat.sampleId }
        _setupQuestion.value = SetupQuestion(
            key = stat.key,
            sender = stat.sender,
            body = stat.sampleBody,
            atMillis = stat.sampleAt,
            guess = stat.guess ?: MessageKind.OUT,
            txn = txn,
            amountMinor = txn?.amountMinor ?: review?.amountMinor,
            currency = txn?.currency ?: review?.currency,
            similar = (stat.count - 1).coerceAtLeast(0),
        )
    }

    /**
     * An answer. "Right" confirms the reading - and the category, which is then
     * remembered for the shop. "Wrong" carries what it actually was: usually the same
     * kind in another category, sometimes another kind altogether. Either way the answer
     * is about the whole template, so every message written like this one is read that
     * way from now on.
     *
     * Only a wrong kind counts against the reading. A category is about the shop, not
     * about how the bank's message was read, so fixing one confirms the reading as much
     * as a "yes" does - and shouldn't buy more questions.
     *
     * Answering a question the user stepped back to replaces its answer and moves on to
     * the one after it.
     */
    fun answerSetup(right: Boolean, kind: MessageKind? = null, categoryId: String? = null) {
        val q = _setupQuestion.value ?: return
        if (tutor == null) return
        val taught = if (right) q.guess else kind ?: q.guess
        val answer = SetupAnswer(
            question = q,
            kind = taught,
            categoryId = if (right) q.txn?.categoryId else categoryId,
            confirmed = right,
        )
        if (setupCursor < setupAnswers.size) setupAnswers[setupCursor] = answer else setupAnswers += answer
        setupCursor++
        val t = Tutor(tutorCandidates, tutorRight, tutorWrong)
        setupAnswers.forEach { t.answer(it.question.key, wasRight = it.kind == it.question.guess) }
        tutor = t
        prefs.teachRight = t.right
        prefs.teachWrong = t.wrong
        _taughtCount.value = setupAnswers.size
        viewModelScope.launch(Dispatchers.IO) {
            store.setLabel(q.key, taught)
            Verbose.ok(
                R.string.log_taught,
                q.sender,
                Verbose.str(
                    when (taught) {
                        MessageKind.OUT -> R.string.log_word_out
                        MessageKind.IN -> R.string.log_word_in
                        MessageKind.TRANSFER -> R.string.label_transfer
                        MessageKind.NONE -> R.string.kind_none
                    }
                ),
                q.similar,
            )
            val txn = q.txn
            val merchant = txn?.merchant?.takeIf { it.isNotBlank() }
            val confirmed = answer.categoryId
            if (txn != null && confirmed != null && taught.direction != null &&
                !(right && confirmed == Categories.defaultFor(txn.type))
            ) {
                // The category answer is about the shop, so it is remembered as one.
                setCategory(txn, confirmed, merchant?.takeIf { prefs.smartRules })
            }
            Verbose.flush()
        }
        showSetupQuestion()
    }

    /**
     * Ends the setup, early or not. What was taught applies from the next read of the
     * inbox, so one is started now rather than left for the next launch.
     */
    fun finishSetup() {
        prefs.setupDone = true
        _setupDone.value = true
        _setupQuestion.value = null
        _setupAnswer.value = null
        _canStepBack.value = false
        _setupStep.value = SetupStep.DONE
        if (_taughtCount.value > 0) startScan(showSheet = false, full = true)
    }

    /**
     * The user says a sender is not their bank: it is never read again, and the accounts
     * that were made from its messages go with it.
     */
    fun excludeSender(sender: String) = viewModelScope.launch(Dispatchers.IO) {
        val key = sender.trim().lowercase()
        prefs.excludedSenders = prefs.excludedSenders + sender.trim()
        prefs.senderAllowlist = prefs.senderAllowlist.filterNot { it.trim().lowercase() == key }.toSet()
        accounts.value.filter { a -> a.senderIds.any { it.trim().lowercase() == key } }
            .forEach { store.deleteAccount(it.id) }
        Verbose.info(R.string.log_sender_excluded, sender)
        Verbose.flush()
    }

    /** A bank the first read missed: its messages are read from now on. */
    fun includeSender(sender: String) {
        val key = sender.trim().lowercase()
        prefs.excludedSenders = prefs.excludedSenders.filterNot { it.trim().lowercase() == key }.toSet()
        prefs.senderAllowlist = prefs.senderAllowlist + sender.trim()
        Verbose.info(R.string.log_sender_included, sender)
        Verbose.flush()
    }

    /** The setup's accounts step after a sender was added: read again, then back here. */
    fun rereadForSetup() {
        setupJob?.cancel()
        _setupStep.value = SetupStep.READING
        setupJob = viewModelScope.launch {
            scanState.first { it !is ScanState.Running }
            startScan(showSheet = false, full = true)
            scanState.first { it !is ScanState.Running }
            _setupStep.value = SetupStep.ACCOUNTS
        }
    }

    /**
     * Review's "this is money out" outside the setup: the same lesson, about the whole
     * template, and a read of the inbox so every message like it is answered at once.
     */
    fun teach(item: ReviewItem, kind: MessageKind) = viewModelScope.launch(Dispatchers.IO) {
        store.setLabel(Skeleton.of(item.sender, item.body), kind)
        Verbose.ok(
            R.string.log_taught,
            item.sender,
            Verbose.str(
                when (kind) {
                    MessageKind.OUT -> R.string.log_word_out
                    MessageKind.IN -> R.string.log_word_in
                    MessageKind.TRANSFER -> R.string.label_transfer
                    MessageKind.NONE -> R.string.kind_none
                }
            ),
            0,
        )
        Verbose.flush()
        startScan(showSheet = false, full = true)
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
        val counted = countedIn(amountMinor, currency, if (type == TxnType.INCOME) toAccountId else fromAccountId)
        val txn = Txn(
            id = "man-${UUID.randomUUID().toString().take(8)}",
            atMillis = atMillis,
            amountMinor = counted.amountMinor,
            currency = counted.currency,
            originalAmountMinor = counted.originalAmountMinor,
            originalCurrency = counted.originalCurrency,
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

    /** An amount as a record counts it, and what it was charged in when that differs. */
    private data class Counted(
        val amountMinor: Long,
        val currency: String,
        val originalAmountMinor: Long? = null,
        val originalCurrency: String? = null,
    )

    /**
     * A hand-entered amount is converted exactly as a scanned one is: into the currency
     * of the account it touches, or the app's own when it touches none.
     */
    private fun countedIn(amountMinor: Long, currency: String, accountId: String?): Counted {
        val home = accounts.value.firstOrNull { it.id == accountId }?.currency ?: prefs.defaultCurrency
        if (currency == home) return Counted(amountMinor, currency)
        val converted = Currencies.convert(amountMinor, currency, home) ?: return Counted(amountMinor, currency)
        return Counted(converted, home, amountMinor, currency)
    }

    // ─────────────────────────── currency ───────────────────────────

    private val _defaultCurrency = MutableStateFlow(prefs.defaultCurrency)
    val defaultCurrency: StateFlow<String> = _defaultCurrency

    fun setDefaultCurrency(code: String) {
        if (code == prefs.defaultCurrency) return
        prefs.defaultCurrency = code
        _defaultCurrency.value = code
        Verbose.info(Verbose.str(R.string.log_setting_changed, Verbose.str(R.string.log_note_currency, code)))
        Verbose.flush()
    }

    private val _fxOverrides = MutableStateFlow(Currencies.decode(prefs.fxRates))
    val fxOverrides: StateFlow<Map<String, BigDecimal>> = _fxOverrides

    /**
     * Corrects one floating rate, as rials per unit; null goes back to the built-in one.
     * Converted records were worked out at the old rate, so the next scan re-reads the
     * inbox - the rates are part of the scan's fingerprint.
     */
    fun setRate(code: String, omrPerUnit: BigDecimal?) {
        val next = _fxOverrides.value.toMutableMap().apply {
            if (omrPerUnit == null || omrPerUnit.signum() <= 0) remove(code) else put(code, omrPerUnit)
        }
        prefs.fxRates = Currencies.encode(next)
        Currencies.setOverrides(next)
        _fxOverrides.value = next
        Verbose.info(R.string.log_rate_changed, code, omrPerUnit?.toPlainString() ?: "-")
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
                        "category,sender,description,confidence,manual,charged_amount,charged_currency"
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
                            t.originalAmountMinor?.let { minor ->
                                Money.toMajor(minor, t.originalCurrency!!).toPlainString()
                            }.orEmpty(),
                            t.originalCurrency.orEmpty(),
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

    fun backup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val withMessages = prefs.backupIncludeMessages
        try {
            val json = store.backupJson(withMessages)
            getApplication<Application>().contentResolver.openOutputStream(uri)
                ?.bufferedWriter()?.use { it.write(json) }
            Verbose.ok(
                if (withMessages) R.string.log_backup_saved else R.string.log_backup_saved_bare,
                Verbose.count(R.plurals.count_records, txns.value.size),
            )
        } catch (e: Exception) {
            Verbose.fail(R.string.log_backup_failed, e.message)
        }
        Verbose.flush()
    }

    fun restore(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val json = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
            if (json.isNullOrBlank()) {
                Verbose.fail(R.string.log_restore_empty)
                Verbose.flush()
            } else if (store.restoreFrom(json)) {
                _accountsConfirmed.value = prefs.accountsConfirmed
            }
        } catch (e: Exception) {
            Verbose.fail(R.string.log_restore_unreadable, e.message)
            Verbose.flush()
        }
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private companion object {
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")

        /** The shortest a check someone asked for shows that it is checking. */
        const val MIN_CHECK_MS = 1_000L
    }
}
