package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.Categorizer
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.SmsParser
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is pure Kotlin, so the whole read-classify pipeline is verified here
 * against realistic Omani bank message shapes — including Arabic digits and the
 * strict "withdraw/deposit only" gate.
 */
class SmsParserTest {

    private val expense = Prefs.DEFAULT_EXPENSE_KEYWORDS
    private val income = Prefs.DEFAULT_INCOME_KEYWORDS
    private val parser = SmsParser(expense, income, "OMR")

    @Test
    fun withdrawalParses_balanceExcluded_merchantFound() {
        val r = parser.parse(
            "Withdrawal of RO 12.500 from a/c ...1234 at LULU HYPERMARKET MUSCAT on 02/07/26. Avl Bal RO 350.250",
        )
        assertTrue(r is SmsParser.Result.Parsed)
        r as SmsParser.Result.Parsed
        assertEquals(Direction.EXPENSE, r.direction)
        assertEquals(12500L, r.amountMinor) // OMR keeps 3 decimals (baisa)
        assertEquals("OMR", r.currency)
        assertTrue(r.merchant!!.contains("LULU"))
    }

    @Test
    fun `money that arrived is not recorded as money that left`() {
        // "لقد استلمت" - I received. The message also says "خدمات الدفع" (payment
        // services), and "دفع" is an expense keyword, so with no income keyword to
        // beat it these were filed as spending: 19 records in one real inbox, worth
        // OMR 791.765, counted the wrong way round in every total on every screen.
        val r = parser.parse(
            "عزيزي الزبون، لقد استلمت OMR 2.000 من MOHAMMED ABDULLAH في حسابك " +
                "0630XXXXXXXX0001 باستخدام خدمات الدفع عبر الهاتف النقال/ المحفظة " +
                "الإلكترونية. رصيدك الحالي هو OMR 5.495"
        )
        assertTrue(r is SmsParser.Result.Parsed)
        r as SmsParser.Result.Parsed
        assertEquals(Direction.INCOME, r.direction)
        assertEquals(2_000L, r.amountMinor)
    }

    @Test
    fun `a service notice is not a question for the user`() {
        // Verbatim from Review. Each says "الدفع" or "Payment", so it read as a
        // transaction, found no amount, and became something to decide about.
        val notices = listOf(
            "عزيزي الزبون ، لقد قمت بإلغاء تفعيل خدمات الدفع عبر الهاتف النقال بنجاح. " +
                "في حالة رغبتك في إعادة التفعيل ، يرجى تسجيل الدخول إلى تطبيق بنك مسقط.",
            "عزيزي الزبون ، لقد قمت بتفعيل خدمات الدفع بواسطة الهاتف النقال بنجاح.",
            "This is to remind you that your National Payment Debit Card Maal for the " +
                "A/C ending with 0022 is ready to collect from Bahla Branch.",
        )
        notices.forEach { assertTrue(it, parser.parse(it) is SmsParser.Result.Skipped) }
    }

    @Test
    fun `a real payment is still read`() {
        // The gate above must not swallow the thing it sits next to.
        assertTrue(
            parser.parse(
                "تم خصم 3.400 OMR من حسابك رقم 0630XXXXXXXX0001 بإستخدام بطاقة الخصم " +
                    "المباشر في 5842-Al Fatah Food Com LLC B بتاريخ 12/08/2026 19:02:41."
            ) is SmsParser.Result.Parsed
        )
    }

    // ── merchants in Arabic ──
    //
    // Every shape below is verbatim from a live inbox, digits aside. Before these, the
    // merchant patterns were English-only and 468 of 481 real records carried no
    // merchant at all - and with no merchant the category picker cannot offer to
    // remember anything, so the same shop was filed by hand every single month.

    private fun merchantOf(body: String): String? =
        (parser.parse(body) as SmsParser.Result.Parsed).merchant

    @Test
    fun `a card purchase names the shop after في, without its till number`() {
        assertEquals(
            "Al Fatah Food Com LLC B",
            merchantOf(
                "تم خصم 3.400 OMR من حسابك رقم 0630XXXXXXXX0001 بإستخدام بطاقة الخصم " +
                    "المباشر في 5842-Al Fatah Food Com LLC B بتاريخ 12/08/2026 19:02:41. " +
                    "رصيدك الحالي هو 45.200 OMR."
            ),
        )
    }

    @Test
    fun `money sent names the person after إلى, not the account it left`() {
        assertEquals(
            "MD REPON",
            merchantOf(
                "عزيزي الزبون، لقد قمت بإرسال OMR 5.000 إلى MD REPON من حسابك " +
                    "0630XXXXXXXX0001 باستخدام خدمات الدفع عبر االهاتف النقال/ المحفظة " +
                    "الإلكترونية. رقم المعاملةMTHQ123. رصيدك الحالي هو OMR 40.200."
            ),
        )
    }

    @Test
    fun `money received names the sender after من`() {
        assertEquals(
            "SUHAIB MOHAMMED",
            merchantOf(
                "عزيزي الزبون، لقد استلمت OMR 12.000 من SUHAIB MOHAMMED في حسابك " +
                    "0427XXXXXXXX0019 باستخدام خدمات الدفع عبر الهاتف النقال/ المحفظة " +
                    "الإلكترونية. رقم المعاملةBMCT456. رصيدك الحالي هو OMR 52.200."
            ),
        )
    }

    @Test
    fun `a message that only names its channel gives the channel`() {
        // 113 of one inbox's records: no shop, no person, just how the money left.
        assertEquals(
            "MBI",
            merchantOf(
                "تم خصم 20.000 OMR من حسابك رقم  0630XXXXXXXX0001بتاريخ 26/08/2026 " +
                    "13:41:05عن طريق MBI.رصيدك الحالي في الحساب هو 150.500 OMR."
            ),
        )
    }

    @Test
    fun `the balance is never mistaken for a merchant`() {
        // "في" sits before a balance as often as before a shop: "رصيدك الحالي في
        // الحساب هو 150.500" would otherwise file 113 records under "الحساب هو 150",
        // a different merchant for every balance the account happened to hold.
        val m = merchantOf(
            "تم خصم 20.000 OMR من حسابك رقم  0630XXXXXXXX0001بتاريخ 26/08/2026 " +
                "13:41:05عن طريق MBI.رصيدك الحالي في الحساب هو 150.500 OMR."
        )
        assertTrue(m == null || !m!!.contains("حساب"))
    }

    @Test
    fun `an English message keeps the name it always had`() {
        // The bank writes "to NAME from your a/c", and "from" was not among the words
        // that end a merchant name, so the name kept a dangling preposition.
        assertEquals(
            "SUHAIB MOHAMMED",
            merchantOf(
                "Dear Customer, You have sent OMR 8.000 to SUHAIB MOHAMMED from your " +
                    "a/c 0427XXXXXXXX0019 on 26/08/2026 13:41:05 using Mobile Payment " +
                    "services. Txn Id BMCT789. Avl Bal OMR 44.200."
            ),
        )
    }

    @Test
    fun `a message that names nobody invents nobody`() {
        assertEquals(
            null,
            merchantOf(
                "OMR 12.500 is debited from your a/c 0427XXXXXXXX0019 on 28/08/2026 " +
                    "14:05:11. New Available Balance is OMR 340.750."
            ),
        )
    }

    @Test
    fun depositWithThousandsSeparatorParses() {
        val r = parser.parse("Deposit of OMR 1,250.000 to your account XX5678. Avl Bal OMR 1,600.000")
        assertTrue(r is SmsParser.Result.Parsed)
        r as SmsParser.Result.Parsed
        assertEquals(Direction.INCOME, r.direction)
        assertEquals(1_250_000L, r.amountMinor)
        assertEquals("OMR", r.currency)
    }

    @Test
    fun arabicMessageWithArabicDigitsParses() {
        val r = parser.parse("تم سحب مبلغ ر.ع ٢٥٫٥٠٠ من حسابك لدى LULU")
        assertTrue(r is SmsParser.Result.Parsed)
        r as SmsParser.Result.Parsed
        assertEquals(Direction.EXPENSE, r.direction)
        assertEquals(25_500L, r.amountMinor)
        assertEquals("OMR", r.currency)
    }

    @Test
    fun messageWithoutKeywordIsSkippedUnread() {
        val r = parser.parse("Your OTP code is 1234. Do not share it with anyone.")
        assertTrue(r is SmsParser.Result.Skipped)
    }

    @Test
    fun keywordWithoutAmountGoesToReview() {
        val r = parser.parse("Withdrawal request received for your account")
        assertTrue(r is SmsParser.Result.NeedsReview)
        assertEquals("no amount found", (r as SmsParser.Result.NeedsReview).reason)
    }

    @Test
    fun balanceOnlyAmountGoesToReview() {
        val r = parser.parse("Withdrawal alert. Avl Bal RO 100.000")
        assertTrue(r is SmsParser.Result.NeedsReview)
        assertEquals("only balance-like amounts found", (r as SmsParser.Result.NeedsReview).reason)
    }

    @Test
    fun bareAmountFallsBackToDefaultCurrencyWithLowerConfidence() {
        val r = parser.parse("Deposited 100.000 into your wallet")
        assertTrue(r is SmsParser.Result.Parsed)
        r as SmsParser.Result.Parsed
        assertEquals(Direction.INCOME, r.direction)
        assertEquals(100_000L, r.amountMinor)
        assertEquals("OMR", r.currency)
        assertTrue(r.confidence < 100)
    }

    @Test
    fun sarUsesTwoDecimals() {
        val r = parser.parse("Withdrawal of SAR 50.00 at STARBUCKS RIYADH")
        assertTrue(r is SmsParser.Result.Parsed)
        r as SmsParser.Result.Parsed
        assertEquals(5_000L, r.amountMinor)
        assertEquals("SAR", r.currency)
    }

    @Test
    fun categorizerMapsOmaniMerchants() {
        val lulu = Categorizer.categorize(Direction.EXPENSE, "LULU HYPERMARKET", "withdrawal at LULU", emptyList())
        assertEquals("groceries", lulu.categoryId)

        val coffee = Categorizer.categorize(Direction.EXPENSE, null, "Withdrawal at COSTA COFFEE", emptyList())
        assertEquals("food", coffee.categoryId) // "coffee" must not trip the "fee" rule

        val unknown = Categorizer.categorize(Direction.EXPENSE, null, "Withdrawal at XYZ", emptyList())
        assertEquals("other", unknown.categoryId)

        val salary = Categorizer.categorize(Direction.INCOME, null, "Salary deposit received", emptyList())
        assertEquals("salary", salary.categoryId)
    }

    @Test
    fun debitedAndCreditedWordingIsCaughtByDefaultGate() {
        // Real Omani bank phrasing that the old withdraw/deposit-only gate used to miss.
        val debit = parser.parse("Your a/c XX1234 debited OMR 4.200 at OOMCO SEEB on 03/07/26. Bal OMR 88.000")
        assertTrue(debit is SmsParser.Result.Parsed)
        debit as SmsParser.Result.Parsed
        assertEquals(Direction.EXPENSE, debit.direction)
        assertEquals(4_200L, debit.amountMinor)

        val credit = parser.parse("Your account has been credited with OMR 500.000 from MOD PAYROLL")
        assertTrue(credit is SmsParser.Result.Parsed)
        credit as SmsParser.Result.Parsed
        assertEquals(Direction.INCOME, credit.direction)
        assertEquals(500_000L, credit.amountMinor)

        val purchase = parser.parse("Purchase of OMR 2.500 at TALABAT with card ending 9876")
        assertTrue(purchase is SmsParser.Result.Parsed)
        assertEquals(Direction.EXPENSE, (purchase as SmsParser.Result.Parsed).direction)
    }

    @Test
    fun categorizerUsesSenderAsSignal() {
        val bySender = Categorizer.categorize(
            Direction.EXPENSE, null, "debited OMR 3.000 for order 4412", emptyList(), sender = "Talabat",
        )
        assertEquals("food", bySender.categoryId)
    }

    @Test
    fun categorizerMatchesArabicKeywords() {
        val salary = Categorizer.categorize(Direction.INCOME, null, "تم إيداع راتب شهر يوليو", emptyList())
        assertEquals("salary", salary.categoryId)
        val fuel = Categorizer.categorize(Direction.EXPENSE, null, "شراء وقود من المحطة", emptyList())
        assertEquals("transport", fuel.categoryId)
    }

    @Test
    fun moneyFormatsOmrWithBaisa() {
        assertEquals("OMR 12.500", Money.format(12_500, "OMR"))
        assertEquals("USD 3.50", Money.format(350, "USD"))
        assertEquals(1_500L, Money.toMinor(BigDecimal("1.5"), "OMR"))
        assertEquals(3, Money.decimalsFor("OMR"))
        assertEquals(2, Money.decimalsFor("USD"))
    }

    // ── advertising is not a transaction ──

    @Test
    fun `a prize draw advert is not money leaving your account`() {
        // Real message from a bank's own sender: it contains "purchase" and an amount,
        // and was being recorded as OMR 50 of spending.
        val body = "Dear Customer, Be among the winners of a total cash prize worth " +
            "OMR 2,250 with Meethaq Mobile & Internet Banking! Register, pay bills or " +
            "purchase a gift card. T&Cs apply."
        val result = parser.parse(body)
        assertTrue("advert must not become a transaction", result is SmsParser.Result.Skipped)
    }

    @Test
    fun `a salary-transfer offer is not income`() {
        val body = "Dear customer, Transfer your salary to Meethaq & get a 15% cash " +
            "bonus with a minimum salary transfer of just OMR 500. Valid till 31 July 2026. " +
            "T&Cs apply."
        val result = parser.parse(body)
        assertTrue("advert must not become income", result is SmsParser.Result.Skipped)
    }

    @Test
    fun `a real debit is still read even though banks advertise from the same sender`() {
        val body = "OMR 88.425 is debited from your a/c 0427XXXXXXXX0019 on " +
            "23/07/2026 12:36:06. New Available Balance is OMR 0.000."
        val result = parser.parse(body) as SmsParser.Result.Parsed
        assertEquals(88_425L, result.amountMinor)
    }

    // ── the bank's own transaction time ──

    @Test
    fun `the bank's timestamp is read from the body, not the message's arrival`() {
        val debit = "تم خصم 500.000 OMR من حسابك رقم  0630XXXXXXXX0001بتاريخ " +
            "2026/07/23 10:21:53عن طريق MBI.رصيدك الحالي في الحساب هو 0.170 OMR."
        val credit = "تم إيداع 500.000 OMR إلى حسابك رقم  0630XXXXXXXX0003بتاريخ " +
            "2026/07/23 10:21:53عن طريق MBI.رصيدك الحالي في الحساب هو 660.265 OMR."
        val a = parser.parse(debit) as SmsParser.Result.Parsed
        val b = parser.parse(credit) as SmsParser.Result.Parsed
        assertEquals("2026/07/23 10:21:53", a.bankStamp)
        // Same stamp on both halves is what proves they are one movement of money,
        // even though these two texts arrived 53 minutes apart.
        assertEquals(a.bankStamp, b.bankStamp)
    }

    @Test
    fun `a TV prize draw is not the biggest expense of your life`() {
        // The real message: Oman TV offering a car and "cash prizes up to 60,000 OMR".
        // It was recorded as OMR 60,000 spent - the largest expense in the history.
        val body = "برنامج البيت عبر تلفزيون سلطنة عُمان يمنحكم فرصة الفوز بالسيارة " +
            "الثانية اليوم 26/3 بالإضافة الى جوائز نقدية تصل الى 60,000 ر.ع " +
            "للمشاركة في السحب أرسل \"البيت\" إلى 91794 525 بيسة للرسالة"
        assertTrue(parser.parse(body) is SmsParser.Result.Skipped)
    }

    // ── a bank also texts about money that did not move ──

    @Test
    fun `a payment request is not income, nobody has paid yet`() {
        val body = "Dear Customer, You have received a payment request of OMR 2.0 from " +
            "SUHAIB MOHAMMED. Txn Id BMCT013392425587. Please login to accept/reject the payment."
        assertTrue(parser.parse(body) is SmsParser.Result.Skipped)
    }

    @Test
    fun `a cancelled standing order is not a payment`() {
        // "دفع" appears inside "أوامر الدفع الدائمة", so this read as money out.
        val body = "عزيزي الزبون، تم حذف طلبك لأوامر الدفع الدائمة ACH Payment بنجاح."
        assertTrue(parser.parse(body) is SmsParser.Result.Skipped)
    }

    @Test
    fun `one message naming both accounts is a transfer, not spending`() {
        val body = "OMR 196.850 is debited from your A/C 0372XXXXXXXX0038 and credited " +
            "to your A/C 0372XXXXXXXX0022 on 31/12/2025 10:12:10."
        val r = parser.parse(body) as SmsParser.Result.Parsed
        assertEquals(196_850L, r.amountMinor)
        assertEquals("0022", r.selfTransferTo)
    }

    @Test
    fun `an ordinary debit names no second account`() {
        val body = "OMR 12.500 is debited from your a/c 0372XXXXXXXX0022 on 13/06/2024 " +
            "14:32:46. New Available Balance is OMR 1.036."
        val r = parser.parse(body) as SmsParser.Result.Parsed
        assertEquals(null, r.selfTransferTo)
    }

    @Test
    fun `one shop written in two languages is one name`() {
        // Verbatim shapes from a real inbox. The Arabic message names the shop and
        // then stops; the English one runs on into the account it was paid from, and
        // the cut that removes that clause used to leave its "in" behind - so the same
        // person arrived as "alis salim" and "alis salim in" and was asked about twice.
        val arabic = parser.parse(
            "تم خصم OMR 25.000 من حسابك 0630XXXXXXXX0001 إلى ALIS SALIM. رصيدك OMR 120.500"
        )
        val english = parser.parse(
            "OMR 25.000 debited to ALIS SALIM in a/c 0630XXXXXXXX0022. Avl Bal OMR 120.500"
        )
        assertTrue(arabic is SmsParser.Result.Parsed)
        assertTrue(english is SmsParser.Result.Parsed)
        assertEquals("ALIS SALIM", (arabic as SmsParser.Result.Parsed).merchant)
        assertEquals("ALIS SALIM", (english as SmsParser.Result.Parsed).merchant)
    }

    @Test
    fun `a connector inside the name is part of the name`() {
        val r = parser.parse("Purchase of RO 4.500 at MADE IN OMAN STORE on 02/07/26")
        assertTrue(r is SmsParser.Result.Parsed)
        assertEquals("MADE IN OMAN STORE", (r as SmsParser.Result.Parsed).merchant)
    }

}
