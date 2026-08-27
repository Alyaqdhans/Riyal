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
}
