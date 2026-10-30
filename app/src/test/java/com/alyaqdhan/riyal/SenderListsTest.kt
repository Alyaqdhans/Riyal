package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.SenderLists
import com.alyaqdhan.riyal.data.SenderLists.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Lists shows each sender the way the scan's gate treats it. Made-up senders. */
class SenderListsTest {

    private val accounts = listOf(
        Account(
            id = "acc_1", name = "", bankName = "Some Wallet", last4 = "0045", currency = "OMR",
            openingBalanceMinor = 0L, openingAtMillis = 0L, senderIds = setOf("WalletCo"),
        ),
    )

    private fun status(sender: String, ignored: Set<String> = emptySet(), added: Set<String> = emptySet()) =
        SenderLists.statusOf(sender, accounts, ignored, added)

    @Test
    fun `a bank by its name or by an account is read`() {
        assertEquals(Status.BANK, status("BankMuscat"))
        assertEquals(Status.BANK, status("WALLETCO"))
    }

    @Test
    fun `anyone else is left out, a phone number as a person`() {
        assertEquals(Status.LEFT_OUT, status("COLLEGE"))
        assertEquals(Status.PHONE, status("+968 9123 4567"))
    }

    @Test
    fun `what the user said wins, however the sender is spelled`() {
        assertEquals(Status.IGNORED, status("BankMuscat", ignored = setOf("bankmuscat ")))
        assertEquals(Status.ADDED, status("COLLEGE", added = setOf("college")))
        assertEquals(Status.ADDED, status("+968 9123 4567", added = setOf("+968 9123 4567")))
        // Added and a bank anyway: it is read because it is a bank.
        assertEquals(Status.BANK, status("BankMuscat", added = setOf("BankMuscat")))
    }

    @Test
    fun `every sender once, read first, then left out, then ignored`() {
        val rows = SenderLists.rows(
            senders = listOf("COLLEGE", "BankMuscat", "BANKMUSCAT", "Shop"),
            accounts = accounts,
            ignored = setOf("Shop"),
            added = setOf("Telco"),
        )
        assertEquals(listOf("BankMuscat", "Telco", "COLLEGE", "Shop"), rows.map { it.sender })
        assertEquals(listOf(Status.BANK, Status.ADDED, Status.LEFT_OUT, Status.IGNORED), rows.map { it.status })
        assertTrue(rows[0].bank)
        assertFalse(rows[3].bank)
    }
}
