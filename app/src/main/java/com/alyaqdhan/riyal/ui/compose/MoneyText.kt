package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.alyaqdhan.riyal.core.Currencies
import com.alyaqdhan.riyal.core.Money

/**
 * Which sign an amount carries in front of it.
 *
 * Money out and money in are said with a sign on every row; a balance or a net figure
 * only shows one when it is actually below zero.
 */
enum class MoneySign { NONE, NEGATIVE, OUT, IN }

/** Height of a currency sign set into a line, as a share of the font size. */
private const val SIGN_EM = 0.78f

private const val LRI = '⁦'
private const val PDI = '⁩'
private const val NARROW_SPACE = ' '
private const val MINUS = '−'

private fun inlineId(code: String) = "cur:$code"

/**
 * Appends one amount: the figure and its currency's sign.
 *
 * The figure and its plus or minus sit in a left-to-right isolate, so "−12.500" reads
 * the same way round in Arabic. A sign that follows the figure, as the Gulf signs do,
 * sits outside that isolate: in English it lands to the right of the number and in
 * Arabic to the left, which is where each script puts it. A leading sign ("$9.99") is
 * part of the figure in both.
 */
private fun AnnotatedString.Builder.appendAmount(prefix: String, number: String, code: String) {
    val info = Currencies.of(code)
    if (info == null) {
        append(LRI); append(prefix); append(code); append(' '); append(number); append(PDI)
        return
    }
    if (info.symbolAfter) {
        append(LRI); append(prefix); append(number); append(PDI)
        append(NARROW_SPACE)
        appendSign(code, info.symbol != null, info.glyph)
    } else {
        append(LRI); append(prefix)
        appendSign(code, info.symbol != null, info.glyph)
        // "$9.99" reads as one thing; "Rs1,200" does not.
        if (info.symbol == null && info.glyph.last().isLetter()) append(' ')
        append(number); append(PDI)
    }
}

private fun AnnotatedString.Builder.appendSign(code: String, hasVector: Boolean, glyph: String) {
    if (hasVector) appendInlineContent(inlineId(code), code) else append(glyph)
}

/** The vector signs a piece of text needs, sized to the text they sit in. */
private fun signContent(codes: Collection<String>, tint: Color): Map<String, InlineTextContent> =
    codes.mapNotNull { code ->
        val info = Currencies.of(code) ?: return@mapNotNull null
        val res = info.symbol ?: return@mapNotNull null
        inlineId(code) to InlineTextContent(
            Placeholder(
                width = (SIGN_EM * info.symbolAspect).em,
                height = SIGN_EM.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Icon(
                painterResource(res),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }.toMap()

/**
 * An amount as people expect to see money written: the figure with the currency's own
 * sign - the new rial sign for rials - rather than its three-letter code.
 */
@Composable
fun MoneyText(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    sign: MoneySign = MoneySign.NEGATIVE,
    textAlign: TextAlign? = null,
    autoSize: TextAutoSize? = null,
) {
    val prefix = when (sign) {
        MoneySign.NONE -> ""
        MoneySign.NEGATIVE -> if (minor < 0) MINUS.toString() else ""
        MoneySign.OUT -> MINUS.toString()
        MoneySign.IN -> "+"
    }
    val number = Money.formatAmount(kotlin.math.abs(minor), currency)
    val text = remember(prefix, number, currency) {
        buildAnnotatedString { appendAmount(prefix, number, currency) }
    }
    val tint = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    Text(
        text,
        modifier = modifier,
        color = tint,
        style = style,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        autoSize = autoSize,
        inlineContent = signContent(listOf(currency), tint),
    )
}

/**
 * "OMR 12.500", optionally isolated and signed, as [Money.format] and the screens'
 * sentences write it. Matched so a whole sentence can have its codes turned into signs.
 */
private val WRITTEN_AMOUNT = Regex(
    "$LRI?([$MINUS+\\-] ?)?([A-Z]{3}) (\\d[\\d,]*(?:\\.\\d+)?)$PDI?"
)

/** [text] with every written amount in it turned into a figure and a currency sign. */
internal fun withCurrencySigns(text: String): Pair<AnnotatedString, Set<String>> {
    val codes = HashSet<String>()
    val built = buildAnnotatedString {
        var last = 0
        for (m in WRITTEN_AMOUNT.findAll(text)) {
            val code = m.groupValues[2]
            if (Currencies.of(code) == null) continue
            // Only take the isolate marks when both ends of one were matched: half a
            // pair left behind would throw the rest of the line out of order.
            val opened = m.value.startsWith(LRI)
            val closed = m.value.endsWith(PDI)
            val start = if (opened && !closed) m.range.first + 1 else m.range.first
            val end = if (closed && !opened) m.range.last else m.range.last + 1
            append(text, last, start)
            appendAmount(m.groupValues[1].trim(), m.groupValues[3], code)
            codes += code
            last = end
        }
        append(text, last, text.length)
    }
    return built to codes
}

/**
 * A sentence that has amounts in it - "Over by OMR 4.500" - with each one written the
 * way [MoneyText] writes it. For text built from string resources around
 * [Money.format], so no sentence has to be taken apart by hand to put a sign in it.
 */
@Composable
fun MoneyAwareText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val (annotated, codes) = remember(text) { withCurrencySigns(text) }
    val tint = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    Text(
        annotated,
        modifier = modifier,
        color = tint,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = signContent(codes, tint),
    )
}

/**
 * A currency's sign on its own, for a field suffix or a picker row: the vector where
 * there is one, the written symbol where there is not.
 */
@Composable
fun CurrencySign(
    code: String,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    tint: Color = LocalContentColor.current,
    style: TextStyle = LocalTextStyle.current,
) {
    val info = Currencies.of(code)
    val res = info?.symbol
    if (info != null && res != null) {
        Icon(
            painterResource(res),
            contentDescription = code,
            tint = tint,
            modifier = modifier.size(width = height * info.symbolAspect, height = height),
        )
    } else {
        Text(info?.glyph ?: code, modifier = modifier, color = tint, style = style)
    }
}
