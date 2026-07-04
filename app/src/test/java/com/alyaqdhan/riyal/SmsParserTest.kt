package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Money
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

    private val expense = setOf("withdraw", "withdrawal", "withdrawn", "سحب")
    private val income = setOf("deposit", "deposited", "إيداع", "ايداع")
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
    fun moneyFormatsOmrWithBaisa() {
        assertEquals("OMR 12.500", Money.format(12_500, "OMR"))
        assertEquals("USD 3.50", Money.format(350, "USD"))
        assertEquals(1_500L, Money.toMinor(BigDecimal("1.5"), "OMR"))
        assertEquals(3, Money.decimalsFor("OMR"))
        assertEquals(2, Money.decimalsFor("USD"))
    }
}
