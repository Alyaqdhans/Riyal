package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.MessageKind
import com.alyaqdhan.riyal.data.Skeleton
import com.alyaqdhan.riyal.data.TemplateLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shop after "at" is a name whatever its case, so teaching one card purchase teaches
 * them all. Made-up messages in Bank Muscat's card format.
 */
class ShopTemplateTest {

    private fun card(amount: String, shop: String, balance: String) =
        "Card of a/c 0123XXXXXXXX0045 used for OMR $amount at $shop on 05 MAY 2026 10:15. Avl Bal OMR $balance."

    private val game = card("1.250", "GOOGLE *Some Game Studio", "20.500")
    private val video = card("0.990", "GOOGLE *VIDEO PREM g.", "19.510")
    private val grocer = card("3.000", "LULU HYPERMARKET", "16.510")
    private val delivery = card("2.100", "Talabat Oman LLC", "14.410")

    @Test
    fun `purchases at shops written in any case are one kind of message`() {
        val keys = listOf(game, video, grocer, delivery).map { Skeleton.of("bank muscat", it) }
        assertEquals(1, keys.distinct().size)
    }

    @Test
    fun `a withdrawal at an ATM stays a kind of its own`() {
        val atm = card("20.000", "ATM SEEB", "0.500")
        assertNotEquals(Skeleton.of("bank muscat", grocer), Skeleton.of("bank muscat", atm))
    }

    @Test
    fun `a message in capitals sets its shop aside too`() {
        val a = Skeleton.of("NBO", "PURCHASE OF OMR 5.000 AT LULU HYPERMARKET ON 05MAY26 FROM A/C XX0045")
        val b = Skeleton.of("NBO", "PURCHASE OF OMR 7.250 AT CITY CENTRE FOOD COURT ON 06MAY26 FROM A/C XX0045")
        assertEquals(a, b)
    }

    @Test
    fun `only a name after at is set aside`() {
        // "at" before a time, or before the template's own words, names no shop.
        assertTrue(Skeleton.of("NBO", "Your balance at 10:15 is OMR 5.000").contains("balance at #"))
        assertTrue(Skeleton.of("NBO", "Your new card is ready at your branch").contains("at your branch"))
    }

    @Test
    fun `the old fingerprint kept the shop's words`() {
        assertTrue(Skeleton.legacyOf("bank muscat", game).contains("some game studio"))
        assertNotEquals(Skeleton.legacyOf("bank muscat", game), Skeleton.legacyOf("bank muscat", delivery))
    }

    @Test
    fun `what was taught about one shop carries over to every shop`() {
        val taught = mapOf(Skeleton.legacyOf("bank muscat", game) to TemplateLabel(MessageKind.OUT, 100L))
        val carried = Skeleton.carryOver(
            taught,
            sequenceOf("bank muscat" to game, "bank muscat" to delivery, "bank muscat" to ""),
        )
        assertEquals(mapOf(Skeleton.of("bank muscat", delivery) to TemplateLabel(MessageKind.OUT, 100L)), carried)
    }

    @Test
    fun `where two shops were taught differently the newer answer wins`() {
        val taught = mapOf(
            Skeleton.legacyOf("bank muscat", game) to TemplateLabel(MessageKind.NONE, 100L),
            Skeleton.legacyOf("bank muscat", delivery) to TemplateLabel(MessageKind.OUT, 200L),
        )
        val carried = Skeleton.carryOver(taught, sequenceOf("bank muscat" to game, "bank muscat" to delivery))
        assertEquals(TemplateLabel(MessageKind.OUT, 200L), carried[Skeleton.of("bank muscat", game)])
    }
}
