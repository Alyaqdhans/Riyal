package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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
    /**
     * Digits roll to a new value - up when it grows, down when it shrinks - rather than
     * being swapped. For the few headline figures that change in front of the user, as
     * a period is picked or a scan lands; a list row keeps still.
     */
    roll: Boolean = false,
) {
    val prefix = when (sign) {
        MoneySign.NONE -> ""
        MoneySign.NEGATIVE -> if (minor < 0) MINUS.toString() else ""
        MoneySign.OUT -> MINUS.toString()
        MoneySign.IN -> "+"
    }
    val number = Money.formatAmount(kotlin.math.abs(minor), currency)
    if (roll) {
        val tint = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
        RollingAmount(minor, prefix, number, currency, modifier, style, tint, textAlign, shrink = autoSize != null)
        return
    }
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
 * [MoneyText] with rolling digits, laid out as [appendAmount] writes an amount: the figure
 * left to right in either language, a sign that follows it outside that run so it lands
 * on the side each script puts it, a sign that leads it inside. Digits are paired from
 * the right, so units roll into units however many digits the new value has, and set in
 * tabular figures so nothing beside them shifts as they turn.
 */
@Composable
private fun RollingAmount(
    minor: Long,
    prefix: String,
    number: String,
    code: String,
    modifier: Modifier,
    style: TextStyle,
    tint: Color,
    textAlign: TextAlign?,
    shrink: Boolean,
) {
    val info = Currencies.of(code)
    // Which way this change goes, settled once per value.
    val last = remember { longArrayOf(minor) }
    val rising = remember(minor) { (minor >= last[0]).also { last[0] = minor } }
    val figures = style.copy(color = tint, fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val align = when (textAlign) {
        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
        TextAlign.Center -> Alignment.Center
        else -> Alignment.CenterStart
    }
    BoxWithConstraints(
        modifier.clearAndSetSemantics { contentDescription = "$prefix$number $code" },
        contentAlignment = align,
    ) {
        // Too wide for its room: smaller, as autoSize would have made the text.
        val size = figures.fontSize
        val fitted = if (shrink && size.isSp && constraints.hasBoundedWidth) {
            val signWidth = with(density) { (size * SIGN_EM * (info?.symbolAspect ?: 1f) * 1.3f).toPx() }
            val wide = measurer.measure(prefix + number, figures).size.width + signWidth
            val scale = (constraints.maxWidth / wide).coerceIn(0.5f, 1f)
            figures.copy(fontSize = size * scale)
        } else {
            figures
        }
        val signHeight = with(density) { (fitted.fontSize * SIGN_EM).toDp() }

        @Composable
        fun Sign() = when {
            info == null -> Text("$code ", style = fitted, maxLines = 1, softWrap = false)
            info.symbol != null -> CurrencySign(code, height = signHeight, tint = tint)
            else -> Text(
                info.glyph + if (info.glyph.last().isLetter()) " " else "",
                style = fitted,
                maxLines = 1,
                softWrap = false,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prefix.isNotEmpty()) Text(prefix, style = fitted, maxLines = 1, softWrap = false)
                    if (info == null || !info.symbolAfter) Sign()
                    number.forEachIndexed { i, c ->
                        key(number.length - i) { RollingChar(c, rising, fitted) }
                    }
                }
            }
            if (info != null && info.symbolAfter) {
                Spacer(Modifier.width(with(density) { (fitted.fontSize * 0.2f).toDp() }))
                Sign()
            }
        }
    }
}

@Composable
private fun RollingChar(c: Char, rising: Boolean, style: TextStyle) {
    AnimatedContent(
        targetState = c,
        transitionSpec = {
            val travel = tween<IntOffset>(durationMillis = 360, easing = FastOutSlowInEasing)
            val fade = tween<Float>(durationMillis = 220)
            (slideInVertically(travel) { h -> if (rising) h else -h } + fadeIn(fade)) togetherWith
                (slideOutVertically(travel) { h -> if (rising) -h else h } + fadeOut(fade)) using
                SizeTransform(clip = true)
        },
        modifier = Modifier.clipToBounds(),
        label = "digit",
    ) { shown ->
        Text(shown.toString(), style = style, maxLines = 1, softWrap = false)
    }
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
