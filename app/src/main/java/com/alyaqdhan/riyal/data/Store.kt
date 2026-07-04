package com.alyaqdhan.riyal.data

import android.content.Context
import com.alyaqdhan.riyal.core.Verbose
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device persistence: one JSON file in app-private storage, written atomically.
 * Scanned transactions are rebuilt from the inbox on every scan; what must survive a
 * rescan is the user's word — category overrides, custom rules, manual entries and
 * dismissed/resolved review items — and it does.
 */
class Store(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val file: File by lazy { File(appContext.filesDir, "riyal_store.json") }

    private val _txns = MutableStateFlow<List<Txn>>(emptyList())
    val txns: StateFlow<List<Txn>> = _txns

    private val _reviews = MutableStateFlow<List<ReviewItem>>(emptyList())
    val reviews: StateFlow<List<ReviewItem>> = _reviews

    private val _rules = MutableStateFlow<List<UserRule>>(emptyList())
    val rules: StateFlow<List<UserRule>> = _rules

    private val _senders = MutableStateFlow<Set<String>>(emptySet())
    val senders: StateFlow<Set<String>> = _senders

    private val _lastSummary = MutableStateFlow<ScanSummary?>(null)
    val lastSummary: StateFlow<ScanSummary?> = _lastSummary

    /** txnId → categoryId chosen by the user; reapplied after every rescan. */
    private val overrides = HashMap<String, String>()

    init {
        scope.launch { mutex.withLock { loadLocked() } }
    }

    suspend fun replaceScanned(
        scanned: List<Txn>,
        newReviews: List<ReviewItem>,
        seenSenders: Set<String>,
        summary: ScanSummary,
    ) = mutex.withLock {
        val withOverrides = scanned.map { t ->
            overrides[t.id]?.let { t.copy(categoryId = it, categorySource = "user") } ?: t
        }
        val manuals = _txns.value.filter { m -> m.manual && scanned.none { it.id == m.id } }
        _txns.value = (withOverrides + manuals).sortedByDescending { it.atMillis }

        val previous = _reviews.value.associateBy { it.id }
        _reviews.value = newReviews.map { r ->
            val old = previous[r.id]
            if (old != null && old.state != ReviewItem.STATE_PENDING) r.copy(state = old.state) else r
        }.sortedByDescending { it.atMillis }

        _senders.value = _senders.value + seenSenders
        _lastSummary.value = summary
        persistLocked()
    }

    suspend fun setCategory(txnId: String, categoryId: String) = mutex.withLock {
        overrides[txnId] = categoryId
        _txns.value = _txns.value.map {
            if (it.id == txnId) it.copy(categoryId = categoryId, categorySource = "user") else it
        }
        persistLocked()
    }

    /** Adds/replaces a rule and re-categorizes auto-categorized transactions. Returns how many changed. */
    suspend fun addRule(rule: UserRule): Int = mutex.withLock {
        _rules.value = _rules.value.filter { it.pattern != rule.pattern } + rule
        var changed = 0
        _txns.value = _txns.value.map { t ->
            if (t.categorySource == "auto") {
                val match = Categorizer.categorize(t.direction, t.merchant, t.body, _rules.value)
                if (match.categoryId != t.categoryId) {
                    changed++
                    t.copy(categoryId = match.categoryId)
                } else t
            } else t
        }
        persistLocked()
        changed
    }

    suspend fun removeRule(pattern: String) = mutex.withLock {
        _rules.value = _rules.value.filter { it.pattern != pattern }
        persistLocked()
    }

    suspend fun addManual(txn: Txn) = mutex.withLock {
        _txns.value = (_txns.value + txn).sortedByDescending { it.atMillis }
        persistLocked()
    }

    suspend fun setReviewState(id: String, state: String) = mutex.withLock {
        _reviews.value = _reviews.value.map { if (it.id == id) it.copy(state = state) else it }
        persistLocked()
    }

    suspend fun resolveReview(reviewId: String, txn: Txn) = mutex.withLock {
        _reviews.value = _reviews.value.map {
            if (it.id == reviewId) it.copy(state = ReviewItem.STATE_RESOLVED) else it
        }
        _txns.value = (_txns.value + txn).sortedByDescending { it.atMillis }
        persistLocked()
    }

    suspend fun wipe() = mutex.withLock {
        _txns.value = emptyList()
        _reviews.value = emptyList()
        _rules.value = emptyList()
        _senders.value = emptySet()
        _lastSummary.value = null
        overrides.clear()
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
            _txns.value = root.optJSONArray("txns").toListOf(::txnFromJson).sortedByDescending { it.atMillis }
            _reviews.value = root.optJSONArray("reviews").toListOf(::reviewFromJson).sortedByDescending { it.atMillis }
            _rules.value = root.optJSONArray("rules").toListOf(::ruleFromJson)
            val senderArr = root.optJSONArray("senders")
            _senders.value = buildSet {
                if (senderArr != null) for (i in 0 until senderArr.length()) add(senderArr.getString(i))
            }
            root.optJSONObject("overrides")?.let { o ->
                for (key in o.keys()) overrides[key] = o.getString(key)
            }
            root.optJSONObject("summary")?.let { s ->
                _lastSummary.value = ScanSummary(
                    at = s.getLong("at"), tookMs = s.getLong("took"),
                    scanned = s.getInt("scanned"), matched = s.getInt("matched"),
                    parsed = s.getInt("parsed"), review = s.getInt("review"),
                    skipped = s.getInt("skipped"),
                )
            }
            Verbose.info(
                "store: loaded ${_txns.value.size} transaction(s), " +
                    "${_reviews.value.count { it.state == ReviewItem.STATE_PENDING }} pending review item(s), " +
                    "${_rules.value.size} rule(s)"
            )
        } catch (e: Exception) {
            Verbose.fail("store: saved data unreadable (${e.message}) — starting fresh, nothing was lost from your inbox")
        }
        Verbose.flush()
    }

    private fun persistLocked() {
        try {
            val root = JSONObject()
            root.put("v", 1)
            root.put("txns", JSONArray().apply { _txns.value.forEach { put(txnToJson(it)) } })
            root.put("reviews", JSONArray().apply { _reviews.value.forEach { put(reviewToJson(it)) } })
            root.put("rules", JSONArray().apply { _rules.value.forEach { put(ruleToJson(it)) } })
            root.put("senders", JSONArray().apply { _senders.value.forEach { put(it) } })
            root.put("overrides", JSONObject().apply { overrides.forEach { (k, v) -> put(k, v) } })
            _lastSummary.value?.let { s ->
                root.put("summary", JSONObject().apply {
                    put("at", s.at); put("took", s.tookMs); put("scanned", s.scanned)
                    put("matched", s.matched); put("parsed", s.parsed)
                    put("review", s.review); put("skipped", s.skipped)
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

    private fun txnToJson(t: Txn) = JSONObject().apply {
        put("id", t.id); put("at", t.atMillis); put("amt", t.amountMinor)
        put("cur", t.currency); put("dir", t.direction.name)
        put("mer", t.merchant ?: JSONObject.NULL); put("sen", t.sender); put("body", t.body)
        put("cat", t.categoryId); put("src", t.categorySource)
        put("conf", t.confidence); put("man", t.manual)
    }

    private fun txnFromJson(o: JSONObject) = Txn(
        id = o.getString("id"),
        atMillis = o.getLong("at"),
        amountMinor = o.getLong("amt"),
        currency = o.getString("cur"),
        direction = Direction.valueOf(o.getString("dir")),
        merchant = if (o.isNull("mer")) null else o.getString("mer"),
        sender = o.getString("sen"),
        body = o.getString("body"),
        categoryId = o.getString("cat"),
        categorySource = o.getString("src"),
        confidence = o.getInt("conf"),
        manual = o.optBoolean("man", false),
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
}
