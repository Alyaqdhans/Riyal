package com.alyaqdhan.riyal.data

import androidx.annotation.StringRes
import com.alyaqdhan.riyal.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backups made of parts, so a file can hold everything - a phone's whole history - or
 * only what is worth giving someone else: the categories you made, the names you filed
 * under them, the lists. Bringing a file in replaces the records with its own and adds
 * the rest to what is already here, which is what makes a friend's categories something
 * to import rather than something that wipes yours.
 *
 * A backup is the store's own JSON with only the chosen parts' keys in it, plus the
 * settings that belong to a part ([IGNORED_SENDERS], [ADDED_SENDERS], [BUDGETS_ON]) and
 * the list of parts. One saved before parts existed is the whole store file, and reads
 * as every part it has anything in.
 */
object Backup {

    /** Senders never read, from settings. */
    const val IGNORED_SENDERS = "ignoredSenders"

    /** Senders read although they are not a bank, from settings. */
    const val ADDED_SENDERS = "addedSenders"

    /** Whether budgets are switched on, from settings. */
    const val BUDGETS_ON = "budgetsOn"

    /** The keys that live in settings rather than in the store's file. */
    val SETTINGS_KEYS = listOf(IGNORED_SENDERS, ADDED_SENDERS, BUDGETS_ON)

    /** What a backup can hold, each saved and brought back on its own. */
    enum class Part(@param:StringRes val label: Int, @param:StringRes val detail: Int, val keys: List<String>) {
        /** The history read from the inbox, the accounts, and every edit made to them. */
        RECORDS(
            R.string.backup_part_records,
            R.string.backup_part_records_detail,
            listOf(
                "txns", "reviews", "accounts", "transfers", "overrides", "accountOverrides",
                "transferDecisions", "manualTransfers", "ignored", "archivedTxns", "deferred",
                "senders", "needed", "templates", "summary",
            )
        ),

        /** The categories the user made. */
        CATEGORIES(R.string.backup_part_categories, R.string.backup_part_categories_detail, listOf("categories")),

        /** Names filed under a category without asking, and names always asked about. */
        RULES(R.string.backup_part_rules, R.string.backup_part_rules_detail, listOf("rules", "askEachTime")),

        /** Kinds of message taught and dismissed, and senders ignored or added. */
        LISTS(
            R.string.backup_part_lists,
            R.string.backup_part_lists_detail,
            listOf("skeleton", "labels", "muted", IGNORED_SENDERS, ADDED_SENDERS),
        ),

        /** Budget plans, and whether budgets are on. */
        BUDGETS(R.string.backup_part_budgets, R.string.backup_part_budgets_detail, listOf("budgets", BUDGETS_ON)),
    }

    /** Message text, which a backup can leave out. */
    private val TEXT = listOf("txns" to "body", "reviews" to "body", "muted" to "sample", "templates" to "body")

    /**
     * A backup of [parts]: [storeJson] is the store's file, and [settings] carries the
     * [SETTINGS_KEYS]. Without [includeMessages] every message's text is blanked; its
     * key stays, so the file still reads.
     */
    fun write(storeJson: String, settings: JSONObject, parts: Set<Part>, includeMessages: Boolean): String {
        val all = JSONObject(storeJson)
        for (key in settings.keys()) all.put(key, settings.get(key))
        val out = JSONObject()
        out.put("v", all.optInt("v", 0))
        out.put("parts", JSONArray().apply { Part.entries.filter { it in parts }.forEach { put(it.name.lowercase()) } })
        for (part in Part.entries) {
            if (part !in parts) continue
            for (key in part.keys) all.opt(key)?.let { out.put(key, it) }
        }
        if (!includeMessages) for ((array, key) in TEXT) blank(out.optJSONArray(array), key)
        return out.toString()
    }

    fun versionOf(json: String): Int = JSONObject(json).optInt("v", 0)

    /**
     * The parts a backup holds, each with how many things are in it: the parts it was
     * saved with, or - saved before a backup had parts - each part with anything in it.
     */
    fun contents(json: String): Map<Part, Int> {
        val root = JSONObject(json)
        val listed = root.optJSONArray("parts")?.let { a ->
            (0 until a.length()).mapNotNull { i -> Part.entries.firstOrNull { it.name.equals(a.optString(i), true) } }.toSet()
        }
        return Part.entries.associateWith { sizeOf(root, it) }
            .filter { (part, n) -> if (listed != null) part in listed else n > 0 }
    }

    private fun sizeOf(root: JSONObject, part: Part): Int = when (part) {
        Part.RECORDS -> root.optJSONArray("txns")?.length() ?: 0
        Part.CATEGORIES -> root.optJSONArray("categories")?.length() ?: 0
        Part.RULES -> (root.optJSONArray("rules")?.length() ?: 0) + (root.optJSONArray("askEachTime")?.length() ?: 0)
        Part.LISTS -> (root.optJSONObject("labels")?.length() ?: 0) + (root.optJSONArray("muted")?.length() ?: 0) +
            (root.optJSONArray(IGNORED_SENDERS)?.length() ?: 0) + (root.optJSONArray(ADDED_SENDERS)?.length() ?: 0)
        Part.BUDGETS -> root.optJSONArray("budgets")?.length() ?: 0
    }

    fun hasMessages(json: String): Boolean {
        val rows = JSONObject(json).optJSONArray("txns") ?: return false
        for (i in 0 until rows.length()) {
            val body = (rows.opt(i) as? JSONObject)?.optString("body").orEmpty()
            if (body.isNotEmpty()) return true
        }
        return false
    }

    /**
     * [theirs]' [parts] brought into [mine] - the store's file with the [SETTINGS_KEYS]
     * added - which comes back changed:
     *
     *  - records and accounts replace these;
     *  - a category with the name of one here (built in or made, same side of the
     *    ledger) is that one; any other is added, under a new id if its own is taken.
     *    Rules, budgets and records bring the categories they file into, even when the
     *    categories themselves were not chosen;
     *  - rules, names asked about, taught and dismissed kinds of message, ignored and
     *    added senders and budgets are added where nothing here says otherwise: on any
     *    disagreement, what is already here stays.
     *
     * [builtIn] is every built-in category, in each language it is named in.
     */
    fun merge(mine: JSONObject, theirs: JSONObject, parts: Set<Part>, builtIn: List<Category>): JSONObject {
        val ids = mergeCategories(mine, theirs, parts, builtIn)
        fun idOf(theirId: String) = ids[theirId] ?: theirId
        val known = builtIn.mapTo(HashSet()) { it.id } +
            mine.optJSONArray("categories").objects().map { it.getString("id") }

        if (Part.RECORDS in parts) {
            for (key in Part.RECORDS.keys) {
                val value = theirs.opt(key)
                if (value != null) mine.put(key, value) else mine.remove(key)
            }
            mine.optJSONArray("txns").objects().forEach { t -> t.optString("cat").takeIf { it.isNotEmpty() }?.let { t.put("cat", idOf(it)) } }
            mine.optJSONObject("overrides")?.let { o -> for (k in o.keys().asSequence().toList()) o.put(k, idOf(o.getString(k))) }
            // Kinds of message fingerprinted another way match nothing here; the next
            // scan works them out again.
            if (theirs.optInt("skeleton", Skeleton.VERSION) != Skeleton.VERSION) mine.remove("templates")
        }
        if (Part.RULES in parts) mergeRules(mine, theirs, ::idOf, known)
        if (Part.LISTS in parts) mergeLists(mine, theirs)
        if (Part.BUDGETS in parts) mergeBudgets(mine, theirs, ::idOf, known)
        return mine
    }

    /** Their category ids, each to the id it has here; the ones new to here are added. */
    private fun mergeCategories(
        mine: JSONObject,
        theirs: JSONObject,
        parts: Set<Part>,
        builtIn: List<Category>,
    ): Map<String, String> {
        val theirCats = theirs.optJSONArray("categories").objects()
        if (theirCats.isEmpty()) return emptyMap()
        // Without the categories themselves, only those the chosen parts file into come.
        val wanted: Set<String>? = if (Part.CATEGORIES in parts) null else buildSet {
            if (Part.RULES in parts) theirs.optJSONArray("rules").objects().forEach { add(it.optString("c")) }
            if (Part.BUDGETS in parts) theirs.optJSONArray("budgets").objects().forEach { b ->
                b.optJSONObject("lines")?.keys()?.forEach { add(it) }
            }
            if (Part.RECORDS in parts) {
                theirs.optJSONArray("txns").objects().forEach { add(it.optString("cat")) }
                theirs.optJSONObject("overrides")?.let { o -> o.keys().forEach { add(o.getString(it)) } }
            }
        }
        val myCats = mine.optJSONArray("categories") ?: JSONArray().also { mine.put("categories", it) }
        fun nameKey(name: String, income: Boolean) = name.trim().lowercase() + if (income) "|in" else "|out"
        val byName = HashMap<String, String>()
        builtIn.forEach { byName.putIfAbsent(nameKey(it.name, it.income), it.id) }
        myCats.objects().forEach { byName[nameKey(it.getString("name"), it.optBoolean("income"))] = it.getString("id") }
        val taken = builtIn.mapTo(HashSet()) { it.id } + myCats.objects().map { it.getString("id") }
        val used = HashSet(taken)
        val out = HashMap<String, String>()
        for (c in theirCats) {
            val id = c.getString("id")
            if (wanted != null && id !in wanted) continue
            val key = nameKey(c.getString("name"), c.optBoolean("income"))
            val same = byName[key]
            if (same != null) {
                out[id] = same
                continue
            }
            var newId = id
            var n = 2
            while (newId in used) newId = id + "_" + n++
            used += newId
            myCats.put(JSONObject(c.toString()).put("id", newId))
            byName[key] = newId
            out[id] = newId
        }
        return out
    }

    private fun mergeRules(mine: JSONObject, theirs: JSONObject, idOf: (String) -> String, known: Set<String>) {
        val rules = mine.optJSONArray("rules") ?: JSONArray().also { mine.put("rules", it) }
        val asked = mine.optJSONArray("askEachTime") ?: JSONArray().also { mine.put("askEachTime", it) }
        val patterns = rules.objects().mapTo(HashSet()) { it.getString("p") }
        val askedNames = asked.strings().toHashSet()
        val categories = known + mine.optJSONArray("categories").objects().map { it.getString("id") }
        for (r in theirs.optJSONArray("rules").objects()) {
            val p = r.getString("p")
            val c = idOf(r.getString("c"))
            if (p in patterns || p in askedNames || c !in categories) continue
            rules.put(JSONObject().put("p", p).put("c", c))
            patterns += p
        }
        for (name in theirs.optJSONArray("askEachTime").strings()) {
            if (name in patterns || name in askedNames) continue
            asked.put(name)
            askedNames += name
        }
    }

    private fun mergeLists(mine: JSONObject, theirs: JSONObject) {
        // Taught kinds: keyed by the fingerprint, so theirs are taken as they are when it
        // is this one, carried over through the messages here when it is the one before.
        val labels = mine.optJSONObject("labels") ?: JSONObject().also { mine.put("labels", it) }
        val theirLabels = theirs.optJSONObject("labels")
        if (theirLabels != null) {
            val incoming: Map<String, JSONObject> = when (theirs.optInt("skeleton", Skeleton.VERSION)) {
                Skeleton.VERSION -> theirLabels.keys().asSequence().associateWith { theirLabels.getJSONObject(it) }
                Skeleton.LEGACY_VERSION -> {
                    val parsed = theirLabels.keys().asSequence().mapNotNull { key ->
                        val l = theirLabels.getJSONObject(key)
                        runCatching { MessageKind.valueOf(l.getString("k")) }.getOrNull()
                            ?.let { key to TemplateLabel(it, l.optLong("at")) }
                    }.toMap()
                    Skeleton.carryOver(parsed, messagesIn(mine))
                        .mapValues { (_, l) -> JSONObject().put("k", l.kind.name).put("at", l.atMillis) }
                }
                else -> emptyMap()
            }
            for ((key, label) in incoming) if (!labels.has(key)) labels.put(key, label)
        }

        // Dismissed kinds, keyed afresh from their sample, the way the store loads them.
        val muted = mine.optJSONArray("muted") ?: JSONArray().also { mine.put("muted", it) }
        val mutedKeys = muted.objects().mapTo(HashSet()) { it.getString("t") }
        for (m in theirs.optJSONArray("muted").objects()) {
            val sample = m.optString("sample")
            val key = if (sample.isNotBlank()) Skeleton.of(m.getString("sen"), sample) else m.getString("t")
            if (key in mutedKeys) continue
            muted.put(JSONObject(m.toString()).put("t", key))
            mutedKeys += key
        }

        // Senders: added to each list unless this phone put them on the other one.
        fun lower(a: JSONArray?) = a.strings().mapTo(HashSet()) { it.trim().lowercase() }
        val ignored = mine.optJSONArray(IGNORED_SENDERS) ?: JSONArray().also { mine.put(IGNORED_SENDERS, it) }
        val added = mine.optJSONArray(ADDED_SENDERS) ?: JSONArray().also { mine.put(ADDED_SENDERS, it) }
        val myIgnored = lower(ignored)
        val myAdded = lower(added)
        for (s in theirs.optJSONArray(IGNORED_SENDERS).strings()) {
            val k = s.trim().lowercase()
            if (k !in myIgnored && k !in myAdded) ignored.put(s).also { myIgnored += k }
        }
        for (s in theirs.optJSONArray(ADDED_SENDERS).strings()) {
            val k = s.trim().lowercase()
            if (k !in myIgnored && k !in myAdded) added.put(s).also { myAdded += k }
        }
    }

    private fun mergeBudgets(mine: JSONObject, theirs: JSONObject, idOf: (String) -> String, known: Set<String>) {
        val budgets = mine.optJSONArray("budgets") ?: JSONArray().also { mine.put("budgets", it) }
        val categories = known + mine.optJSONArray("categories").objects().map { it.getString("id") }
        val periods = budgets.objects().mapTo(HashSet()) { it.optLong("start") to it.optLong("end") }
        val ids = budgets.objects().mapTo(HashSet()) { it.getString("id") }
        for (b in theirs.optJSONArray("budgets").objects()) {
            val period = b.optLong("start") to b.optLong("end")
            // A plan for the same dates is one already here: that one stays.
            if (period in periods) continue
            val lines = JSONObject()
            b.optJSONObject("lines")?.let { l ->
                for (k in l.keys()) idOf(k).takeIf { it in categories }?.let { lines.put(it, l.getLong(k)) }
            }
            var id = b.getString("id")
            var n = 2
            while (id in ids) id = b.getString("id") + "_" + n++
            ids += id
            periods += period
            budgets.put(JSONObject(b.toString()).put("id", id).put("lines", lines))
        }
        if (theirs.optBoolean(BUDGETS_ON)) mine.put(BUDGETS_ON, true)
    }

    /** Every message the store holds, as (sender, text): what labels are carried over through. */
    private fun messagesIn(store: JSONObject): Sequence<Pair<String, String>> =
        sequenceOf("templates", "txns", "reviews").flatMap { array ->
            store.optJSONArray(array).objects().asSequence().map { it.optString("sen") to it.optString("body") }
        }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { opt(it) as? JSONObject }
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun blank(rows: JSONArray?, key: String) {
        if (rows == null) return
        for (i in 0 until rows.length()) {
            val row = rows.opt(i) as? JSONObject ?: continue
            if (row.has(key)) row.put(key, "")
        }
    }
}
