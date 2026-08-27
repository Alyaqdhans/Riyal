package com.alyaqdhan.riyal.data

import android.content.Context
import com.alyaqdhan.riyal.core.Verbose
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device persistence: one JSON file in app-private storage, written atomically.
 * Scanned transactions are rebuilt from the inbox on every scan; what must survive a
 * rescan is the user's word - category overrides, account assignments, transfer
 * decisions, custom rules, manual entries and dismissed/resolved review items - and it does.
 *
 * Transfers are *derived*, never baked in: the file stores the raw two-legged scan
 * output plus a yes/no per proposal, and [txns] is recomputed from both. That is what
 * makes "actually, that wasn't a transfer" a one-tap undo rather than a rescan.
 */
class Store(context: Context, autoConfirmTransfers: Boolean = true) {

    /**
     * Whether a freshly matched pair counts as a transfer straight away instead of
     * waiting in Review. Mirrors the Settings switch; a pair the user answered by hand
     * is never touched by it, in either direction.
     */
    private var autoConfirm: Boolean = autoConfirmTransfers

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val file: File by lazy { File(appContext.filesDir, "riyal_store.json") }

    /** What the scan produced plus manual entries, before transfer pairs are collapsed. */
    private var rawTxns: List<Txn> = emptyList()

    private val _txns = MutableStateFlow<List<Txn>>(emptyList())
    val txns: StateFlow<List<Txn>> = _txns

    private val _reviews = MutableStateFlow<List<ReviewItem>>(emptyList())
    val reviews: StateFlow<List<ReviewItem>> = _reviews

    private val _rules = MutableStateFlow<List<UserRule>>(emptyList())
    val rules: StateFlow<List<UserRule>> = _rules

    private val _senders = MutableStateFlow<Set<String>>(emptySet())
    val senders: StateFlow<Set<String>> = _senders

    /** Message kinds the user dismissed; similar future messages are auto-dismissed. */
    private val _muted = MutableStateFlow<List<MutedTemplate>>(emptyList())
    val muted: StateFlow<List<MutedTemplate>> = _muted

    /** Message kinds the user recorded from Review; similar ones always reach Review. */
    private val _needed = MutableStateFlow<Set<String>>(emptySet())
    val needed: StateFlow<Set<String>> = _needed

    /** User-created categories, merged into the [Categories] registry on every change. */
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts

    private val _budgets = MutableStateFlow<List<BudgetPlan>>(emptyList())
    val budgets: StateFlow<List<BudgetPlan>> = _budgets

    /** Candidate transfers, in every state; the UI shows the pending ones. */
    private val _transfers = MutableStateFlow<List<TransferProposal>>(emptyList())
    val transfers: StateFlow<List<TransferProposal>> = _transfers

    private val _lastSummary = MutableStateFlow<ScanSummary?>(null)
    val lastSummary: StateFlow<ScanSummary?> = _lastSummary

    /** Live balance per account id, the one place every screen reads a balance from. */
    val balances: StateFlow<Map<String, Long>> =
        combine(_accounts, _txns) { accounts, txns ->
            accounts.associate { it.id to AccountDiscovery.balanceOf(it, txns) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** txnId → categoryId chosen by the user; reapplied after every rescan. */
    private val overrides = HashMap<String, String>()

    /** txnId → accountId chosen by the user, for messages the scanner couldn't route. */
    private val accountOverrides = HashMap<String, String>()

    /** proposalId → accepted | rejected. The user is never asked the same pair twice. */
    private val transferDecisions = HashMap<String, String>()

    /**
     * txnId → the two ends of a transfer the user declared by hand (when only one bank
     * texted, so there was no second leg to pair with). Re-applied on every rescan, or
     * the record would silently revert to being counted as spending.
     */
    private val manualTransfers = HashMap<String, Pair<String?, String?>>()

    /**
     * Txn ids the user removed as "not a real transaction". Since scanned txns are
     * rebuilt from the inbox each scan and the id is a stable hash of the message,
     * these must be filtered out on every rescan or the message would just come back.
     */
    private val ignored = HashSet<String>()

    init {
        scope.launch { mutex.withLock { loadLocked() } }
    }

    // ─────────────────────────── scanning ───────────────────────────

    suspend fun replaceScanned(
        scanned: List<Txn>,
        proposals: List<TransferProposal>,
        newReviews: List<ReviewItem>,
        seenSenders: Set<String>,
        summary: ScanSummary,
    ) = mutex.withLock {
        val withOverrides = scanned.asSequence()
            .filter { it.id !in ignored }
            .map { applyUserEdits(it) }
            .toList()
        val manuals = rawTxns.filter { m -> m.manual && scanned.none { it.id == m.id } }
        rawTxns = (withOverrides + manuals).sortedByDescending { it.atMillis }

        // A proposal the user already answered keeps that answer; only genuinely new
        // pairs arrive as pending, so a rescan never re-asks a settled question.
        _transfers.value = proposals.distinctBy { it.id }.map { p ->
            transferDecisions[p.id]?.let { p.copy(state = it) } ?: p
        }.sortedByDescending { it.atMillis }
        applyAutoConfirmLocked()
        recomputeTxnsLocked()

        val previous = _reviews.value.associateBy { it.id }
        // Two byte-identical messages hash to one id, and a list rendered by id must
        // not contain it twice - that crashes the Review page rather than degrading.
        _reviews.value = newReviews.distinctBy { it.id }.map { r ->
            val old = previous[r.id]
            if (old != null && old.state != ReviewItem.STATE_PENDING) r.copy(state = old.state) else r
        }.sortedByDescending { it.atMillis }

        _senders.value = _senders.value + seenSenders
        _lastSummary.value = summary
        persistLocked()
    }

    private fun applyUserEdits(t: Txn): Txn {
        var out = t
        overrides[t.id]?.let { out = out.copy(categoryId = it, categorySource = "user") }
        accountOverrides[t.id]?.let { acc ->
            out = if (out.type == TxnType.INCOME) {
                out.copy(toAccountId = acc)
            } else {
                out.copy(fromAccountId = acc)
            }
        }
        return out
    }

    /** Applies a hand-declared transfer mark to one record. */
    private fun asMarkedTransfer(t: Txn, ends: Pair<String?, String?>): Txn = t.copy(
        type = TxnType.TRANSFER,
        fromAccountId = ends.first,
        toAccountId = ends.second,
        categoryId = Categories.TRANSFER_ID,
        categorySource = "user",
        // One leg, not two: enough to mark it as derived from a real record, which is
        // what tells the UI this transfer can be split back apart.
        legIds = listOf(t.id),
    )

    /**
     * Rebuilds [txns] from the raw scan output by collapsing every accepted transfer
     * pair into one record. Called after anything that could change either input.
     */
    private fun recomputeTxnsLocked() {
        val accepted = _transfers.value.filter { it.state == TransferProposal.STATE_ACCEPTED }
        val byId = rawTxns.associateBy { it.id }
        val consumed = HashSet<String>()
        val merged = ArrayList<Txn>()
        for (p in accepted) {
            val out = byId[p.outTxnId] ?: continue
            val income = byId[p.inTxnId] ?: continue
            if (out.id in consumed || income.id in consumed) continue
            consumed += out.id
            consumed += income.id
            merged += TransferMatcher.merge(p, out, income)
        }
        _txns.value = (
            rawTxns
                .filter { it.id !in consumed }
                .map { t -> manualTransfers[t.id]?.let { asMarkedTransfer(t, it) } ?: t } + merged
            ).sortedByDescending { it.atMillis }
    }

    // ─────────────────────────── transfers ───────────────────────────

    /** Confirms a pair really was one movement between the user's own accounts. */
    suspend fun acceptTransfer(proposal: TransferProposal) = mutex.withLock {
        transferDecisions[proposal.id] = TransferProposal.STATE_ACCEPTED
        setTransferStateLocked(proposal.id, TransferProposal.STATE_ACCEPTED)
        recomputeTxnsLocked()
        persistLocked()
    }

    /** Keeps both legs as a real expense and a real income, and stops asking. */
    suspend fun rejectTransfer(proposal: TransferProposal) = mutex.withLock {
        transferDecisions[proposal.id] = TransferProposal.STATE_REJECTED
        setTransferStateLocked(proposal.id, TransferProposal.STATE_REJECTED)
        recomputeTxnsLocked()
        persistLocked()
    }

    /** Undoes an earlier answer, putting the pair back in the queue. */
    suspend fun reopenTransfer(proposalId: String) = mutex.withLock {
        // Pending is recorded as a decision of its own: the user asked to be asked, and
        // auto-confirm must not quietly answer for them on the next scan.
        transferDecisions[proposalId] = TransferProposal.STATE_PENDING
        setTransferStateLocked(proposalId, TransferProposal.STATE_PENDING)
        recomputeTxnsLocked()
        persistLocked()
    }

    /**
     * Undoes a transfer from a transaction row, whichever way it became one: a
     * confirmed pair goes back to two records, a hand-declared one goes back to the
     * expense or income the scanner originally read.
     */
    suspend fun splitTransfer(txn: Txn) = mutex.withLock {
        if (txn.legIds.size == 2) {
            val id = TransferProposal.idFor(txn.legIds[0], txn.legIds[1])
            transferDecisions[id] = TransferProposal.STATE_REJECTED
            setTransferStateLocked(id, TransferProposal.STATE_REJECTED)
        }
        // A mark is an overlay, so dropping it restores the record the scanner read.
        txn.legIds.forEach { manualTransfers.remove(it) }
        manualTransfers.remove(txn.id)
        recomputeTxnsLocked()
        persistLocked()
    }

    /**
     * Records a transfer the user declared by hand, e.g. when only one of the two banks
     * texted. The chosen expense row becomes the single transfer record.
     */
    suspend fun markAsTransfer(txn: Txn, fromAccountId: String?, toAccountId: String?) = mutex.withLock {
        manualTransfers[txn.id] = fromAccountId to toAccountId
        recomputeTxnsLocked()
        persistLocked()
    }

    /**
     * Turns auto-confirm on or off. Only pairs the user never answered move: switching
     * it on confirms the ones still waiting, switching it off puts them back in the
     * queue, and a hand-made yes or no survives both.
     */
    suspend fun setAutoConfirmTransfers(enabled: Boolean) = mutex.withLock {
        if (autoConfirm == enabled) return@withLock
        autoConfirm = enabled
        applyAutoConfirmLocked()
        recomputeTxnsLocked()
        persistLocked()
    }

    /**
     * Applies the auto-confirm setting to every proposal the user has not answered.
     * Kept separate from the decision map so that "the app decided this" and "you
     * decided this" never get confused: only the former is reversible by the switch.
     */
    private fun applyAutoConfirmLocked() {
        var changed = 0
        _transfers.value = _transfers.value.map { p ->
            if (p.id in transferDecisions) return@map p
            when {
                autoConfirm && p.state == TransferProposal.STATE_PENDING -> {
                    changed++
                    p.copy(state = TransferProposal.STATE_ACCEPTED)
                }
                !autoConfirm && p.state == TransferProposal.STATE_ACCEPTED -> {
                    changed++
                    p.copy(state = TransferProposal.STATE_PENDING)
                }
                else -> p
            }
        }
        if (changed > 0) {
            Verbose.info(
                if (autoConfirm) {
                    "$changed matched pair(s) confirmed as transfers automatically · " +
                        "they no longer count as spending or income, and each one can be split back apart"
                } else {
                    "$changed automatically confirmed transfer(s) moved back to Review for your answer"
                }
            )
        }
    }

    private fun setTransferStateLocked(proposalId: String, state: String) {
        _transfers.value = _transfers.value.map {
            if (it.id == proposalId) it.copy(state = state) else it
        }
    }

    // ─────────────────────────── accounts ───────────────────────────

    /** Replaces the account list wholesale, used by first-run confirmation. */
    suspend fun replaceAccounts(list: List<Account>) = mutex.withLock {
        _accounts.value = list
        recomputeTxnsLocked()
        persistLocked()
    }

    suspend fun addAccount(account: Account): String = mutex.withLock {
        val id = account.id.ifBlank { Account.ID_PREFIX + UUID.randomUUID().toString().take(8) }
        _accounts.value = _accounts.value + account.copy(id = id)
        persistLocked()
        id
    }

    suspend fun updateAccount(account: Account) = mutex.withLock {
        _accounts.value = _accounts.value.map { if (it.id == account.id) account else it }
        persistLocked()
    }

    /**
     * Deletes an account. Records that pointed at it are detached rather than removed:
     * the money still moved, we just no longer know which account it moved through.
     */
    suspend fun deleteAccount(id: String) = mutex.withLock {
        _accounts.value = _accounts.value.filter { it.id != id }
        rawTxns = rawTxns.map {
            when (id) {
                it.fromAccountId -> it.copy(fromAccountId = null)
                it.toAccountId -> it.copy(toAccountId = null)
                else -> it
            }
        }
        for ((k, v) in accountOverrides.toMap()) if (v == id) accountOverrides.remove(k)
        recomputeTxnsLocked()
        persistLocked()
    }

    /** Routes one record to an account by hand, and remembers it across rescans. */
    suspend fun setTxnAccount(txnId: String, accountId: String) = mutex.withLock {
        accountOverrides[txnId] = accountId
        rawTxns = rawTxns.map { if (it.id == txnId) applyUserEdits(it) else it }
        recomputeTxnsLocked()
        persistLocked()
    }

    // ─────────────────────────── budgets ───────────────────────────

    suspend fun addBudget(plan: BudgetPlan): String = mutex.withLock {
        val id = plan.id.ifBlank { BudgetPlan.ID_PREFIX + UUID.randomUUID().toString().take(8) }
        _budgets.value = (_budgets.value + plan.copy(id = id)).sortedByDescending { it.startMillis }
        persistLocked()
        id
    }

    suspend fun updateBudget(plan: BudgetPlan) = mutex.withLock {
        _budgets.value = _budgets.value
            .map { if (it.id == plan.id) plan else it }
            .sortedByDescending { it.startMillis }
        persistLocked()
    }

    suspend fun deleteBudget(id: String) = mutex.withLock {
        _budgets.value = _budgets.value.filter { it.id != id }
        persistLocked()
    }

    /** Sets or clears one category cap inside a plan. A zero cap removes the line. */
    suspend fun setBudgetLine(planId: String, categoryId: String, minor: Long) = mutex.withLock {
        _budgets.value = _budgets.value.map { plan ->
            if (plan.id != planId) plan
            else plan.copy(
                lines = plan.lines.toMutableMap().apply {
                    if (minor > 0) this[categoryId] = minor else remove(categoryId)
                },
            )
        }
        persistLocked()
    }

    // ─────────────────────────── user edits ───────────────────────────

    suspend fun setCategory(txnId: String, categoryId: String) = mutex.withLock {
        overrides[txnId] = categoryId
        rawTxns = rawTxns.map {
            if (it.id == txnId) it.copy(categoryId = categoryId, categorySource = "user") else it
        }
        recomputeTxnsLocked()
        persistLocked()
    }

    /**
     * Removes a transaction the user says isn't a real one. A scanned transaction is
     * also remembered as ignored so future rescans keep dropping the same message; a
     * manual transaction is simply deleted (there's no inbox message to re-parse).
     */
    suspend fun ignoreTxn(txn: Txn) = mutex.withLock {
        if (!txn.manual) {
            ignored.add(txn.id)
            overrides.remove(txn.id)
        }
        // A confirmed transfer stands for two messages; ignoring it must ignore both.
        txn.legIds.forEach { if (!txn.manual) ignored.add(it) }
        val gone = (txn.legIds + txn.id).toSet()
        gone.forEach { manualTransfers.remove(it) }
        rawTxns = rawTxns.filter { it.id !in gone }
        recomputeTxnsLocked()
        persistLocked()
    }

    /** Adds/replaces a rule and re-categorizes auto-categorized transactions. Returns how many changed. */
    suspend fun addRule(rule: UserRule): Int = mutex.withLock {
        _rules.value = _rules.value.filter { it.pattern != rule.pattern } + rule
        var changed = 0
        rawTxns = rawTxns.map { t ->
            if (t.categorySource == "auto" && t.type != TxnType.TRANSFER) {
                val direction = if (t.type == TxnType.INCOME) Direction.INCOME else Direction.EXPENSE
                val match = Categorizer.categorize(direction, t.merchant, t.body, _rules.value, t.sender)
                if (match.categoryId != t.categoryId) {
                    changed++
                    t.copy(categoryId = match.categoryId)
                } else t
            } else t
        }
        recomputeTxnsLocked()
        persistLocked()
        changed
    }

    suspend fun removeRule(pattern: String) = mutex.withLock {
        _rules.value = _rules.value.filter { it.pattern != pattern }
        persistLocked()
    }

    // ─────────────────────────── custom categories ───────────────────────────

    /** Creates a new user category and returns its generated id. */
    suspend fun addCategory(name: String, income: Boolean, color: Int): String = mutex.withLock {
        val id = Categories.CUSTOM_ID_PREFIX + System.currentTimeMillis().toString(36)
        _categories.value = _categories.value + Category(id, name, income, color, custom = true)
        Categories.setCustom(_categories.value)
        persistLocked()
        id
    }

    suspend fun updateCategory(id: String, name: String, color: Int) = mutex.withLock {
        _categories.value = _categories.value.map {
            if (it.id == id) it.copy(name = name, color = color) else it
        }
        Categories.setCustom(_categories.value)
        persistLocked()
    }

    /**
     * Deletes a user category. Any transactions, rules, budget lines and overrides still
     * pointing at it are re-homed to the default so nothing dangles at an unknown id.
     */
    suspend fun deleteCategory(id: String) = mutex.withLock {
        val removed = _categories.value.firstOrNull { it.id == id } ?: return@withLock
        _categories.value = _categories.value.filter { it.id != id }
        Categories.setCustom(_categories.value)
        val fallback = if (removed.income) Categories.DEFAULT_INCOME else Categories.DEFAULT_EXPENSE
        rawTxns = rawTxns.map { if (it.categoryId == id) it.copy(categoryId = fallback) else it }
        _rules.value = _rules.value.map { if (it.categoryId == id) it.copy(categoryId = fallback) else it }
        _budgets.value = _budgets.value.map { it.copy(lines = it.lines - id) }
        for ((k, v) in overrides.toMap()) if (v == id) overrides[k] = fallback
        recomputeTxnsLocked()
        persistLocked()
    }

    suspend fun addManual(txn: Txn) = mutex.withLock {
        rawTxns = (rawTxns + txn).sortedByDescending { it.atMillis }
        recomputeTxnsLocked()
        persistLocked()
    }

    // ─────────────────────────── review ───────────────────────────

    /**
     * Dismisses [item]. With [smart], similar pending items go with it and the message
     * kind is muted so future scans auto-dismiss it too. Returns how many other items
     * were dismissed alongside.
     */
    suspend fun dismissReview(item: ReviewItem, smart: Boolean): Int = mutex.withLock {
        val template = MsgTemplate.of(item.sender, item.body)
        var extra = 0
        _reviews.value = _reviews.value.map { r ->
            when {
                r.id == item.id -> r.copy(state = ReviewItem.STATE_DISMISSED)
                smart && r.state == ReviewItem.STATE_PENDING &&
                    MsgTemplate.of(r.sender, r.body) == template -> {
                    extra++
                    r.copy(state = ReviewItem.STATE_DISMISSED)
                }
                else -> r
            }
        }
        if (smart && _muted.value.none { it.template == template }) {
            _muted.value = _muted.value +
                MutedTemplate(template, item.sender, item.body, System.currentTimeMillis())
        }
        persistLocked()
        extra
    }

    /**
     * Brings a dismissed [item] back to pending. If its kind was muted, the mute is
     * lifted and every similarly dismissed item comes back too (the whole group was
     * hidden as one decision, it is restored as one). Returns how many others returned.
     */
    suspend fun restoreReview(item: ReviewItem): Int = mutex.withLock {
        val template = MsgTemplate.of(item.sender, item.body)
        val wasMuted = _muted.value.any { it.template == template }
        if (wasMuted) _muted.value = _muted.value.filter { it.template != template }
        var extra = 0
        _reviews.value = _reviews.value.map { r ->
            when {
                r.id == item.id -> r.copy(state = ReviewItem.STATE_PENDING)
                wasMuted && r.state == ReviewItem.STATE_DISMISSED &&
                    MsgTemplate.of(r.sender, r.body) == template -> {
                    extra++
                    r.copy(state = ReviewItem.STATE_PENDING)
                }
                else -> r
            }
        }
        persistLocked()
        extra
    }

    suspend fun resolveReview(reviewId: String, txn: Txn, learn: Boolean = true) = mutex.withLock {
        val item = if (learn) _reviews.value.firstOrNull { it.id == reviewId } else null
        if (item != null) {
            // Recording it is the opposite signal of dismissing: this kind of message
            // matters, keep showing similar ones and drop any mute on them.
            val template = MsgTemplate.of(item.sender, item.body)
            _needed.value = _needed.value + template
            _muted.value = _muted.value.filter { it.template != template }
        }
        _reviews.value = _reviews.value.map {
            if (it.id == reviewId) it.copy(state = ReviewItem.STATE_RESOLVED) else it
        }
        rawTxns = (rawTxns + txn).sortedByDescending { it.atMillis }
        recomputeTxnsLocked()
        persistLocked()
    }

    suspend fun wipe() = mutex.withLock {
        rawTxns = emptyList()
        _txns.value = emptyList()
        _reviews.value = emptyList()
        _rules.value = emptyList()
        _senders.value = emptySet()
        _muted.value = emptyList()
        _needed.value = emptySet()
        _categories.value = emptyList()
        Categories.setCustom(emptyList())
        _accounts.value = emptyList()
        _budgets.value = emptyList()
        _transfers.value = emptyList()
        _lastSummary.value = null
        overrides.clear()
        accountOverrides.clear()
        transferDecisions.clear()
        manualTransfers.clear()
        ignored.clear()
        file.delete()
        Verbose.info("store: all saved data deleted by you")
        Verbose.flush()
    }

    // ─────────────────────────── persistence ───────────────────────────

    private fun loadLocked() {
        if (!file.exists()) {
            Verbose.info("store: no saved data yet (first run)")
            Verbose.flush()
            return
        }
        try {
            val root = JSONObject(file.readText())
            val version = root.optInt("v", 1)
            if (version != SCHEMA_VERSION) {
                // Accounts, transfer types and dated budgets changed the shape of every
                // record. Transactions are rebuilt from the inbox anyway, so the honest
                // move is to start clean rather than guess at half-formed old rows.
                file.delete()
                Verbose.info(
                    "store: saved data is from an older version (v$version) and this build " +
                        "stores accounts and transfers, discarded it. Your inbox is untouched, " +
                        "the next scan rebuilds everything."
                )
                Verbose.flush()
                return
            }
            rawTxns = root.optJSONArray("txns").toListOf(::txnFromJson).sortedByDescending { it.atMillis }
            _reviews.value = root.optJSONArray("reviews").toListOf(::reviewFromJson).sortedByDescending { it.atMillis }
            _rules.value = root.optJSONArray("rules").toListOf(::ruleFromJson)
            _senders.value = root.optJSONArray("senders").toStringSet()
            _muted.value = root.optJSONArray("muted").toListOf(::mutedFromJson)
            _needed.value = root.optJSONArray("needed").toStringSet()
            _categories.value = root.optJSONArray("categories").toListOf(::categoryFromJson)
            Categories.setCustom(_categories.value)
            _accounts.value = root.optJSONArray("accounts").toListOf(::accountFromJson)
            _budgets.value = root.optJSONArray("budgets").toListOf(::budgetFromJson)
                .sortedByDescending { it.startMillis }
            _transfers.value = root.optJSONArray("transfers").toListOf(::transferFromJson)
                .sortedByDescending { it.atMillis }
            root.optJSONObject("overrides")?.let { o ->
                for (key in o.keys()) overrides[key] = o.getString(key)
            }
            root.optJSONObject("accountOverrides")?.let { o ->
                for (key in o.keys()) accountOverrides[key] = o.getString(key)
            }
            root.optJSONObject("transferDecisions")?.let { o ->
                for (key in o.keys()) transferDecisions[key] = o.getString(key)
            }
            root.optJSONObject("manualTransfers")?.let { o ->
                for (key in o.keys()) {
                    val ends = o.getJSONObject(key)
                    manualTransfers[key] = ends.optNullableString("from") to ends.optNullableString("to")
                }
            }
            root.optJSONArray("ignored")?.let { a ->
                for (i in 0 until a.length()) ignored.add(a.getString(i))
            }
            root.optJSONObject("summary")?.let { s ->
                _lastSummary.value = ScanSummary(
                    at = s.getLong("at"), tookMs = s.getLong("took"),
                    scanned = s.getInt("scanned"), matched = s.getInt("matched"),
                    parsed = s.getInt("parsed"), review = s.getInt("review"),
                    skipped = s.getInt("skipped"), transfers = s.optInt("transfers", 0),
                )
            }
            // A pair saved before auto-confirm was on is answered now, on the way in.
            applyAutoConfirmLocked()
            recomputeTxnsLocked()
            Verbose.info(
                "store: loaded ${_txns.value.size} transaction(s), " +
                    "${_accounts.value.size} account(s), " +
                    "${_transfers.value.count { it.state == TransferProposal.STATE_PENDING }} transfer(s) to confirm, " +
                    "${_reviews.value.count { it.state == ReviewItem.STATE_PENDING }} pending review item(s), " +
                    "${_rules.value.size} rule(s)"
            )
        } catch (e: Exception) {
            Verbose.fail("store: saved data unreadable (${e.message}), starting fresh, nothing was lost from your inbox")
        }
        Verbose.flush()
    }

    private fun persistLocked() {
        try {
            val root = JSONObject()
            root.put("v", SCHEMA_VERSION)
            root.put("txns", JSONArray().apply { rawTxns.forEach { put(txnToJson(it)) } })
            root.put("reviews", JSONArray().apply { _reviews.value.forEach { put(reviewToJson(it)) } })
            root.put("rules", JSONArray().apply { _rules.value.forEach { put(ruleToJson(it)) } })
            root.put("senders", JSONArray().apply { _senders.value.forEach { put(it) } })
            root.put("muted", JSONArray().apply { _muted.value.forEach { put(mutedToJson(it)) } })
            root.put("needed", JSONArray().apply { _needed.value.forEach { put(it) } })
            root.put("categories", JSONArray().apply { _categories.value.forEach { put(categoryToJson(it)) } })
            root.put("accounts", JSONArray().apply { _accounts.value.forEach { put(accountToJson(it)) } })
            root.put("budgets", JSONArray().apply { _budgets.value.forEach { put(budgetToJson(it)) } })
            root.put("transfers", JSONArray().apply { _transfers.value.forEach { put(transferToJson(it)) } })
            root.put("overrides", JSONObject().apply { overrides.forEach { (k, v) -> put(k, v) } })
            root.put("accountOverrides", JSONObject().apply { accountOverrides.forEach { (k, v) -> put(k, v) } })
            root.put("transferDecisions", JSONObject().apply { transferDecisions.forEach { (k, v) -> put(k, v) } })
            root.put("manualTransfers", JSONObject().apply {
                manualTransfers.forEach { (id, ends) ->
                    put(id, JSONObject().apply {
                        put("from", ends.first ?: JSONObject.NULL)
                        put("to", ends.second ?: JSONObject.NULL)
                    })
                }
            })
            root.put("ignored", JSONArray().apply { ignored.forEach { put(it) } })
            _lastSummary.value?.let { s ->
                root.put("summary", JSONObject().apply {
                    put("at", s.at); put("took", s.tookMs); put("scanned", s.scanned)
                    put("matched", s.matched); put("parsed", s.parsed)
                    put("review", s.review); put("skipped", s.skipped)
                    put("transfers", s.transfers)
                })
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Verbose.fail("store: could not save (${e.message})")
        }
    }

    private fun <T> JSONArray?.toListOf(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            try {
                out += mapper(getJSONObject(i))
            } catch (_: Exception) {
                // one corrupt row never takes the rest down
            }
        }
        return out
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        if (this@toStringSet != null) {
            for (i in 0 until this@toStringSet.length()) add(this@toStringSet.getString(i))
        }
    }

    private fun txnToJson(t: Txn) = JSONObject().apply {
        put("id", t.id); put("at", t.atMillis); put("amt", t.amountMinor)
        put("cur", t.currency); put("type", t.type.name)
        put("from", t.fromAccountId ?: JSONObject.NULL)
        put("to", t.toAccountId ?: JSONObject.NULL)
        put("mer", t.merchant ?: JSONObject.NULL); put("sen", t.sender); put("body", t.body)
        put("cat", t.categoryId); put("src", t.categorySource)
        put("conf", t.confidence); put("man", t.manual)
        if (t.legIds.isNotEmpty()) put("legs", JSONArray().apply { t.legIds.forEach { put(it) } })
    }

    private fun txnFromJson(o: JSONObject) = Txn(
        id = o.getString("id"),
        atMillis = o.getLong("at"),
        amountMinor = o.getLong("amt"),
        currency = o.getString("cur"),
        type = TxnType.valueOf(o.getString("type")),
        fromAccountId = o.optNullableString("from"),
        toAccountId = o.optNullableString("to"),
        merchant = o.optNullableString("mer"),
        sender = o.getString("sen"),
        body = o.getString("body"),
        categoryId = o.getString("cat"),
        categorySource = o.getString("src"),
        confidence = o.getInt("conf"),
        manual = o.optBoolean("man", false),
        legIds = o.optJSONArray("legs").toStringSet().toList(),
    )

    private fun accountToJson(a: Account) = JSONObject().apply {
        put("id", a.id); put("name", a.name); put("bank", a.bankName)
        put("last4", a.last4 ?: JSONObject.NULL); put("cur", a.currency)
        put("open", a.openingBalanceMinor); put("openAt", a.openingAtMillis)
        put("senders", JSONArray().apply { a.senderIds.forEach { put(it) } })
        put("color", a.color); put("archived", a.archived); put("needsBal", a.needsBalance)
    }

    /**
     * Accounts discovered before accounts named themselves carry a placeholder nickname
     * ("Main", "Account ···0019"). Dropping it lets [Account.displayName] show the bank
     * and the digits instead; a nickname the user actually typed is left alone.
     */
    private fun legacyAutoName(stored: String): String =
        if (stored == "Main" || LEGACY_AUTO_NAME.matches(stored)) "" else stored

    private fun accountFromJson(o: JSONObject) = Account(
        id = o.getString("id"),
        name = legacyAutoName(o.getString("name")),
        bankName = o.optString("bank", ""),
        last4 = o.optNullableString("last4"),
        currency = o.getString("cur"),
        openingBalanceMinor = o.getLong("open"),
        openingAtMillis = o.getLong("openAt"),
        senderIds = o.optJSONArray("senders").toStringSet(),
        color = o.optInt("color", 0),
        archived = o.optBoolean("archived", false),
        needsBalance = o.optBoolean("needsBal", false),
    )

    private fun budgetToJson(b: BudgetPlan) = JSONObject().apply {
        put("id", b.id); put("label", b.label)
        put("start", b.startMillis); put("end", b.endExclusiveMillis)
        put("lines", JSONObject().apply { b.lines.forEach { (k, v) -> put(k, v) } })
    }

    private fun budgetFromJson(o: JSONObject) = BudgetPlan(
        id = o.getString("id"),
        label = o.getString("label"),
        startMillis = o.getLong("start"),
        endExclusiveMillis = o.getLong("end"),
        lines = o.optJSONObject("lines")?.let { l ->
            buildMap { for (k in l.keys()) l.optLong(k).takeIf { it > 0 }?.let { put(k, it) } }
        } ?: emptyMap(),
    )

    private fun transferToJson(t: TransferProposal) = JSONObject().apply {
        put("id", t.id); put("out", t.outTxnId); put("in", t.inTxnId)
        put("from", t.fromAccountId ?: JSONObject.NULL)
        put("to", t.toAccountId ?: JSONObject.NULL)
        put("amt", t.amountMinor); put("cur", t.currency)
        put("at", t.atMillis); put("state", t.state)
    }

    private fun transferFromJson(o: JSONObject) = TransferProposal(
        id = o.getString("id"),
        outTxnId = o.getString("out"),
        inTxnId = o.getString("in"),
        fromAccountId = o.optNullableString("from"),
        toAccountId = o.optNullableString("to"),
        amountMinor = o.getLong("amt"),
        currency = o.getString("cur"),
        atMillis = o.getLong("at"),
        state = o.optString("state", TransferProposal.STATE_PENDING),
    )

    private fun reviewToJson(r: ReviewItem) = JSONObject().apply {
        put("id", r.id); put("at", r.atMillis); put("sen", r.sender)
        put("body", r.body); put("reason", r.reason); put("state", r.state)
    }

    private fun reviewFromJson(o: JSONObject) = ReviewItem(
        id = o.getString("id"),
        atMillis = o.getLong("at"),
        sender = o.getString("sen"),
        body = o.getString("body"),
        reason = o.getString("reason"),
        state = o.optString("state", ReviewItem.STATE_PENDING),
    )

    private fun ruleToJson(r: UserRule) = JSONObject().apply {
        put("p", r.pattern); put("c", r.categoryId)
    }

    private fun ruleFromJson(o: JSONObject) = UserRule(o.getString("p"), o.getString("c"))

    private fun categoryToJson(c: Category) = JSONObject().apply {
        put("id", c.id); put("name", c.name); put("income", c.income); put("color", c.color)
    }

    private fun categoryFromJson(o: JSONObject) = Category(
        id = o.getString("id"),
        name = o.getString("name"),
        income = o.optBoolean("income", false),
        color = o.optInt("color", 0),
        custom = true,
    )

    private fun mutedToJson(m: MutedTemplate) = JSONObject().apply {
        put("t", m.template); put("sen", m.sender); put("sample", m.sample); put("at", m.at)
    }

    private fun mutedFromJson(o: JSONObject) = MutedTemplate(
        template = o.getString("t"),
        sender = o.getString("sen"),
        sample = o.getString("sample"),
        at = o.getLong("at"),
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private companion object {
        /** Bumped for accounts + transfer types + dated budgets; older files are discarded. */
        const val SCHEMA_VERSION = 2

        /** The placeholder nickname discovery used to write, e.g. "Account ···0019". */
        val LEGACY_AUTO_NAME = Regex("^Account [·.]{0,3}\\d{2,6}$")
    }
}
