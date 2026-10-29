package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.BalanceChain
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.MessageKind
import com.alyaqdhan.riyal.data.Skeleton
import com.alyaqdhan.riyal.data.SmsParser
import com.alyaqdhan.riyal.data.TemplateInference
import com.alyaqdhan.riyal.data.Tutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of reading a bank that are learned rather than listed. Every message here is
 * the shape of a real one, digits aside.
 */
class LearningTest {

    // ── templates ──

    private val fatah = "تم خصم 3.400 OMR من حسابك رقم 0630XXXXXXXX0001 بإستخدام بطاقة الخصم " +
        "المباشر في 5842-Al Fatah Food Com LLC B بتاريخ 12/08/2026 19:02:41. رصيدك الحالي هو 45.200 OMR."
    private val twin = "تم خصم 2.000 OMR من حسابك رقم 0630XXXXXXXX0001 بإستخدام بطاقة الخصم " +
        "المباشر في 655185-OMAN OIL KUMAILIA TWIN MUSC بتاريخ 2026/10/06 13:45:56. رصيدك الحالي هو 1.015 OMR."

    @Test
    fun `two purchases at different shops are one template`() {
        assertEquals(Skeleton.of("BankMuscat", fatah), Skeleton.of("BankMuscat", twin))
    }

    @Test
    fun `the template keeps the words and drops what the bank filled in`() {
        val key = Skeleton.of("BankMuscat", fatah)
        assertTrue(key, key.startsWith("bankmuscat|"))
        assertTrue(key, "خصم" in key)
        assertFalse(key, "Fatah" in key || "fatah" in key)
        assertFalse(key, "5842" in key || "3.400" in key)
    }

    @Test
    fun `money out and money in are different templates`() {
        val out = "OMR 12.500 is debited from your a/c 0427XXXXXXXX0019 on 28/08/2026 14:05:11. " +
            "New Available Balance is OMR 340.750."
        val inn = "OMR 12.500 is credited to your a/c 0427XXXXXXXX0019 on 28/08/2026 14:05:11. " +
            "New Available Balance is OMR 353.250."
        assertNotEquals(Skeleton.of("BankMuscat", out), Skeleton.of("BankMuscat", inn))
    }

    @Test
    fun `a capitalised shop in an English message is masked`() {
        assertEquals(
            Skeleton.of("NBO", "Purchase of OMR 2.500 at TALABAT with card ending 9876"),
            Skeleton.of("NBO", "Purchase of OMR 4.100 at LULU HYPERMARKET with card ending 1234"),
        )
    }

    @Test
    fun `an ATM is a word of the template, not a shop`() {
        assertNotEquals(
            Skeleton.of("NBO", "Cash withdrawal of OMR 20.000 at ATM SEEB"),
            Skeleton.of("NBO", "Cash withdrawal of OMR 20.000 at LULU SEEB"),
        )
    }

    @Test
    fun `the same words from another bank are another template`() {
        assertNotEquals(Skeleton.of("BankMuscat", fatah), Skeleton.of("BankDhofar", fatah))
    }

    // ── balances ──

    private fun point(
        id: String,
        at: Long,
        amount: Long,
        balance: Long,
        tail: String? = "0001",
        sender: String = "BankNizwa",
    ) = BalanceChain.Point(id, sender, tail, at, amount, "OMR", balance, "OMR")

    @Test
    fun `a balance that falls by exactly the amount proves money out, and rises proves money in`() {
        val proven = BalanceChain.directions(
            listOf(
                point("a", 1, 1_000, 100_000),
                point("b", 2, 5_000, 95_000),
                point("c", 3, 10_000, 105_000),
            )
        )
        assertNull(proven["a"])
        assertEquals(Direction.EXPENSE, proven["b"])
        assertEquals(Direction.INCOME, proven["c"])
    }

    @Test
    fun `a gap in the messages proves nothing`() {
        // 95.000 to 80.000 is 15, not the 5 this message moved: something in between
        // was never texted, so nothing is claimed about either.
        val proven = BalanceChain.directions(listOf(point("a", 1, 1_000, 95_000), point("b", 2, 5_000, 80_000)))
        assertTrue(proven.isEmpty())
    }

    @Test
    fun `two accounts are two balances`() {
        val chains = BalanceChain.chain(
            listOf(point("a", 1, 1_000, 100_000, "0001"), point("b", 2, 5_000, 495_000, "0022"))
        )
        assertTrue(chains.directions.isEmpty())
        assertNotEquals(chains.lineOf["a"], chains.lineOf["b"])
    }

    @Test
    fun `a number that continues another's balance is the same account`() {
        // Bank Nizwa: purchases name no account, a withdrawal names the account, and a
        // transfer can name the other party's. The balance runs through all of them.
        val chains = BalanceChain.chain(
            listOf(
                point("pos", 1, 2_750, 310_420, tail = null),
                point("atm", 2, 4_000, 306_420, tail = "005"),
                point("pos2", 3, 1_600, 304_820, tail = null),
                point("other", 4, 2_990, 301_830, tail = "4410"),
            )
        )
        assertEquals(Direction.EXPENSE, chains.directions["other"])
        assertEquals(1, setOf("pos", "atm", "pos2", "other").map { chains.lineOf[it] }.distinct().size)
    }

    @Test
    fun `a balance is continued from further back when another account texted in between`() {
        val chains = BalanceChain.chain(
            listOf(
                point("a1", 1, 1_000, 100_000, "0001"),
                point("b1", 2, 2_000, 50_000, "0022"),
                point("a2", 3, 5_000, 95_000, "0001"),
            )
        )
        assertEquals(Direction.EXPENSE, chains.directions["a2"])
        assertEquals(chains.lineOf["a1"], chains.lineOf["a2"])
        assertNotEquals(chains.lineOf["a1"], chains.lineOf["b1"])
    }

    @Test
    fun `moving money between two accounts does not make them one balance`() {
        // Everything out of one account into an empty one: the first ends at 0 and the
        // second starts at 0, so the second's balance is the first's plus the amount.
        val chains = BalanceChain.chain(
            listOf(point("out", 1, 100_000, 0, "0001"), point("in", 2, 100_000, 100_000, "0022"))
        )
        assertTrue(chains.directions.isEmpty())
        assertNotEquals(chains.lineOf["out"], chains.lineOf["in"])
    }

    @Test
    fun `one bank's balance never continues another's`() {
        val chains = BalanceChain.chain(
            listOf(
                point("a", 1, 1_000, 100_000, sender = "BankMuscat"),
                point("b", 2, 5_000, 95_000, sender = "BankDhofar"),
            )
        )
        assertTrue(chains.directions.isEmpty())
    }

    @Test
    fun `order is by time, not by the order the messages were read in`() {
        val proven = BalanceChain.directions(listOf(point("b", 2, 5_000, 95_000), point("a", 1, 1_000, 100_000)))
        assertEquals(Direction.EXPENSE, proven["b"])
    }

    // ── what a template adds up to ──

    private fun obs(
        id: String,
        read: Direction? = null,
        proven: Direction? = null,
        skipped: Boolean = false,
        key: String = "bm|t",
    ) = TemplateInference.Observation(
        key = key, sender = "BankMuscat", messageId = id, atMillis = id.hashCode().toLong(),
        body = "", read = read, proven = proven, selfTransfer = false, skipped = skipped,
        hasAmount = !skipped,
    )

    @Test
    fun `balances beat the words`() {
        // "لقد استلمت ... باستخدام خدمات الدفع": the payment-services phrase read as money
        // out, nineteen times. The balances said in, and the balances are arithmetic.
        val stat = TemplateInference.infer(
            listOf(
                obs("1", read = Direction.EXPENSE, proven = Direction.INCOME),
                obs("2", read = Direction.EXPENSE, proven = Direction.INCOME),
                obs("3", read = Direction.EXPENSE),
            )
        ).getValue("bm|t")
        assertEquals(MessageKind.IN, stat.guess)
        assertTrue(TemplateInference.isProven(stat))
    }

    @Test
    fun `a purchase the advert filter dropped belongs to a proven template`() {
        // The TWIN station: same template as every other card purchase, so once that
        // template is proven the dropped message can be read for what it is.
        val stat = TemplateInference.infer(
            listOf(
                obs("1", read = Direction.EXPENSE, proven = Direction.EXPENSE),
                obs("2", read = Direction.EXPENSE, proven = Direction.EXPENSE),
                obs("twin", skipped = true),
            )
        ).getValue("bm|t")
        assertTrue(TemplateInference.isProven(stat))
        assertEquals(Direction.EXPENSE, stat.guess?.direction)
    }

    @Test
    fun `one proof is not enough to overrule anything`() {
        val stat = TemplateInference.infer(listOf(obs("1", proven = Direction.EXPENSE), obs("2"))).getValue("bm|t")
        assertFalse(TemplateInference.isProven(stat))
    }

    @Test
    fun `words alone are believed, but not fully`() {
        val stat = TemplateInference.infer(
            listOf(obs("1", read = Direction.EXPENSE), obs("2", read = Direction.EXPENSE))
        ).getValue("bm|t")
        assertEquals(MessageKind.OUT, stat.guess)
        assertTrue(stat.confidence < 0.9f)
        assertFalse(TemplateInference.isProven(stat))
    }

    @Test
    fun `a template that is only ever skipped is guessed to be nothing`() {
        val stat = TemplateInference.infer(listOf(obs("1", skipped = true), obs("2", skipped = true))).getValue("bm|t")
        assertEquals(MessageKind.NONE, stat.guess)
    }

    // ── reading a message as a kind already known ──

    @Test
    fun `a message with no direction word reads fully once its kind is known`() {
        val parser = SmsParser(Prefs.DEFAULT_EXPENSE_KEYWORDS, Prefs.DEFAULT_INCOME_KEYWORDS, "OMR")
        val body = "Your card ending XXXX7777 was used for OMR 5.500 at LULU HYPERMARKET on 02/09/26"
        assertTrue(parser.parse(body) is SmsParser.Result.NeedsReview)
        val read = parser.parseAs(body, Direction.EXPENSE)!!
        assertEquals(5_500L, read.amountMinor)
        assertEquals(Direction.EXPENSE, read.direction)
        assertTrue(read.merchant!!.startsWith("LULU HYPERMARKET"))
    }

    // ── the questions ──

    private fun tutor() = Tutor(
        listOf(
            Tutor.Candidate("a", "BankMuscat", 500, 0.9f),
            Tutor.Candidate("b", "BankMuscat", 200, 0.9f),
            Tutor.Candidate("c", "NBO", 100, 0.9f),
            Tutor.Candidate("d", "NBO", 50, 0.9f),
            Tutor.Candidate("e", "NBO", 30, 0.9f),
            Tutor.Candidate("f", "BankMuscat", 20, 0.9f),
        )
    )

    private fun run(t: Tutor, answers: (Int) -> Boolean): Int {
        var i = 0
        while (true) {
            val q = t.next() ?: break
            t.answer(q.key, answers(i++))
        }
        return i
    }

    @Test
    fun `the biggest kind of message is asked about first`() {
        assertEquals("a", tutor().next()?.key)
    }

    @Test
    fun `right answers end the questions sooner than wrong ones`() {
        val allRight = run(tutor()) { true }
        val twoWrong = run(tutor()) { it < 2 }
        assertTrue("all right took $allRight, two wrong took $twoWrong", twoWrong > allRight)
    }

    @Test
    fun `at least a few questions are asked even when the app is sure`() {
        assertTrue(run(tutor()) { true } >= Tutor.MIN_QUESTIONS)
    }

    @Test
    fun `the estimate of questions left matches what confirming every one takes`() {
        val t = tutor()
        val estimate = t.questionsLeft()
        assertEquals(estimate, run(t) { true })
    }

    @Test
    fun `nothing to ask about is done at once`() {
        val t = Tutor(emptyList())
        assertTrue(t.done)
        assertNull(t.next())
        assertEquals(1f, t.progress)
    }
}
