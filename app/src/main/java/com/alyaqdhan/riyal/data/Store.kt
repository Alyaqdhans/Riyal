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
 * rescan is the user's word, category overrides, custom rules, manual entries and
 * dismissed/resolved review items, and it does.
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

    /** Message kinds the user dismissed; similar future messages are auto-dismissed. */
    private val _muted = MutableStateFlow<List<MutedTemplate>>(emptyList())
    val muted: StateFlow<List<MutedTemplate>> = _muted

    /** Message kinds the user recorded from Review; similar ones always reach Review. */
    private val _needed = MutableStateFlow<Set<String>>(emptySet())
    val needed: StateFlow<Set<String>> = _needed

    /** User-created categories, merged into the [Categories] registry on every change. */
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val _lastSummary = MutableStateFlow<ScanSummary?>(null)
    val lastSummary: StateFlow<ScanSummary?> = _lastSummary

    /** txnId → categoryId chosen by the user; reapplied after every rescan. */
    private val overrides = HashMap<String, String>()

    /**
     * Txn ids the user removed as "not a real transaction". Since scanned txns are
     * rebuilt from the inbox each scan and the id is a stable hash of the message,
     * these must be filtered out on every rescan or the message would just come back.
     */
    private val ignored = HashSet<String>()

    init {
        scope.launch { mutex.withLock { loadLocked() } }
    }

    suspend fun replaceScanned(
        scanned: List<Txn>,
        newReviews: List<ReviewItem>,
        seenSenders: Set<String>,
        summary: ScanSummary,
    ) = mutex.withLock {
        val withOverrides = scanned.asSequence()
            .filter { it.id !in ignored }
            .map { t ->
                overrides[t.id]?.let { t.copy(categoryId = it, categorySource = "user") } ?: t
            }.toList()
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
        _txns.value = _txns.value.filter { it.id != txn.id }
        persistLocked()
    }

    /** Adds/replaces a rule and re-categorizes auto-categorized transactions. Returns how many changed. */
    suspend fun addRule(rule: UserRule): Int = mutex.withLock {
        _rules.value = _rules.value.filter { it.pattern != rule.pattern } + rule
        var changed = 0
        _txns.value = _txns.value.map { t ->
            if (t.categorySource == "auto") {
                val match = Categorizer.categorize(t.direction, t.merchant, t.body, _rules.value, t.sender)
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
     * Deletes a user category. Any transactions, rules and overrides still pointing at
     * it are re-homed to the default so nothing dangles at an unknown id.
     */
    suspend fun deleteCategory(id: String) = mutex.withLock {
        val removed = _categories.value.firstOrNull { it.id == id } ?: return@withLock
        _categories.value = _categories.value.filter { it.id != id }
        Categories.setCustom(_categories.value)
        val fallback = if (removed.income) Categories.DEFAULT_INCOME else Categories.DEFAULT_EXPENSE
        _txns.value = _txns.value.map { if (it.categoryId == id) it.copy(categoryId = fallback) else it }
        _rules.value = _rules.value.map { if (it.categoryId == id) it.copy(categoryId = fallback) else it }
        for ((k, v) in overrides.toMap()) if (v == id) overrides[k] = fallback
        persistLocked()
    }

    suspend fun addManual(txn: Txn) = mutex.withLock {
        _txns.value = (_txns.value + txn).sortedByDescending { it.atMillis }
        persistLocked()
    }

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
        _txns.value = (_txns.value + txn).sortedByDescending { it.atMillis }
        persistLocked()
    }

    suspend fun wipe() = mutex.withLock {
        _txns.value = emptyList()
        _reviews.value = emptyList()
        _rules.value = emptyList()
        _senders.value = emptySet()
        _muted.value = emptyList()
        _needed.value = emptySet()
        _categories.value = emptyList()
        Categories.setCustom(emptyList())
        _lastSummary.value = null
        overrides.clear()
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
            _txns.value = root.optJSONArray("txns").toListOf(::txnFromJson).sortedByDescending { it.atMillis }
            _reviews.value = root.optJSONArray("reviews").toListOf(::reviewFromJson).sortedByDescending { it.atMillis }
            _rules.value = root.optJSONArray("rules").toListOf(::ruleFromJson)
            val senderArr = root.optJSONArray("senders")
            _senders.value = buildSet {
                if (senderArr != null) for (i in 0 until senderArr.length()) add(senderArr.getString(i))
            }
            _muted.value = root.optJSONArray("muted").toListOf(::mutedFromJson)
            val neededArr = root.optJSONArray("needed")
            _needed.value = buildSet {
                if (neededArr != null) for (i in 0 until neededArr.length()) add(neededArr.getString(i))
            }
            _categories.value = root.optJSONArray("categories").toListOf(::categoryFromJson)
            Categories.setCustom(_categories.value)
            root.optJSONObject("overrides")?.let { o ->
                for (key in o.keys()) overrides[key] = o.getString(key)
            }
            root.optJSONArray("ignored")?.let { a ->
                for (i in 0 until a.length()) ignored.add(a.getString(i))
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
            Verbose.fail("store: saved data unreadable (${e.message}), starting fresh, nothing was lost from your inbox")
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
            root.put("muted", JSONArray().apply { _muted.value.forEach { put(mutedToJson(it)) } })
            root.put("needed", JSONArray().apply { _needed.value.forEach { put(it) } })
            root.put("categories", JSONArray().apply { _categories.value.forEach { put(categoryToJson(it)) } })
            root.put("overrides", JSONObject().apply { overrides.forEach { (k, v) -> put(k, v) } })
            root.put("ignored", JSONArray().apply { ignored.forEach { put(it) } })
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
}
