package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Backup
import com.alyaqdhan.riyal.data.Backup.Part
import com.alyaqdhan.riyal.data.Category
import com.alyaqdhan.riyal.data.Skeleton
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Backups made of parts, and bringing them in beside what is already here. Made-up data. */
class BackupTest {

    private val store = """
        {"v":2,
         "txns":[{"id":"t1","at":1,"amt":4500,"cur":"OMR","type":"EXPENSE","sen":"BankMuscat",
                  "body":"purchase of OMR 4.500 at SPAR","mer":"SPAR","cat":"u_coffee",
                  "src":"builtin","conf":80,"man":false}],
         "reviews":[{"id":"r1","at":2,"sen":"NBO","body":"something unreadable",
                     "reason":"no_amount","state":"open"}],
         "muted":[{"t":"old key","sen":"NBO","sample":"OTP is 123456","at":3}],
         "templates":[{"key":"k","sen":"NBO","n":1,"sid":"s","body":"Balance is OMR 1.000","at":4}],
         "accounts":[{"id":"acc_1","name":"","bank":"Bank Muscat","last4":"0019"}],
         "categories":[{"id":"u_coffee","name":"Coffee","income":false,"color":0,"icon":""}],
         "rules":[{"p":"spar","c":"u_coffee"}],
         "askEachTime":["ahmed"],
         "skeleton":3,
         "labels":{"nbo|balance is ¤":{"k":"NONE","at":5}},
         "budgets":[{"id":"bp_1","label":"May","start":100,"end":200,"lines":{"u_coffee":5000}}]}
    """.trimIndent()

    private val settings = JSONObject()
        .put(Backup.IGNORED_SENDERS, JSONArray(listOf("COLLEGE")))
        .put(Backup.ADDED_SENDERS, JSONArray(listOf("WalletCo")))
        .put(Backup.BUDGETS_ON, true)

    private val builtIn = listOf(
        Category("food", "Food"),
        Category("salary", "Salary", income = true),
    )

    private fun written(parts: Set<Part>, messages: Boolean = true) =
        JSONObject(Backup.write(store, settings, parts, messages))

    // ── writing ──

    @Test
    fun `a backup holds only the parts chosen`() {
        val out = written(setOf(Part.CATEGORIES, Part.RULES))
        assertTrue(out.has("categories") && out.has("rules") && out.has("askEachTime"))
        assertFalse(out.has("txns") || out.has("accounts") || out.has("budgets") || out.has("labels"))
        assertEquals(2, out.getInt("v"))
        assertEquals(listOf("categories", "rules"), (0 until out.getJSONArray("parts").length()).map { out.getJSONArray("parts").getString(it) })
    }

    @Test
    fun `lists and budgets carry the settings that belong to them`() {
        val out = written(setOf(Part.LISTS, Part.BUDGETS))
        assertEquals("COLLEGE", out.getJSONArray(Backup.IGNORED_SENDERS).getString(0))
        assertEquals("WalletCo", out.getJSONArray(Backup.ADDED_SENDERS).getString(0))
        assertTrue(out.getBoolean(Backup.BUDGETS_ON))
        assertEquals(3, out.getInt("skeleton"))
    }

    @Test
    fun `without messages every text is blanked but kept as a key`() {
        val out = written(Part.entries.toSet(), messages = false)
        assertEquals("", out.getJSONArray("txns").getJSONObject(0).getString("body"))
        assertEquals("", out.getJSONArray("reviews").getJSONObject(0).getString("body"))
        assertEquals("", out.getJSONArray("muted").getJSONObject(0).getString("sample"))
        assertEquals("", out.getJSONArray("templates").getJSONObject(0).getString("body"))
        assertEquals("SPAR", out.getJSONArray("txns").getJSONObject(0).getString("mer"))
        assertFalse(Backup.hasMessages(out.toString()))
        assertTrue(Backup.hasMessages(written(Part.entries.toSet()).toString()))
    }

    @Test
    fun `what a backup holds is the parts it was saved with, counted`() {
        val contents = Backup.contents(written(setOf(Part.CATEGORIES, Part.BUDGETS)).toString())
        assertEquals(mapOf(Part.CATEGORIES to 1, Part.BUDGETS to 1), contents)
    }

    @Test
    fun `a backup saved before parts holds every part with anything in it`() {
        val old = """{"v":2,"txns":[{"id":"t1"}],"categories":[],"rules":[{"p":"x","c":"food"}],"labels":{},"muted":[],"budgets":[]}"""
        assertEquals(mapOf(Part.RECORDS to 1, Part.RULES to 1), Backup.contents(old))
        assertEquals(2, Backup.versionOf(old))
        assertEquals(0, Backup.versionOf("""{"txns":[]}"""))
    }

    // ── bringing in ──

    private fun mine() = JSONObject(
        """
        {"v":2,
         "txns":[{"id":"m1","sen":"BankMuscat","body":"mine","cat":"food"}],
         "categories":[{"id":"u_coffee","name":"Tea","income":false,"color":0,"icon":""},
                       {"id":"u_kids","name":"Kids","income":false,"color":0,"icon":""}],
         "rules":[{"p":"spar","c":"food"}],
         "askEachTime":[],
         "labels":{},
         "muted":[],
         "budgets":[{"id":"bp_1","label":"May","start":100,"end":200,"lines":{"food":1000}}],
         "ignoredSenders":["WalletCo"],
         "addedSenders":[]}
        """.trimIndent()
    )

    private fun theirs(json: String) = JSONObject(json)

    @Test
    fun `a category of the same name is the one already here, built in or made`() {
        val friend = theirs(
            """{"v":2,"categories":[
                 {"id":"u_kids","name":"kids","income":false},
                 {"id":"u_food","name":"Food","income":false},
                 {"id":"u_coffee","name":"Coffee","income":false},
                 {"id":"u_gym","name":"Gym","income":false}]}"""
        )
        val merged = Backup.merge(mine(), friend, setOf(Part.CATEGORIES), builtIn)
        val cats = merged.getJSONArray("categories")
        val names = (0 until cats.length()).map { cats.getJSONObject(it).getString("name") }
        // "kids" and "Food" were already here; their "Coffee" takes a new id, since ours
        // is a category called Tea.
        assertEquals(listOf("Tea", "Kids", "Coffee", "Gym"), names)
        assertEquals("u_coffee_2", cats.getJSONObject(2).getString("id"))
    }

    @Test
    fun `learned categories bring their categories, and ours win on a name`() {
        val friend = theirs(
            """{"v":2,
                "categories":[{"id":"u_gym","name":"Gym","income":false},{"id":"u_spare","name":"Spare","income":false}],
                "rules":[{"p":"spar","c":"u_gym"},{"p":"fitness world","c":"u_gym"},{"p":"nowhere","c":"u_lost"}],
                "askEachTime":["ahmed","spar"]}"""
        )
        val merged = Backup.merge(mine(), friend, setOf(Part.RULES), builtIn)
        val rules = merged.getJSONArray("rules")
        assertEquals(
            listOf("spar" to "food", "fitness world" to "u_gym"),
            (0 until rules.length()).map { rules.getJSONObject(it).let { r -> r.getString("p") to r.getString("c") } },
        )
        val cats = merged.getJSONArray("categories")
        // Only the category the rules file into came; "Spare" was not asked for.
        assertEquals(listOf("Tea", "Kids", "Gym"), (0 until cats.length()).map { cats.getJSONObject(it).getString("name") })
        assertEquals(listOf("ahmed"), (0 until merged.getJSONArray("askEachTime").length()).map { merged.getJSONArray("askEachTime").getString(it) })
    }

    @Test
    fun `lists add what is missing without crossing what is here`() {
        val friend = theirs(
            """{"v":2,"skeleton":3,
                "labels":{"nbo|balance is ¤":{"k":"NONE","at":5}},
                "muted":[{"t":"stale","sen":"NBO","sample":"Your OTP is 4821","at":6}],
                "ignoredSenders":["Telco","walletco"],
                "addedSenders":["WALLETCO","College"]}"""
        )
        val merged = Backup.merge(mine(), friend, setOf(Part.LISTS), builtIn)
        assertEquals("NONE", merged.getJSONObject("labels").getJSONObject("nbo|balance is ¤").getString("k"))
        assertEquals(Skeleton.of("NBO", "Your OTP is 4821"), merged.getJSONArray("muted").getJSONObject(0).getString("t"))
        fun list(key: String) = merged.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
        assertEquals(listOf("WalletCo", "Telco"), list(Backup.IGNORED_SENDERS))
        assertEquals(listOf("College"), list(Backup.ADDED_SENDERS))
    }

    @Test
    fun `labels from the fingerprint before are carried over through the messages here`() {
        val shop = "Card of a/c 0123XXXXXXXX0045 used for OMR 1.250 at Some Cafe Oman on 05 MAY 2026 10:15. Avl Bal OMR 2.500."
        val here = mine().put(
            "txns",
            JSONArray().put(JSONObject().put("id", "m1").put("sen", "bank muscat").put("body", shop).put("cat", "food")),
        )
        val friend = theirs("""{"v":2,"skeleton":2,"labels":{}}""").apply {
            getJSONObject("labels").put(Skeleton.legacyOf("bank muscat", shop), JSONObject().put("k", "OUT").put("at", 9))
        }
        val merged = Backup.merge(here, friend, setOf(Part.LISTS), builtIn)
        assertEquals("OUT", merged.getJSONObject("labels").getJSONObject(Skeleton.of("bank muscat", shop)).getString("k"))
    }

    @Test
    fun `budgets for new dates come in, with their categories`() {
        val friend = theirs(
            """{"v":2,
                "categories":[{"id":"u_gym","name":"Gym","income":false}],
                "budgets":[{"id":"bp_1","label":"May","start":100,"end":200,"lines":{"food":9}},
                           {"id":"bp_1","label":"June","start":200,"end":300,"lines":{"u_gym":7000,"u_lost":5}}],
                "budgetsOn":true}"""
        )
        val merged = Backup.merge(mine().put(Backup.BUDGETS_ON, false), friend, setOf(Part.BUDGETS), builtIn)
        val budgets = merged.getJSONArray("budgets")
        assertEquals(2, budgets.length())
        assertEquals(1000, budgets.getJSONObject(0).getJSONObject("lines").getLong("food"))
        val june = budgets.getJSONObject(1)
        assertEquals("bp_1_2", june.getString("id"))
        assertEquals(7000, june.getJSONObject("lines").getLong("u_gym"))
        assertFalse(june.getJSONObject("lines").has("u_lost"))
        assertTrue(merged.getBoolean(Backup.BUDGETS_ON))
    }

    @Test
    fun `records replace these, filed under the categories they came with`() {
        val merged = Backup.merge(mine(), JSONObject(store), setOf(Part.RECORDS), builtIn)
        val txn = merged.getJSONArray("txns").getJSONObject(0)
        assertEquals("t1", txn.getString("id"))
        assertEquals(1, merged.getJSONArray("txns").length())
        // Their "Coffee" is not our "Tea": it came in under its own new id.
        assertEquals("u_coffee_2", txn.getString("cat"))
        assertEquals("acc_1", merged.getJSONArray("accounts").getJSONObject(0).getString("id"))
        // What was not chosen stays as it was.
        assertEquals("food", merged.getJSONArray("rules").getJSONObject(0).getString("c"))
    }
}
