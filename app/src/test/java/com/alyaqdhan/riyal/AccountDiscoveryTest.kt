package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.AccountDiscovery
import com.alyaqdhan.riyal.data.Banks
import com.alyaqdhan.riyal.data.SmsParser
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDiscoveryTest {

    private val parser = SmsParser(Prefs.DEFAULT_EXPENSE_KEYWORDS, Prefs.DEFAULT_INCOME_KEYWORDS, "OMR")
    private val base = 1_800_000_000_000L
    private fun hours(n: Long) = n * 3_600_000L

    private fun parsed(body: String): SmsParser.Result.Parsed =
        parser.parse(body) as SmsParser.Result.Parsed

    // ── what the parser now has to hand back ──

    @Test
    fun `the quoted balance is captured instead of thrown away`() {
        val r = parsed("Withdrawal of RO 12.500 from a/c XXXX1234 at LULU. Avl Bal RO 350.250")
        assertEquals(12_500L, r.amountMinor)     // the transaction, unchanged
        assertEquals(350_250L, r.balanceMinor)   // the balance, now kept
    }

    @Test
    fun `account tails are read from the shapes banks actually use`() {
        assertEquals("1234", parsed("Withdrawal of RO 1.000 from a/c XXXX1234 at X").accountTail)
        assertEquals("1234", parsed("Withdrawal of RO 1.000 from a/c ...1234 at X").accountTail)
        assertEquals("5678", parsed("Deposit of OMR 1.000 to account ending 5678").accountTail)
        assertEquals("4321", parsed("Purchase of OMR 2.000 on card *4321 at X").accountTail)
    }

    @Test
    fun `the real Bank Muscat format, masked in the middle, yields its tail`() {
        // Verbatim shape from a live inbox; the mask sits between two digit runs,
        // which an earlier leading-mask-only pattern missed entirely.
        val r = parsed(
            "OMR 12.500 is debited from your a/c 0427XXXXXXXX0019 on 23/08/2026 13:41:05. " +
                "New Available Balance is OMR 340.750."
        )
        assertEquals("0019", r.accountTail)
        assertEquals(12_500L, r.amountMinor)
        assertEquals(340_750L, r.balanceMinor)
    }

    @Test
    fun `the real Meethaq Arabic format yields its tail`() {
        // Verbatim shape from a live inbox: the word takes a suffix (حسابك), a "رقم"
        // qualifier follows, and the number runs into the next word with no space.
        val r = parsed("تم خصم 2.500 OMR من حسابك رقم 0630XXXXXXXX0001 بتاريخ 2026/08/25")
        assertEquals("0001", r.accountTail)

        val noSpace = parsed("تم إيداع 5.000 OMR إلى حسابك رقم  0630XXXXXXXX0002بتاريخ 2026/08/25")
        assertEquals("0002", noSpace.accountTail)

        val noQualifier = parsed(
            "لقد قمت بإرسال 1.000 OMR إلى ABU MOHAMMED من حسابك 0630XXXXXXXX0003 " +
                "باستخدام خدمات الدفع عبر الهاتف النقال"
        )
        assertEquals("0003", noQualifier.accountTail)
    }

    @Test
    fun `the Arabic "account ending in" phrasing yields its tail`() {
        val r = parsed("تم خصم 1.000 OMR من الحساب المنتهي ب 0019 بتاريخ 2026/08/25")
        assertEquals("0019", r.accountTail)
    }

    @Test
    fun `a balance is never mistaken for an account number`() {
        // "your balance in the account is 12.500" must not read 500 as an account.
        assertNull(parsed("تم خصم 2.500 OMR رصيدك في الحساب 12.500").accountTail)
    }

    @Test
    fun `a fully masked account number yields no tail rather than a wrong one`() {
        assertNull(parsed("Withdrawal of RO 1.000 from a/c XXXXXXXX at X").accountTail)
    }

    @Test
    fun `a reference number is not mistaken for an account tail`() {
        assertNull(parsed("Withdrawal of RO 1.000 at LULU ref 998877").accountTail)
    }

    @Test
    fun `transfer wording is flagged`() {
        assertTrue(parsed("Transfer of OMR 100.000 debited from a/c XXXX1234").transferHint)
        assertFalse(parsed("Purchase of OMR 100.000 at LULU").transferHint)
    }

    // ── turning those into accounts ──

    private fun obs(
        sender: String,
        tail: String?,
        balance: Long?,
        at: Long,
        signed: Long,
        currency: String = "OMR",
    ) = AccountDiscovery.Observation(sender, tail, currency, balance, at, signed)

    @Test
    fun `one account per sender and tail, named by the bank`() {
        val accounts = AccountDiscovery.propose(
            listOf(
                obs("BankMuscat", "1234", 350_250, base, -12_500),
                obs("BankMuscat", "5678", 900_000, base, -1_000),
                obs("NBO", "1111", 40_000, base, -500),
            )
        )
        assertEquals(3, accounts.size)
        assertTrue(accounts.any { it.bankName == "Bank Muscat" && it.last4 == "1234" })
        assertTrue(accounts.any { it.bankName == "National Bank of Oman" && it.last4 == "1111" })
    }

    @Test
    fun `a sender with several accounts gets no extra account for its untailed messages`() {
        // Bank Muscat's three real accounts were becoming four: the messages that
        // named no account invented a phantom "Main" the user does not have.
        val accounts = AccountDiscovery.propose(
            listOf(
                obs("BankMuscat", "0019", 350_250, base, -12_500),
                obs("BankMuscat", "0022", 100_000, base, -1_000),
                obs("BankMuscat", "0038", 5_000, base, -500),
                obs("BankMuscat", null, 900_000, base + hours(1), -2_000),
            )
        )
        assertEquals(3, accounts.size)
        assertEquals(setOf("0019", "0022", "0038"), accounts.mapNotNull { it.last4 }.toSet())
        assertTrue(accounts.none { it.last4 == null })
    }

    @Test
    fun `untailed messages join the sender's only account`() {
        val accounts = AccountDiscovery.propose(
            listOf(
                obs("BankMuscat", "1234", 350_250, base, -12_500),
                obs("BankMuscat", null, null, base + hours(1), -1_000),
            )
        )
        assertEquals(1, accounts.size)
        assertEquals("1234", accounts.single().last4)
    }

    @Test
    fun `the opening balance is the newest quoted one, valid from just after it`() {
        val accounts = AccountDiscovery.propose(
            listOf(
                obs("BankMuscat", "1234", 500_000, base, -10_000),
                obs("BankMuscat", "1234", 350_250, base + hours(5), -12_500),
                obs("BankMuscat", "1234", null, base + hours(9), -1_000),
            )
        )
        val account = accounts.single()
        assertEquals(350_250L, account.openingBalanceMinor)
        // Just after the quoting message, so that message's own amount isn't counted twice.
        assertEquals(base + hours(5) + 1, account.openingAtMillis)
        assertFalse(account.needsBalance)
    }

    @Test
    fun `an account whose balance was never quoted asks the user for one`() {
        val account = AccountDiscovery.propose(
            listOf(obs("SomeBank", "1234", null, base, -1_000))
        ).single()
        assertEquals(0L, account.openingBalanceMinor)
        assertTrue(account.needsBalance)
    }

    // ── the balance that comes out the other end ──

    @Test
    fun `balance counts only what happened after the opening moment`() {
        val account = Account(
            id = "acc_a", name = "Main", bankName = "Bank Muscat", last4 = "1234",
            currency = "OMR", openingBalanceMinor = 350_250, openingAtMillis = base,
        )
        val txns = listOf(
            txn("before", 99_000, base - hours(1), TxnType.EXPENSE, from = "acc_a"),
            txn("out", 10_000, base + hours(1), TxnType.EXPENSE, from = "acc_a"),
            txn("in", 4_000, base + hours(2), TxnType.INCOME, to = "acc_a"),
            txn("elsewhere", 50_000, base + hours(3), TxnType.EXPENSE, from = "acc_b"),
        )
        // 350.250 − 10.000 + 4.000; the pre-opening record is already baked in.
        assertEquals(344_250L, AccountDiscovery.balanceOf(account, txns))
    }

    @Test
    fun `a transfer moves money out of one account and into the other`() {
        val a = Account(
            id = "acc_a", name = "Main", bankName = "B", last4 = null,
            currency = "OMR", openingBalanceMinor = 100_000, openingAtMillis = base,
        )
        val b = a.copy(id = "acc_b", name = "Savings", openingBalanceMinor = 0L)
        val transfer = txn("t", 30_000, base + hours(1), TxnType.TRANSFER, from = "acc_a", to = "acc_b")
        assertEquals(70_000L, AccountDiscovery.balanceOf(a, listOf(transfer)))
        assertEquals(30_000L, AccountDiscovery.balanceOf(b, listOf(transfer)))
    }

    @Test
    fun `bank sender recognition covers brands that omit the word bank`() {
        assertTrue(Banks.looksLikeBank("BankMuscat"))
        assertTrue(Banks.looksLikeBank("NBO"))
        assertTrue(Banks.looksLikeBank("بنك نزوى"))
        assertFalse(Banks.looksLikeBank("Talabat"))
    }

    @Test
    fun `a short brand acronym does not fire inside an ordinary name`() {
        // "Makasib" contains "sib" and was being labelled Sharjah Islamic Bank.
        assertFalse(Banks.looksLikeBank("Makasib"))
        assertEquals("Makasib", Banks.displayName("Makasib"))
        // The acronym still works when it really is the sender.
        assertTrue(Banks.looksLikeBank("SIB"))
        assertEquals("Sharjah Islamic Bank", Banks.displayName("SIB"))
    }

    @Test
    fun `senders that are not banks and quote no balance get no account`() {
        // A telecom texting "payment received" is not somewhere money is held.
        val accounts = AccountDiscovery.propose(
            listOf(
                obs("Ooredoo", null, null, base, -5_000),
                obs("Omantel", null, null, base, -3_000),
                obs("BankMuscat", null, 350_250, base, -12_500),
            )
        )
        assertEquals(1, accounts.size)
        assertEquals("Bank Muscat", accounts.single().bankName)
    }

    @Test
    fun `an unrecognised sender that quotes balances is still treated as a bank`() {
        // Sender learning exists for exactly this: a bank the brand list doesn't know.
        val accounts = AccountDiscovery.propose(
            listOf(obs("MyLocalCU", "1234", 90_000, base, -1_000))
        )
        assertEquals(1, accounts.size)
        assertEquals("1234", accounts.single().last4)
    }

    // ── routing a message to one of them ──

    private fun account(id: String, tail: String?, sender: String = "BankMuscat") = Account(
        id = id, name = tail?.let { "Account ···$it" } ?: "Main", bankName = "Bank Muscat",
        last4 = tail, currency = "OMR", openingBalanceMinor = 0L, openingAtMillis = base,
        senderIds = setOf(sender),
    )

    @Test
    fun `a quoted tail wins over everything else`() {
        val accounts = listOf(account("a", "0019"), account("b", "0022"), account("c", null))
        assertEquals("b", AccountDiscovery.routeTo(accounts, "BankMuscat", "0022"))
    }

    @Test
    fun `a message naming no account goes to the sender's untailed account`() {
        // The regression that left 1735 records unassigned on real data while the
        // account created for exactly those messages sat empty.
        val accounts = listOf(account("a", "0019"), account("b", "0022"), account("c", null))
        assertEquals("c", AccountDiscovery.routeTo(accounts, "BankMuscat", null))
    }

    @Test
    fun `a sender with one account takes everything it sends`() {
        assertEquals("a", AccountDiscovery.routeTo(listOf(account("a", "0019")), "BankMuscat", null))
    }

    @Test
    fun `several tailed accounts and no untailed one stays unrouted rather than guessing`() {
        val accounts = listOf(account("a", "0019"), account("b", "0022"))
        assertNull(AccountDiscovery.routeTo(accounts, "BankMuscat", null))
    }

    @Test
    fun `another bank's message is not routed to this one`() {
        val accounts = listOf(account("a", null, sender = "BankMuscat"))
        assertNull(AccountDiscovery.routeTo(accounts, "Meethaq", null))
    }

    // ── how an account is named ──

    @Test
    fun `a discovered account is named after its bank and last digits`() {
        val accounts = AccountDiscovery.propose(
            listOf(
                obs("BankMuscat", "0019", 350_250, base, -12_500),
            ),
        )
        assertEquals(1, accounts.size)
        assertEquals("", accounts[0].name)
        assertEquals("Bank Muscat · 0019", accounts[0].displayName)
    }

    @Test
    fun `the name follows the bank and digits, and a nickname wins over both`() {
        val account = Account(
            id = "acc_a", name = "", bankName = "Bank Muscat", last4 = "0019",
            currency = "OMR", openingBalanceMinor = 0L, openingAtMillis = base,
        )
        assertEquals("Bank Muscat · 0019", account.displayName)
        assertEquals("Bank Muscat · 0021", account.copy(last4 = "0021").displayName)
        assertEquals("Bank Muscat", account.copy(last4 = null).displayName)
        assertEquals("Account · 0019", account.copy(bankName = "").displayName)
        assertEquals("Account", account.copy(bankName = "", last4 = null).displayName)
        assertEquals("Salary", account.copy(name = "Salary").displayName)
    }

    private fun txn(
        id: String,
        amount: Long,
        at: Long,
        type: TxnType,
        from: String? = null,
        to: String? = null,
    ) = Txn(
        id = id, atMillis = at, amountMinor = amount, currency = "OMR", type = type,
        fromAccountId = from, toAccountId = to, merchant = null, sender = "BankMuscat",
        body = "test", categoryId = "other", categorySource = "auto", confidence = 100,
    )

    // ── a sender that holds none of your accounts ──

    @Test
    fun `only a sender that holds one of your accounts can move your money`() {
        val accounts = listOf(
            Account(
                id = "acc_a", name = "", bankName = "Bank Muscat", last4 = "0019",
                currency = "OMR", openingBalanceMinor = 0L, openingAtMillis = base,
                senderIds = setOf("bank muscat"),
            ),
        )
        assertTrue(AccountDiscovery.isKnownSender(accounts, "bank muscat"))
        assertTrue(AccountDiscovery.isKnownSender(accounts, "Bank Muscat"))
        // A telecom, a shop or a broadcaster texts about money it will never move.
        assertFalse(AccountDiscovery.isKnownSender(accounts, "Oman TV"))
        assertFalse(AccountDiscovery.isKnownSender(accounts, "Ooredoo"))
        assertFalse(AccountDiscovery.isKnownSender(accounts, ""))
    }
}
