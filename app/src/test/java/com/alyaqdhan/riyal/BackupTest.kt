package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Backup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val store = """
        {"v":2,
         "txns":[{"id":"t1","at":1,"amt":4500,"cur":"OMR","type":"EXPENSE","sen":"BankMuscat",
                  "body":"purchase of OMR 4.500 at SPAR","mer":"SPAR","cat":"groceries",
                  "src":"builtin","conf":80,"man":false}],
         "reviews":[{"id":"r1","at":2,"sen":"NBO","body":"something unreadable",
                     "reason":"no_amount","state":"open"}],
         "muted":[{"t":"OTP is ###","sen":"NBO","sample":"OTP is 123456","at":3}],
         "accounts":[{"id":"acc_1","name":"","bank":"Bank Muscat","last4":"0019"}]}
    """.trimIndent()

    @Test
    fun `including messages leaves the file exactly as it was`() {
        assertEquals(store, Backup.of(store, includeMessages = true))
    }

    @Test
    fun `excluding messages blanks the text but keeps the keys`() {
        val out = JSONObject(Backup.of(store, includeMessages = false))
        val txn = out.getJSONArray("txns").getJSONObject(0)
        assertTrue(txn.has("body"))
        assertEquals("", txn.getString("body"))
        assertEquals("", out.getJSONArray("reviews").getJSONObject(0).getString("body"))
        assertEquals("", out.getJSONArray("muted").getJSONObject(0).getString("sample"))
    }

    @Test
    fun `excluding messages keeps everything that is not a message`() {
        val txn = JSONObject(Backup.of(store, includeMessages = false))
            .getJSONArray("txns").getJSONObject(0)
        assertEquals(4500, txn.getLong("amt"))
        assertEquals("SPAR", txn.getString("mer"))
        assertEquals("groceries", txn.getString("cat"))
        assertEquals("BankMuscat", txn.getString("sen"))
    }

    @Test
    fun `accounts and the muted template survive either way`() {
        val out = JSONObject(Backup.of(store, includeMessages = false))
        assertEquals("Bank Muscat", out.getJSONArray("accounts").getJSONObject(0).getString("bank"))
        assertEquals("OTP is ###", out.getJSONArray("muted").getJSONObject(0).getString("t"))
    }

    @Test
    fun `the version comes back so an old backup can be refused`() {
        assertEquals(2, Backup.versionOf(store))
        assertEquals(0, Backup.versionOf("""{"txns":[]}"""))
    }

    @Test
    fun `a backup says whether it carries any message text`() {
        assertTrue(Backup.hasMessages(store))
        assertFalse(Backup.hasMessages(Backup.of(store, includeMessages = false)))
    }

    @Test
    fun `counting records does not need the text`() {
        assertEquals(1, Backup.countOf(store))
        assertEquals(1, Backup.countOf(Backup.of(store, includeMessages = false)))
    }
}
