package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.core.Prefs
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.Skeleton
import com.alyaqdhan.riyal.data.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bank Nizwa's Arabic alerts, in the bank's own shape with made-up shops, figures and
 * digits. Purchases put the shop where the account number usually goes, withdrawals mask
 * the account with "#", and the card network's own name\city\country field is pasted
 * in as it is.
 */
class BankNizwaFormatTest {

    private val parser = SmsParser(Prefs.DEFAULT_EXPENSE_KEYWORDS, Prefs.DEFAULT_INCOME_KEYWORDS, "OMR")

    private fun parsed(body: String) = parser.parse(body) as SmsParser.Result.Parsed

    private val purchase = "تم خصم 2.750 ر.ع من حسابك رقم CAFE NOVA\\AL MAABELA\\OM\\778812omaOMN " +
        "في 14-OCT , 09:12:40 الرصيد المتوفر هو 310.420 ر.ع"

    private val withdrawal = "تم سحب 6.000 ر.ع من حساب رقم 005######00350 الى SALI####AHMD. " +
        "الرصيد المتوفر هو 304.420 ر.ع"

    @Test
    fun `a purchase reads its amount, balance and shop, and names no account`() {
        val r = parsed(purchase)
        assertEquals(Direction.EXPENSE, r.direction)
        assertEquals(2_750L, r.amountMinor)
        assertEquals(310_420L, r.balanceMinor)
        assertEquals("CAFE NOVA", r.merchant)
        assertNull(r.accountTail)
    }

    @Test
    fun `the currency can come before the amount`() {
        val r = parsed(
            "تم خصم ر.ع 2.750 من حسابك رقم CAFE NOVA\\AL MAABELA\\OM\\ في 14-OCT , 09:12:40 " +
                "الرصيد المتوفر هو ر.ع 310.420"
        )
        assertEquals(2_750L, r.amountMinor)
        assertEquals(310_420L, r.balanceMinor)
    }

    @Test
    fun `the date after the shop is never taken for the shop`() {
        // "في 14-OCT" with its day cut off as a till number was a shop called "OCT".
        val r = parsed(
            "تم خصم 1.100 ر.ع من حسابك رقم GREEN MART\\\\SEEB\\0 في 15-OCT , 19:15:37 " +
                "الرصيد المتوفر هو 303.320 ر.ع"
        )
        assertEquals("GREEN MART", r.merchant)
    }

    @Test
    fun `a hash-masked account number reads as its last digits`() {
        // Sent as "005######00350" so that, laid out right to left, the phone shows
        // "00350######005". The token used to stop at the first "#" and read "0350".
        val r = parsed(withdrawal)
        assertEquals("005", r.accountTail)
        assertEquals(6_000L, r.amountMinor)
        assertEquals(304_420L, r.balanceMinor)
    }

    @Test
    fun `a spaced number reads in the order the phone shows it`() {
        // Sent as "005 ###### 00350" so that, right to left, the phone shows
        // "00350 ###### 005". Read in the order it was sent, the account was "0350".
        val r = parsed(
            "تم سحب 6.000 ر.ع من حساب رقم 005 ###### 00350 الى SALI####AHMD. " +
                "الرصيد المتوفر هو 304.420 ر.ع"
        )
        assertEquals("005", r.accountTail)
        assertEquals("00350", r.accountHead)
        assertEquals("1234", parsed("Withdrawal of RO 1.000 from a/c XXXX 1234 at X").accountTail)
    }

    @Test
    fun `invisible marks and full-width masks still read the end, and a hidden end names nothing`() {
        listOf("00350‎ ###### ‎005", "⁦00350######005⁩", "005＃＃＃＃00350").forEach { number ->
            val r = parsed("تم سحب 6.000 ر.ع من حساب رقم $number الى SALI####AHMD. الرصيد المتوفر هو 304.420 ر.ع")
            assertEquals(number, "005", r.accountTail)
        }
        assertNull(parsed("تم سحب 6.000 ر.ع من حساب رقم 00350###### الى SALI. الرصيد المتوفر هو 304.420 ر.ع").accountTail)
        assertNull(parsed("تم سحب 6.000 ر.ع من حساب رقم 00350 ###### الى SALI. الرصيد المتوفر هو 304.420 ر.ع").accountTail)
    }

    @Test
    fun `a refund that repeats the branch where the last digits go names no account`() {
        // The refund writes "00350 ###### 00350": read as an account, every refund was
        // "0350", and the refunds kept that account alive beside the real one.
        listOf("00350 ###### 00350", "00350######00350").forEach { number ->
            val r = parser.parseAs(
                "تم إسترجاع 2.600 ر.ع في حساب رقم $number من ANAS####SAIF. الرصيد المتوفر هو 542.006 ر.ع",
                Direction.INCOME,
            )!!
            assertNull(number, r.accountTail)
            assertEquals(542_006L, r.balanceMinor)
        }
    }

    @Test
    fun `wider or unbroken spacing around the mask still reads the end`() {
        listOf("005  ###### 00350", "005 ###### 00350", "005 ### ### 00350", "005######00350").forEach { number ->
            val r = parsed("تم سحب 6.000 ر.ع من حساب رقم $number الى SALI####AHMD. الرصيد المتوفر هو 304.420 ر.ع")
            assertEquals(number, "005", r.accountTail)
            assertEquals(number, "00350", r.accountHead)
        }
    }

    @Test
    fun `of two accounts in one message, the one written as yours is the one it is about`() {
        val r = parsed(
            "تم خصم 10.000 ر.ع للتحويل إلى حساب رقم 0123456784410 من حسابك رقم 005######00350 " +
                "الرصيد المتوفر هو 294.420 ر.ع"
        )
        assertEquals("005", r.accountTail)
    }

    @Test
    fun `a masked name is one template whatever the length of its mask`() {
        val a = "تم سحب 6.000 ر.ع من حساب رقم 00350######005 الى SALI####AHMD. الرصيد المتوفر هو 304.420 ر.ع"
        val b = "تم سحب 2.000 ر.ع من حساب رقم 00350######005 الى MOHA######KHALID. الرصيد المتوفر هو 302.420 ر.ع"
        assertEquals(Skeleton.of("BANK NIZWA", a), Skeleton.of("BANK NIZWA", b))
    }

    @Test
    fun `purchases at different shops are one template`() {
        val other = "تم خصم 0.900 ر.ع من حسابك رقم GREEN MART\\\\SEEB\\0 في 15-OCT , 19:15:37 " +
            "الرصيد المتوفر هو 303.320 ر.ع"
        // No terminal number at all: still the same kind of message.
        val bare = "تم خصم 0.700 ر.ع من حسابك رقم HOME STORE - FOOD SALES\\\\AL KHUWAIR في 15-OCT , 20:03:11 " +
            "الرصيد المتوفر هو 302.620 ر.ع"
        assertEquals(Skeleton.of("BANK NIZWA", purchase), Skeleton.of("BANK NIZWA", other))
        assertEquals(Skeleton.of("BANK NIZWA", purchase), Skeleton.of("BANK NIZWA", bare))
    }
}
