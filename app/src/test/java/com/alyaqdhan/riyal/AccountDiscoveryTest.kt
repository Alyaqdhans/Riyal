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
    fun `a card's digits are told apart from an account number`() {
        val card = parsed("Purchase of OMR 2.000 on card *4321 at LULU")
        assertEquals("4321", card.accountTail)
        assertTrue(card.tailIsCard)

        // Both quoted: the message is about the account, the card is just how it was reached.
        val both = parsed("Purchase of OMR 2.000 on card *4321 from a/c XXXX0019. Avl Bal OMR 10.000")
        assertEquals("0019", both.accountTail)
        assertFalse(both.tailIsCard)
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

    // ── accounts that appear later, one bank at a time ──

    @Test
    fun `a bank that never texted before gets an account on the scan that hears from it`() {
        // What a fresh start looks like on day two: nothing existed, then one bank
        // spoke, then another. The first account must survive the second discovery.
        val muscat = AccountDiscovery.propose(
            listOf(obs("BankMuscat", "0019", 350_250, base, -12_500))
        )
        val added = AccountDiscovery.proposeMissing(
            muscat,
            listOf(
                obs("BankMuscat", "0019", 340_000, base + hours(2), -10_250),
                obs("NBO", "1111", 40_000, base + hours(3), -500),
            )
        )
        assertEquals(1, added.size)
        assertEquals("National Bank of Oman", added.single().bankName)
        assertEquals("1111", added.single().last4)
    }

    @Test
    fun `a bank's message placed in another bank's account founds no account of its own`() {
        // NBO writing that a payment was released to you: money arriving at another
        // bank. Answered in Review with the account it went to, it names that account,
        // and NBO - which holds none of yours - must not become a bank of its own.
        val existing = AccountDiscovery.propose(listOf(obs("BANK NIZWA", "0019", 350_250, base, -12_500)))
        val released = obs("NBO", null, null, base + hours(3), 10_000)
        assertEquals(1, AccountDiscovery.proposeMissing(existing, listOf(released)).size)
        assertTrue(
            AccountDiscovery.proposeMissing(existing, listOf(released.copy(accountId = existing.single().id))).isEmpty()
        )
    }

    @Test
    fun `messages that land in an account we already have add nothing`() {
        val existing = AccountDiscovery.propose(
            listOf(obs("BankMuscat", "0019", 350_250, base, -12_500))
        )
        val added = AccountDiscovery.proposeMissing(
            existing,
            listOf(
                obs("BankMuscat", "0019", 340_000, base + hours(2), -10_250),
                // No tail: it belongs to the sender's only account, not to a new one.
                obs("BankMuscat", null, null, base + hours(3), -1_000),
            )
        )
        assertTrue(added.isEmpty())
    }

    @Test
    fun `a known bank's untailed message never mints a second account for it`() {
        // The phantom-account bug, in its later-scan form: with two accounts already
        // known, an untailed message routes nowhere - and must still invent nothing.
        val existing = AccountDiscovery.propose(
            listOf(
                obs("BankMuscat", "0019", 350_250, base, -12_500),
                obs("BankMuscat", "0022", 100_000, base, -1_000),
            )
        )
        assertEquals(2, existing.size)
        val added = AccountDiscovery.proposeMissing(
            existing,
            listOf(obs("BankMuscat", null, 900_000, base + hours(4), -2_000))
        )
        assertTrue(added.isEmpty())
    }

    @Test
    fun `a new account number at a bank we know is still a new account`() {
        // After a fresh start the accounts arrive one at a time, and the second one
        // must not be swallowed by the first: the bank named a number we don't hold,
        // and named it again.
        val existing = AccountDiscovery.propose(
            listOf(obs("BankMuscat", "0019", 350_250, base, -12_500))
        )
        val added = AccountDiscovery.proposeMissing(
            existing,
            listOf(
                obs("BankMuscat", "0038", 5_000, base + hours(6), -500),
                obs("BankMuscat", "0038", 4_500, base + hours(7), -500),
            )
        )
        assertEquals("0038", added.single().last4)
    }

    @Test
    fun `a number a bank we know quotes once is not an account yet`() {
        // It is as likely a reference or the other party's account as a new account of
        // yours; its message waits unassigned rather than founding one.
        val existing = AccountDiscovery.propose(
            listOf(obs("BankMuscat", "0019", 350_250, base, -12_500))
        )
        val one = obs("BankMuscat", "0038", 5_000, base + hours(6), -500)
        assertTrue(AccountDiscovery.proposeMissing(existing, listOf(one)).isEmpty())
        assertNull(AccountDiscovery.routeTo(existing, "BankMuscat", "0038"))
    }

    // ── the digits that are not an account ──

    private fun seen(
        tail: String?,
        balance: Long,
        at: Long,
        signed: Long,
        line: Int?,
        accountId: String? = null,
    ) = AccountDiscovery.Observation(
        "BANK NIZWA", tail, "OMR", balance, base + hours(at), signed, line = line, accountId = accountId,
    )

    /** One account's balance line: purchases naming nothing, withdrawals naming the account, a reference. */
    private val oneLine = listOf(
        seen(null, 310_420, 1, -2_750, line = 1),
        seen("005", 306_420, 2, -4_000, line = 1),
        seen(null, 304_820, 3, -1_600, line = 1),
        seen("4410", 301_830, 4, -2_990, line = 1),
        seen("005", 299_830, 5, -2_000, line = 1),
        seen("7716", 298_730, 6, -1_100, line = 1),
    )

    @Test
    fun `numbers on one balance line other than the account's are not accounts`() {
        // One Bank Nizwa account came back as seven: every reference and every other
        // party's number founded an account of its own.
        val accounts = AccountDiscovery.propose(oneLine)
        assertEquals(1, accounts.size)
        assertEquals("005", accounts.single().last4)
        // The newest balance on the line opens it.
        assertEquals(298_730L, accounts.single().openingBalanceMinor)
    }

    @Test
    fun `two balance lines that each quote their own number are two accounts`() {
        val savings = listOf(
            seen("0038", 900_000, 2, 10_000, line = 2),
            seen("0038", 910_000, 4, 10_000, line = 2),
        )
        val accounts = AccountDiscovery.propose(oneLine + savings)
        assertEquals(setOf("005", "0038"), accounts.map { it.last4 }.toSet())
    }

    @Test
    fun `a reference after the last stored record lands in the account it belongs to`() {
        // An incremental pass: the account's last stored record is read back, and the
        // new message continues its balance while quoting someone else's number.
        val existing = AccountDiscovery.propose(oneLine)
        val id = existing.single().id
        val observations = listOf(
            seen("005", 299_830, 5, -2_000, line = 9, accountId = id),
            seen("9182", 297_830, 7, -2_000, line = 9),
        )
        assertTrue(AccountDiscovery.proposeMissing(existing, observations).isEmpty())
        assertEquals(id, AccountDiscovery.lineAccounts(existing, observations)[9])
    }

    @Test
    fun `an account made from a reference is folded back into the real one`() {
        val main = AccountDiscovery.propose(oneLine).single()
        val phantom = main.copy(id = "acc_phantom", last4 = "4410")
        // Named or not: the bank's messages, by majority, say it was never an account.
        val named = main.copy(id = "acc_named", last4 = "7716", name = "Mum's")
        val folded = AccountDiscovery.foreignAccounts(listOf(main, phantom, named), oneLine)
        assertEquals(mapOf("acc_phantom" to main.id, "acc_named" to main.id), folded)
    }

    @Test
    fun `a bank's only account made from where its number starts takes the number's end`() {
        val withHead = oneLine.map { if (it.accountTail == "005") it.copy(head = "00350") else it }
        val lone = AccountDiscovery.propose(withHead).single().copy(last4 = "0350")
        assertEquals(mapOf(lone.id to "005"), AccountDiscovery.renumbered(listOf(lone), withHead))
    }

    @Test
    fun `a number a few messages quote beside the bank's main one folds into it`() {
        // Two refunds said "0350" on the main account's own balance line.
        val main = AccountDiscovery.propose(oneLine).single()
        val stray = main.copy(id = "acc_stray", last4 = "0350", name = "Named")
        val few = List(2) { seen("0350", 300_000L + it, 10L + it, 1_000, line = 1) }
        val many = oneLine + List(30) { seen("005", 200_000L + it, 20L + it, -100, line = 1) }
        assertEquals(mapOf("acc_stray" to main.id), AccountDiscovery.minorityFolds(listOf(main, stray), many + few))
        // A rarely used account with a balance line of its own, in use alongside, stays.
        val ownLine = List(3) { seen("0350", 50_000L - it, 25L + it, -10, line = 9) }
        assertTrue(AccountDiscovery.minorityFolds(listOf(main, stray), many + ownLine).isEmpty())
        // Many transfers to someone's "0350", on the main account's balance line, fold.
        val transfers = List(12) { seen("0350", 210_000L + it, 21L + it, -500, line = 1) }
        assertEquals(mapOf("acc_stray" to main.id), AccountDiscovery.minorityFolds(listOf(main, stray), many + transfers))
        // A line of its own, but all before the main account's first message: the same
        // account under an older number.
        val older = List(3) { seen("0350", 50_000L - it, -200L + it, -10, line = 9) }
        assertEquals(mapOf("acc_stray" to main.id), AccountDiscovery.minorityFolds(listOf(main, stray), many + older))
    }

    // ── which group of a masked number is the account ──

    private fun masked(vararg groups: String, at: Long, line: Int?) =
        seen(groups.last().takeLast(4), 100_000L - at, at, -100, line = line).copy(groups = groups.toList())

    private fun settled(observations: List<AccountDiscovery.Observation>) =
        AccountDiscovery.resolveMaskedTails(observations).mapNotNull { it.accountTail }.toSet()

    @Test
    fun `one account written every which way is the group all its numbers share`() {
        // Bank Nizwa's formats, with made-up times: before a renumbering "002…001",
        // after it "001 … 00350" spaced and unspaced. One balance line runs through.
        val older = List(4) { masked("002", "001", at = it.toLong(), line = 1) }
        val newer = List(4) { masked("001", "00350", at = 10L + it, line = 1) }
        assertEquals(setOf("001"), settled(older + newer))
        // The balance line broken at the renumbering: two runs sharing "001" that
        // never overlap are still one account.
        val split = older + newer.map { it.copy(line = 2) }
        assertEquals(setOf("001"), settled(split))
    }

    @Test
    fun `an account an earlier build made from the branch group folds into the real one`() {
        val observed = AccountDiscovery.resolveMaskedTails(
            List(4) { masked("005", "00350", at = it.toLong(), line = 1) }
        )
        val main = AccountDiscovery.propose(observed).single()
        val stale = main.copy(id = "acc_stale", last4 = "0350")
        assertEquals("005", main.last4)
        assertEquals(mapOf("acc_stale" to main.id), AccountDiscovery.headFolds(listOf(main, stale), observed))
    }

    @Test
    fun `two accounts at one branch are told apart by their ends`() {
        val a = List(4) { masked("001", "00350", at = it.toLong() * 2, line = 1) }
        val b = List(4) { masked("002", "00350", at = it.toLong() * 2 + 1, line = 2) }
        val tails = AccountDiscovery.resolveMaskedTails(a + b).map { it.accountTail }
        assertEquals(List(4) { "001" } + List(4) { "002" }, tails)
    }

    @Test
    fun `an account only ever written one way is known by its shorter group`() {
        assertEquals(setOf("001"), settled(List(3) { masked("001", "00350", at = it.toLong(), line = 1) }))
        assertEquals(setOf("0019"), settled(List(3) { masked("0427", "0019", at = it.toLong(), line = 1) }))
    }

    @Test
    fun `someone else's number on an account's balance line is left to itself`() {
        // A transfer quoting only the other party's number, at another branch, sits on
        // the account's balance line by the balance it carries. Joining the two left an
        // account whose numbers share no group, and choosing between none stopped the scan.
        val own = List(4) { masked("0350", "0019", at = it.toLong() * 2, line = 1) }
        val theirs = masked("0630", "0022", at = 3L, line = 1)
        assertEquals(List(4) { "0019" } + "0022", AccountDiscovery.resolveMaskedTails(own + theirs).map { it.accountTail })
        // Two numbers each sharing a different group with the line's own: nothing
        // decides, so every number keeps the parser's answer.
        val branch = masked("0350", "0777", at = 5L, line = 1)
        val end = masked("0630", "0019", at = 7L, line = 1)
        assertEquals(List(4) { "0019" } + "0777" + "0019", AccountDiscovery.resolveMaskedTails(own + branch + end).map { it.accountTail })
    }

    @Test
    fun `a sender is one bank however it is spelled`() {
        val main = AccountDiscovery.propose(oneLine).single()
        val spelled = main.copy(senderIds = setOf("BankNizwa"))
        assertEquals(main.id, AccountDiscovery.ownerOf(listOf(spelled), "BANK NIZWA", "005"))
    }

    @Test
    fun `an account kept under the wrong digits by a merge takes the ones its messages end with`() {
        // "001" merged into "0210" the wrong way round: one account, known by the branch.
        val main = AccountDiscovery.propose(oneLine).single()
        val wrong = main.copy(last4 = "0350", aliases = setOf("005"))
        assertEquals(mapOf(main.id to "005"), AccountDiscovery.renumbered(listOf(wrong), oneLine))
        // Its own digits still quoted: left as it is.
        assertTrue(AccountDiscovery.renumbered(listOf(main.copy(aliases = setOf("4410"))), oneLine).isEmpty())
    }

    @Test
    fun `an account made from where a masked number starts folds into where it ends`() {
        // "0350" came from the start of "00350 ###### 005". Even named, and with no message
        // left to say where it was opened, it was never an account.
        val withHead = oneLine.map { if (it.accountTail == "005") it.copy(head = "00350") else it }
        val main = AccountDiscovery.propose(withHead).single()
        val phantom = main.copy(id = "acc_phantom", last4 = "0350", name = "Savings", openingAtMillis = base - hours(100))
        assertEquals(mapOf("acc_phantom" to main.id), AccountDiscovery.foreignAccounts(listOf(main, phantom), withHead))
        // A number some message does end with is an account of its own.
        // A number some message does end with, with a balance of its own while the main
        // account is in use, is an account of its own.
        val quoted = withHead + seen("0350", 50_000, 3, -1_000, line = 2)
        assertTrue(AccountDiscovery.foreignAccounts(listOf(main, phantom), quoted).isEmpty())
    }

    @Test
    fun `an account read from a number no message quotes any more goes back to the real one`() {
        // An earlier reading made "0350" out of "00350 ###### 005". Read properly, the
        // message it was opened from belongs to 005, and nothing quotes 0350 at all.
        val main = AccountDiscovery.propose(oneLine).single()
        val founding = oneLine[1]
        val misread = main.copy(
            id = "acc_misread", last4 = "0350",
            openingBalanceMinor = founding.balanceMinor!!, openingAtMillis = founding.atMillis + 1,
        )
        assertEquals(mapOf("acc_misread" to main.id), AccountDiscovery.foreignAccounts(listOf(main, misread), oneLine))
        // An account with no such message, or one the user named, is left alone.
        val other = misread.copy(id = "acc_other", openingAtMillis = base - hours(100))
        assertTrue(AccountDiscovery.foreignAccounts(listOf(main, other), oneLine).isEmpty())
    }

    @Test
    fun `digits folded into an account route to it from then on`() {
        val account = account("a", "005", sender = "BANK NIZWA").copy(aliases = setOf("4410"))
        assertEquals("a", AccountDiscovery.routeTo(listOf(account, account("b", "0038", "BANK NIZWA")), "BANK NIZWA", "4410"))
        assertEquals("a", AccountDiscovery.ownerOf(listOf(account), "bank nizwa", "4410"))
    }

    @Test
    fun `a card's digits never become an account`() {
        // A card belongs to an account without sharing its number, so its digits can
        // neither define an account nor keep a message out of the one it belongs to.
        val existing = AccountDiscovery.propose(
            listOf(obs("BankMuscat", "0019", 350_250, base, -12_500))
        )
        val card = AccountDiscovery.Observation(
            sender = "BankMuscat", accountTail = "4321", currency = "OMR",
            balanceMinor = 340_000, atMillis = base + hours(2), signedMinor = -2_000,
            tailIsCard = true,
        )
        assertTrue(AccountDiscovery.proposeMissing(existing, listOf(card)).isEmpty())
        assertEquals(
            existing.single().id,
            AccountDiscovery.routeTo(existing, "BankMuscat", "4321", tailIsCard = true),
        )
    }

    @Test
    fun `with nothing known yet it is an ordinary first discovery`() {
        val added = AccountDiscovery.proposeMissing(
            emptyList(),
            listOf(obs("BankMuscat", "0019", 350_250, base, -12_500))
        )
        assertEquals(1, added.size)
        assertEquals("0019", added.single().last4)
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

    // ── one bank, spelled several ways ──

    @Test
    fun `a bank that varies how it spells its sender is still one account`() {
        // A tester's two banks came back as six accounts. Senders were grouped by their
        // exact text, so every casing the bank used started another account.
        val accounts = AccountDiscovery.propose(
            listOf(
                obs("BankMuscat", "1234", 350_250, base, -12_500),
                obs("BANKMUSCAT", "1234", 340_000, base + hours(1), -10_250),
                obs("bankmuscat", "1234", 330_000, base + hours(2), -10_000),
            )
        )
        assertEquals(1, accounts.size)
        assertEquals("1234", accounts.single().last4)
    }

    @Test
    fun `every proposed account has an id of its own`() {
        // The ids are derived from the lowercased sender, so spelling variants collided
        // on one id while still being separate accounts. Two rows sharing a key is what
        // brought the accounts screen down: LazyColumn rejects a duplicate key outright.
        val accounts = AccountDiscovery.propose(
            listOf(
                obs("BankMuscat", null, 350_250, base, -12_500),
                obs("BANKMUSCAT", null, 340_000, base + hours(1), -10_250),
                obs("Bank Muscat", null, 330_000, base + hours(2), -10_000),
                obs("NBO", "1111", 40_000, base, -500),
                obs("nbo", "1111", 39_500, base + hours(1), -500),
            )
        )
        assertEquals(accounts.size, accounts.map { it.id }.distinct().size)
    }

    @Test
    fun `a sender that differs only by surrounding space is one account`() {
        val accounts = AccountDiscovery.propose(
            listOf(
                obs("NBO", "1111", 40_000, base, -500),
                obs(" NBO ", "1111", 39_500, base + hours(1), -500),
            )
        )
        assertEquals(1, accounts.size)
    }
}
