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
}
