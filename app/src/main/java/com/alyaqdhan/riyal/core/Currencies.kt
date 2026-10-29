package com.alyaqdhan.riyal.core

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.alyaqdhan.riyal.R
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * One currency the app knows how to name, flag and convert.
 *
 * [omrPerUnit] is the whole of the exchange model: how many Omani rials one unit is
 * worth. Every conversion goes through the rial, so converting between two foreign
 * currencies is two multiplications and never needs a table of pairs.
 */
data class CurrencyInfo(
    val code: String,
    /** ISO 3166 region whose flag stands for the currency ("EU" for the euro). */
    val region: String,
    @param:StringRes val name: Int,
    val omrPerUnit: BigDecimal,
    /**
     * A rate fixed by a central bank rather than a market. The rial has been pegged to
     * the dollar at 0.3845 since 1986, and most of the Gulf is pegged to the dollar too,
     * so for these the conversion is exact and is not the user's to edit.
     */
    val pegged: Boolean,
    /**
     * The currency's own sign as a vector, where it has one: the new rial, riyal and
     * dirham signs, the euro, the pound. Drawn in place of the code next to an amount.
     */
    @param:DrawableRes val symbol: Int? = null,
    /** Width over height of [symbol], so it can be set into a line of text at its own shape. */
    val symbolAspect: Float = 1f,
    /** What stands in for the sign when there is no vector for it: "$", "KD". */
    val glyph: String = code,
    /** Whether the sign follows the figure, as the Gulf signs do, or leads it ("$9.99"). */
    val symbolAfter: Boolean = false,
) {
    /** The region's flag as an emoji: two regional-indicator letters, drawn by the system font. */
    val flag: String
        get() = buildString {
            region.uppercase().forEach { c -> appendCodePoint(REGIONAL_A + (c - 'A')) }
        }

    private companion object {
        const val REGIONAL_A = 0x1F1E6
    }
}

/**
 * The currencies the pickers offer, and what each is worth in rials.
 *
 * Purchases from abroad - a Steam game, an app store, a hotel - arrive in a message
 * that names no rial at all, only "USD 9.99". Leaving them in dollars kept them out of
 * every total, so the month looked cheaper than it was. They are converted into the
 * account's own currency instead, at the rates below, and the original figure is kept
 * on the record so nothing about what the bank said is lost.
 */
object Currencies {

    /** The rial's own peg: one US dollar is 0.3845 rials. */
    private val USD_PEG = BigDecimal("0.3845")

    private fun perDollar(unitsPerUsd: String): BigDecimal =
        USD_PEG.divide(BigDecimal(unitsPerUsd), MathContext.DECIMAL64)

    private fun info(
        code: String,
        region: String,
        @StringRes name: Int,
        omr: BigDecimal,
        pegged: Boolean,
        glyph: String,
        after: Boolean,
        @DrawableRes symbol: Int? = null,
        aspect: Float = 1f,
    ) = CurrencyInfo(code, region, name, omr, pegged, symbol, aspect, glyph, after)

    /**
     * Display order: the rial, then the Gulf and the dollar it is pegged to, then the
     * currencies people in Oman most often send money home in or shop online with.
     *
     * The floating rates are reference values for when a message gives no rial figure,
     * set when this list was written. They drift, which is why each can be overridden in
     * Settings - see [setOverrides].
     */
    val BUILT_IN: List<CurrencyInfo> = listOf(
        info("OMR", "OM", R.string.cur_omr, BigDecimal.ONE, true, "ر.ع.", true, R.drawable.ic_cur_omr, 1.7862f),
        info("USD", "US", R.string.cur_usd, USD_PEG, true, "$", false),
        info("AED", "AE", R.string.cur_aed, perDollar("3.6725"), true, "د.إ", true, R.drawable.ic_cur_aed, 1.1494f),
        info("SAR", "SA", R.string.cur_sar, perDollar("3.75"), true, "ر.س", true, R.drawable.ic_cur_sar, 0.8947f),
        info("QAR", "QA", R.string.cur_qar, perDollar("3.64"), true, "QR", true),
        info("BHD", "BH", R.string.cur_bhd, perDollar("0.376"), true, "BD", true),
        info("JOD", "JO", R.string.cur_jod, perDollar("0.709"), true, "JD", true),
        info("KWD", "KW", R.string.cur_kwd, perDollar("0.3070"), false, "KD", true),
        info("EUR", "EU", R.string.cur_eur, perDollar("0.8600"), false, "€", false, R.drawable.ic_cur_eur, 0.75f),
        info("GBP", "GB", R.string.cur_gbp, perDollar("0.7450"), false, "£", false, R.drawable.ic_cur_gbp, 0.6506f),
        info("INR", "IN", R.string.cur_inr, perDollar("88.00"), false, "₹", false, R.drawable.ic_cur_inr, 0.68f),
        info("PKR", "PK", R.string.cur_pkr, perDollar("281.0"), false, "Rs", false),
        info("BDT", "BD", R.string.cur_bdt, perDollar("122.0"), false, "৳", false),
        info("PHP", "PH", R.string.cur_php, perDollar("58.00"), false, "₱", false),
        info("EGP", "EG", R.string.cur_egp, perDollar("48.50"), false, "E£", false),
        info("LKR", "LK", R.string.cur_lkr, perDollar("300.0"), false, "Rs", false),
        info("NPR", "NP", R.string.cur_npr, perDollar("141.0"), false, "Rs", false),
        info("TRY", "TR", R.string.cur_try, perDollar("41.50"), false, "₺", false, R.drawable.ic_cur_try, 0.7478f),
        info("CNY", "CN", R.string.cur_cny, perDollar("7.150"), false, "¥", false, R.drawable.ic_cur_yen, 0.7103f),
    )

    private val byCode: Map<String, CurrencyInfo> = BUILT_IN.associateBy { it.code }

    /** User-set rates for floating currencies, OMR per unit. Pegged ones are never overridden. */
    @Volatile
    private var overrides: Map<String, BigDecimal> = emptyMap()

    /** Called at start-up and whenever the user edits a rate. */
    fun setOverrides(rates: Map<String, BigDecimal>) {
        overrides = rates.filter { (code, rate) ->
            rate.signum() > 0 && byCode[code]?.pegged == false
        }
    }

    val ALL: List<CurrencyInfo> get() = BUILT_IN.map { c -> overrides[c.code]?.let { c.copy(omrPerUnit = it) } ?: c }

    fun of(code: String): CurrencyInfo? {
        val c = byCode[code.uppercase()] ?: return null
        return overrides[c.code]?.let { c.copy(omrPerUnit = it) } ?: c
    }

    /** The flag for a code, or nothing for one the app does not know. */
    fun flagOf(code: String): String = of(code)?.flag.orEmpty()

    fun isOverridden(code: String): Boolean = code.uppercase() in overrides

    /** The rates in force, as one stable string: part of what a scan's records depend on. */
    fun fingerprint(): String =
        overrides.toSortedMap().entries.joinToString(",") { (code, rate) -> code + "=" + rate.toPlainString() }

    /**
     * [minor] units of [from] expressed in [to], rounded half-up to [to]'s own minor
     * unit. Null when either currency is unknown: an amount the app cannot convert is
     * left as it was rather than given an invented rate.
     */
    fun convert(minor: Long, from: String, to: String): Long? {
        if (from.equals(to, ignoreCase = true)) return minor
        val a = of(from) ?: return null
        val b = of(to) ?: return null
        val major = Money.toMajor(minor, a.code)
        val inOmr = major.multiply(a.omrPerUnit, MathContext.DECIMAL64)
        val inTarget = inOmr.divide(b.omrPerUnit, MathContext.DECIMAL64)
        return Money.toMinor(inTarget.setScale(Money.decimalsFor(b.code), RoundingMode.HALF_UP), b.code)
    }

    /** How many units of [to] one unit of [from] buys, for saying the rate out loud. */
    fun rate(from: String, to: String): BigDecimal? {
        val a = of(from) ?: return null
        val b = of(to) ?: return null
        return a.omrPerUnit.divide(b.omrPerUnit, MathContext.DECIMAL64)
    }

    /** "CODE=rate" lines, the shape the overrides are stored in. */
    fun encode(rates: Map<String, BigDecimal>): Set<String> =
        rates.mapTo(HashSet()) { (code, rate) -> code + "=" + rate.toPlainString() }

    fun decode(lines: Set<String>): Map<String, BigDecimal> = buildMap {
        for (line in lines) {
            val code = line.substringBefore('=', "").trim().uppercase()
            val rate = line.substringAfter('=', "").trim().toBigDecimalOrNull() ?: continue
            if (code.isNotEmpty() && rate.signum() > 0) put(code, rate)
        }
    }
}
