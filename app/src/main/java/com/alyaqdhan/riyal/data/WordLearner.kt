package com.alyaqdhan.riyal.data

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * Which way money went, learned from this phone's own messages rather than a word list.
 *
 * The keyword list is fixed in the app, and every bank and wallet words its alerts its own
 * way: "was charged", "تم إضافة مبلغ". A message none of its words cover went to Review,
 * one question per kind of message, and the answer taught nothing about the next sender
 * to write it the same way. Yet the phone already knows a great deal: every kind of
 * message whose balances proved its direction, and every one the user answered, says
 * which words go with money going out and which with money coming in.
 *
 * So the words of the settled kinds are counted, each way, and a kind nothing settles is
 * read from its words: naive Bayes over the words of its template ([Skeleton]) and the
 * pairs of words side by side, with the user's answers counting more than a proof. It
 * decides only when it is sure (nine in ten or better) and one word on its own points
 * firmly the same way, and only for a message the keywords left without a direction; a
 * label, a balance or a proven kind always come first. Anything less sure is still asked,
 * now with the learner's reading as the guess to confirm.
 *
 * Nothing is stored: it is rebuilt from the templates and the labels every scan.
 */
class WordLearner private constructor(
    private val outWeight: Map<String, Float>,
    private val inWeight: Map<String, Float>,
    private val outTotal: Float,
    private val inTotal: Float,
) {

    /** A reading from words: which way, how sure, and the word that weighed most. */
    data class Guess(val direction: Direction, val probability: Float, val word: String)

    /** How many words it learned something about. */
    val size: Int get() = (outWeight.keys + inWeight.keys).size

    /** The reading of the kind of message [key] is, or null when the words do not settle it. */
    fun guess(key: String): Guess? {
        // With only one way ever seen, every word seems to point that way.
        if (outTotal < MIN_SIDE || inTotal < MIN_SIDE) return null
        var score = 0.0
        var strongest: String? = null
        var strongestWeight = 0.0
        for (w in wordsOf(key)) {
            val o = outWeight[w] ?: 0f
            val i = inWeight[w] ?: 0f
            if (o + i < MIN_SUPPORT) continue
            val weight = ln(((o + SMOOTH) / (outTotal + 2 * SMOOTH)).toDouble()) -
                ln(((i + SMOOTH) / (inTotal + 2 * SMOOTH)).toDouble())
            score += weight
            if (abs(weight) > abs(strongestWeight)) {
                strongest = w
                strongestWeight = weight
            }
        }
        val word = strongest ?: return null
        val out = score > 0
        // The firmest word must point the way the whole does, firmly, on its own.
        if ((strongestWeight > 0) != out || abs(strongestWeight) < ln(FIRM)) return null
        val p = 1.0 / (1.0 + exp(-abs(score)))
        if (p < SURE) return null
        return Guess(if (out) Direction.EXPENSE else Direction.INCOME, p.toFloat(), word)
    }

    companion object {
        /** Each side needs this much evidence before either is believed. */
        const val MIN_SIDE = 2f

        /** A word seen fewer times than this says nothing yet. */
        const val MIN_SUPPORT = 2f

        /** The odds one word on its own must give: three to one. */
        const val FIRM = 3.0

        /** How sure the whole reading must be to decide. */
        const val SURE = 0.9

        /** What one answer by the user weighs, against one proof by a balance. */
        const val ANSWER_WEIGHT = 3f

        /** The most a kind's proofs weigh, however many it has: one kind is one wording. */
        const val MOST_PROOFS = 3f

        private const val SMOOTH = 0.5f

        private val masks = setOf("¤", "#", "@")

        /**
         * The words of a template key ("sender|your card was charged ¤ ..."): its own
         * words, not the sender's name or what was masked, and each pair of them side by
         * side, which is where "was charged" differs from "was credited".
         */
        fun wordsOf(key: String): Set<String> {
            val tokens = key.substringAfter('|').split(' ').filter { it.isNotEmpty() }
            val out = HashSet<String>()
            var previous: String? = null
            for (t in tokens) {
                val word = t.takeIf { it !in masks && it.length >= 2 && it.any(Char::isLetter) }
                if (word != null) {
                    out += word
                    previous?.let { out += "$it $word" }
                }
                previous = word
            }
            return out
        }

        /**
         * Learns from the kinds of message already settled: [templates] whose balances
         * proved them one way only, and the kinds the user taught ([labels]), which win
         * over a proof of the same kind.
         */
        fun train(templates: Collection<TemplateStat>, labels: Map<String, TemplateLabel>): WordLearner {
            val outWeight = HashMap<String, Float>()
            val inWeight = HashMap<String, Float>()
            var outTotal = 0f
            var inTotal = 0f
            fun learn(key: String, out: Boolean, weight: Float) {
                val into = if (out) outWeight else inWeight
                for (w in wordsOf(key)) into[w] = (into[w] ?: 0f) + weight
                if (out) outTotal += weight else inTotal += weight
            }
            for ((key, label) in labels) {
                when (label.kind) {
                    MessageKind.OUT -> learn(key, true, ANSWER_WEIGHT)
                    MessageKind.IN -> learn(key, false, ANSWER_WEIGHT)
                    else -> {}
                }
            }
            for (t in templates) {
                if (t.key in labels) continue
                when {
                    t.provenOut > 0 && t.provenIn == 0 -> learn(t.key, true, min(t.provenOut.toFloat(), MOST_PROOFS))
                    t.provenIn > 0 && t.provenOut == 0 -> learn(t.key, false, min(t.provenIn.toFloat(), MOST_PROOFS))
                }
            }
            return WordLearner(outWeight, inWeight, outTotal, inTotal)
        }

        /** Knows nothing, and so never decides. */
        val EMPTY = WordLearner(emptyMap(), emptyMap(), 0f, 0f)
    }
}
