package com.alyaqdhan.riyal.ui.compose

import androidx.compose.ui.semantics.semantics
import androidx.annotation.DrawableRes
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
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
private fun signContent(codes: Collection<String>, tint: Color, style: TextStyle): Map<String, InlineTextContent> =
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
            SignGlyph(res, tint, style, Modifier.fillMaxSize())
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
        inlineContent = signContent(listOf(currency), tint, style),
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
            info.symbol != null -> CurrencySign(code, height = signHeight, tint = tint, style = fitted)
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
                    // Keyed on the replay count: a finished scan starts the roll over.
                    key(Rolls.generation) { RollingDigits(minor, number, Money.formatAmount(0, code), fitted) }
                }
            }
            if (info != null && info.symbolAfter) {
                Spacer(Modifier.width(with(density) { (fitted.fontSize * 0.2f).toDp() }))
                Sign()
            }
        }
    }
}

/** NumberFlow's own timing: 900 ms, easing out hard so a spin settles rather than stops. */
internal const val SPIN_MS = 900
internal val spinEasing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

/**
 * The digits of [number], rolling to it the way firePOS's NumberFlow (with its
 * `continuous` plugin) does.
 *
 * A figure rolls in from zero ([zero], "0.000") only when the app starts and when a scan
 * or refresh lands ([Rolls]); one that comes into view any other time - a tab switch, a
 * scroll - is simply there. From then on it rolls to each new value it is given. Places are
 * paired from the right, so the decimals stay put and a figure that gains digits grows
 * leftwards, the new places opening out of the decimal point's side; one that loses them
 * closes the same way. Each digit is a wheel that turns through the digits between, up
 * when the figure grows and down when it shrinks, and a place whose higher places changed
 * goes once more round, as an odometer's would.
 */
@Composable
private fun RollingDigits(minor: Long, number: String, zero: String, style: TextStyle) {
    val fromZero = remember { Rolls.armed() }
    val start = if (fromZero) zero else number
    var shown by remember { mutableStateOf(start) }
    var before by remember { mutableStateOf(start) }
    var rising by remember { mutableStateOf(true) }
    var was by remember { mutableLongStateOf(if (fromZero) 0L else minor) }
    var turn by remember { mutableIntStateOf(0) }
    var widest by remember { mutableIntStateOf(start.length) }
    // Places that arrive after the first frame - a figure gaining digits - always open.
    var settled by remember { mutableStateOf(false) }
    SideEffect { settled = true }
    LaunchedEffect(number, minor) {
        if (number == shown) return@LaunchedEffect
        rising = kotlin.math.abs(minor) >= kotlin.math.abs(was)
        was = minor
        before = shown
        shown = number
        widest = maxOf(widest, number.length)
        turn++
    }
    // The ten digits are laid out once, and the wheels only draw them: a roll redraws,
    // and never recomposes or measures text, frame after frame.
    val measurer = rememberTextMeasurer()
    val digits = remember(style) { List(10) { measurer.measure(it.toString(), style) } }
    val width = with(LocalDensity.current) { digits[0].size.width.toDp() }
    val height = with(LocalDensity.current) { digits[0].size.height.toDp() }
    Row {
        for (place in widest - 1 downTo 0) {
            key(place) {
                RollingPlace(
                    c = shown.getOrNull(shown.length - 1 - place),
                    arrive = fromZero || settled,
                    turn = turn,
                    rising = rising,
                    lap = higherPlaces(before, place) != higherPlaces(shown, place),
                    style = style,
                    digits = digits,
                    width = width,
                    height = height,
                )
            }
        }
    }
}

/** The value of the places above [place], counted from the right, as written. */
private fun higherPlaces(s: String, place: Int): String =
    s.dropLast(place + 1).filter(Char::isDigit).trimStart('0')

/** One place: opens when the figure gains it, closes when it loses it. */
@Composable
private fun RollingPlace(
    c: Char?,
    arrive: Boolean,
    turn: Int,
    rising: Boolean,
    lap: Boolean,
    style: TextStyle,
    digits: List<TextLayoutResult>,
    width: Dp,
    height: Dp,
) {
    val held = remember { mutableStateOf(c ?: '0') }
    SideEffect { if (c != null) held.value = c }
    // A place that is simply there starts open and its wheel at its digit.
    val spinIn = remember { arrive }
    val visible = remember { MutableTransitionState(!spinIn && c != null) }
    visible.targetState = c != null
    AnimatedVisibility(
        visibleState = visible,
        enter = expandHorizontally(tween(SPIN_MS, easing = spinEasing), expandFrom = Alignment.End) +
            fadeIn(tween(SPIN_MS / 2)),
        exit = shrinkHorizontally(tween(SPIN_MS, easing = spinEasing), shrinkTowards = Alignment.End) +
            fadeOut(tween(SPIN_MS / 3)),
    ) {
        val ch = c ?: held.value
        if (ch.isDigit()) {
            DigitWheel(ch.digitToInt(), spinIn, turn, rising, lap, digits, width, height)
        } else {
            Text(ch.toString(), style = style, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * A wheel of 0-9 behind a one-digit window, turned to [digit]. Its position is read only
 * while drawing, so a turning wheel costs a redraw of its two visible digits per frame.
 */
@Composable
private fun DigitWheel(
    digit: Int,
    spinIn: Boolean,
    turn: Int,
    rising: Boolean,
    lap: Boolean,
    digits: List<TextLayoutResult>,
    width: Dp,
    height: Dp,
) {
    val wheel = remember { Animatable(if (spinIn) 0f else digit.toFloat()) }
    LaunchedEffect(turn) {
        val from = wheel.value
        val at = ((from % 10f) + 10f) % 10f
        var delta = if (rising) ((digit - at) % 10f + 10f) % 10f else -(((at - digit) % 10f + 10f) % 10f)
        if (lap) delta += if (rising) 10f else -10f
        if (delta != 0f) wheel.animateTo(from + delta, tween(SPIN_MS, easing = spinEasing))
        wheel.snapTo(digit.toFloat())
    }
    Spacer(
        Modifier
            .size(width, height)
            .clipToBounds()
            .drawBehind {
                val position = wheel.value
                val base = kotlin.math.floor(position)
                for (k in 0..1) {
                    val shown = (((base.toInt() + k) % 10) + 10) % 10
                    drawText(digits[shown], topLeft = Offset(0f, (base + k - position) * size.height))
                }
            },
    )
}

/**
 * How many times the tab holding this has been brought into view: 0 while it is first
 * built, then one more for every switch to it. What should play again whenever its tab
 * is shown (the category wheel filling) keys on it; the tabs stay built between switches.
 */
val LocalTabShows = compositionLocalOf { 0 }

/**
 * When figures roll in from zero: for a moment after the app starts, and after each scan
 * or refresh lands, when [replay] also rebuilds every rolling figure on screen. A figure
 * that appears outside those moments shows its value at once.
 */
object Rolls {
    /** How long after a start or a refresh a figure that appears still rolls in. */
    private const val WINDOW_MS = 1_500L

    var generation by mutableIntStateOf(0)
        private set

    private var armedUntil = android.os.SystemClock.uptimeMillis() + WINDOW_MS

    fun armed(): Boolean = android.os.SystemClock.uptimeMillis() < armedUntil

    fun replay() {
        armedUntil = android.os.SystemClock.uptimeMillis() + WINDOW_MS
        generation++
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
        inlineContent = signContent(codes, tint, style),
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
        SignGlyph(
            res,
            tint,
            style,
            modifier
                .size(width = height * info.symbolAspect, height = height)
                .semantics { contentDescription = code },
        )
    } else {
        Text(info?.glyph ?: code, modifier = modifier, color = tint, style = style)
    }
}
