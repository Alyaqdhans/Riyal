package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.MessageKind
import com.alyaqdhan.riyal.data.Skeleton
import com.alyaqdhan.riyal.data.TemplateInference
import com.alyaqdhan.riyal.data.TemplateLabel
import com.alyaqdhan.riyal.data.TemplateStat
import com.alyaqdhan.riyal.data.WordLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Words learned from the kinds of message already settled, read into new ones. Made-up
 * messages from made-up senders.
 */
class WordLearnerTest {

    private fun stat(sender: String, body: String, out: Int = 0, inn: Int = 0) = TemplateStat(
        key = Skeleton.of(sender, body),
        sender = sender,
        count = out + inn,
        sampleId = body.hashCode().toString(),
        sampleBody = body,
        sampleAt = 0L,
        guess = null,
        confidence = 0f,
        provenOut = out,
        provenIn = inn,
    )

    /** One bank's kinds of message, each proven by its balances. */
    private val settled = listOf(
        stat("ExampleBank", "Your card ending 1234 was charged OMR 5.000 at EXAMPLE CAFE. Avl Bal OMR 100.000", out = 3),
        stat("ExampleBank", "OMR 20.000 was debited from your a/c 0019. Avl Bal OMR 80.000", out = 2),
        stat("ExampleBank", "OMR 500.000 was credited to your a/c 0019. Avl Bal OMR 580.000", inn = 3),
        stat("ExampleBank", "Salary of OMR 800.000 has been credited to your a/c 0019", inn = 2),
    )

    private val learner = WordLearner.train(settled, emptyMap())

    @Test
    fun `a new sender's charge reads as money out from what another bank proved`() {
        val g = learner.guess(Skeleton.of("ExamplePay", "Dear Customer, your Example card XX1234 was charged OMR 2.000 for annual fees"))
        assertNotNull(g)
        assertEquals(Direction.EXPENSE, g!!.direction)
        assertTrue(g.probability >= WordLearner.SURE)
    }

    @Test
    fun `a new sender's credit reads as money in`() {
        val g = learner.guess(Skeleton.of("ExamplePay", "OMR 5.000 was credited to your Example card"))
        assertNotNull(g)
        assertEquals(Direction.INCOME, g!!.direction)
    }

    @Test
    fun `words it never learned decide nothing`() {
        assertNull(learner.guess(Skeleton.of("ExamplePay", "Thank you for choosing us. OMR 1.000 reference 4411")))
    }

    @Test
    fun `with only one way ever seen it does not guess`() {
        val oneWay = WordLearner.train(settled.filter { it.provenOut > 0 }, emptyMap())
        assertNull(oneWay.guess(Skeleton.of("ExamplePay", "your card was charged OMR 2.000")))
    }

    @Test
    fun `an answer counts, and over a proof of the same kind`() {
        val body = "Your card ending 1234 was charged OMR 5.000 at EXAMPLE CAFE. Avl Bal OMR 100.000"
        // The user said this kind is money coming in, whatever its balances said.
        val labels = mapOf(Skeleton.of("ExampleBank", body) to TemplateLabel(MessageKind.IN, 0L))
        val taught = WordLearner.train(settled, labels)
        val g = taught.guess(Skeleton.of("ExamplePay", "your card was charged OMR 2.000 at EXAMPLE SHOP"))
        // "charged" is now on the side of money in, so it no longer reads as money out.
        assertTrue(g == null || g.direction == Direction.INCOME)
    }

    @Test
    fun `the words of a key are its own words and their pairs, not its masks or sender`() {
        val words = WordLearner.wordsOf("examplebank|your card # was charged ¤ at @")
        assertTrue("was charged" in words)
        assertTrue("charged" in words)
        assertFalse("examplebank" in words)
        assertFalse("¤" in words)
        // Not paired across what was masked.
        assertFalse("card was" in words)
    }

    @Test
    fun `a template nothing settles takes the learned reading to be asked about`() {
        val body = "Dear Customer, your Example card XX1234 was charged OMR 2.000 for annual fees"
        val key = Skeleton.of("ExamplePay", body)
        val stats = TemplateInference.infer(
            listOf(
                TemplateInference.Observation(
                    key = key, sender = "ExamplePay", messageId = "m1", atMillis = 0L, body = body,
                    read = null, proven = null, selfTransfer = false, skipped = false, hasAmount = true,
                )
            ),
            learner::guess,
        )
        val t = stats.getValue(key)
        assertEquals(MessageKind.OUT, t.guess)
        // Surer than the blind guess it replaces, less than words of its own.
        assertTrue(t.confidence > 0.3f && t.confidence < 0.8f)
    }

    @Test
    fun `a short scan keeps the proofs a full one found`() {
        val old = settled[0].copy(provenOut = 10, count = 40)
        val fresh = settled[0].copy(provenOut = 1, count = 2, guess = MessageKind.OUT, confidence = 0.6f)
        val merged = TemplateInference.merge(old, fresh)
        assertEquals(10, merged.provenOut)
        assertEquals(40, merged.count)
        assertTrue(TemplateInference.isProven(merged))
    }

    @Test
    fun `one proof the other way unsettles a template`() {
        val old = settled[0].copy(provenOut = 10)
        val fresh = settled[0].copy(provenOut = 0, provenIn = 1)
        val merged = TemplateInference.merge(old, fresh)
        assertEquals(10, merged.provenOut)
        assertEquals(1, merged.provenIn)
        assertFalse(TemplateInference.isProven(merged))
        assertEquals(MessageKind.OUT, merged.guess)
    }
}
