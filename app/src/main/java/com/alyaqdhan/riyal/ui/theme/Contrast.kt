package com.alyaqdhan.riyal.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * WCAG contrast, in plain arithmetic so it can be tested off the device.
 *
 * Colours here are 0xAARRGGBB ints, opaque. Text needs 4.5:1 against what it sits on;
 * icons, bars and other graphics that carry meaning need 3:1.
 */

/** The contrast text needs against its background. */
const val TEXT_CONTRAST = 4.5

/**
 * The contrast category colours are held to against the theme's cards: 3:1 is the floor
 * for graphics, and the extra half keeps a thin bar or a small chip icon clear of it on
 * the lighter surfaces too.
 */
const val GRAPHIC_CONTRAST = 3.5

/** Relative luminance, 0 for black to 1 for white. */
fun luminance(argb: Int): Double {
    fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel((argb shr 16) and 0xFF) +
        0.7152 * channel((argb shr 8) and 0xFF) +
        0.0722 * channel(argb and 0xFF)
}

/** The WCAG contrast ratio of two opaque colours, 1 to 21. */
fun contrast(a: Int, b: Int): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/**
 * [color] with its lightness moved - its hue and saturation kept - just far enough to
 * stand [ratio]:1 against [background]: darker on a light background, lighter on a dark
 * one. A colour that already stands out is returned as it is, so the palette keeps its
 * own look wherever it can.
 */
fun legibleOn(color: Int, background: Int, ratio: Double): Int {
    if (contrast(color, background) >= ratio) return color
    val hsl = toHsl(color)
    val darken = luminance(background) > 0.5
    var shifted = color
    for (step in 1..100) {
        hsl[2] = (hsl[2] + if (darken) -0.01f else 0.01f).coerceIn(0f, 1f)
        shifted = fromHsl(hsl, alpha = color ushr 24)
        if (contrast(shifted, background) >= ratio) return shifted
    }
    return shifted
}

/** [color] laid at [alpha] over the opaque [background]. */
fun blend(color: Int, alpha: Float, background: Int): Int {
    fun mix(shift: Int) = kotlin.math.round(
        ((color shr shift) and 0xFF) * alpha + ((background shr shift) and 0xFF) * (1 - alpha)
    ).toInt()
    return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

/**
 * A category badge on [card]: its circle, [color] faintly over the card, and the icon
 * colour that reads on that circle. The circle is a shade off the card, so the icon is
 * fitted to the circle itself rather than to the card.
 */
fun badgeColours(color: Int, card: Int, alpha: Float = BADGE_TINT): Pair<Int, Int> {
    val shown = legibleOn(color, card, GRAPHIC_CONTRAST)
    val circle = blend(shown, alpha, card)
    return circle to legibleOn(shown, circle, 3.0)
}

/** How strongly a badge's circle is tinted with its category's colour. */
const val BADGE_TINT = 0.18f

/** Black or white, whichever reads better on [fill]: for an icon or tick drawn on it. */
fun inkOn(fill: Int): Int {
    val white = 0xFFFFFFFF.toInt()
    val black = 0xFF000000.toInt()
    return if (contrast(white, fill) >= contrast(black, fill)) white else black
}

private fun toHsl(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val l = (hi + lo) / 2f
    val d = hi - lo
    if (d == 0f) return floatArrayOf(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (hi) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
}

private fun fromHsl(hsl: FloatArray, alpha: Int): Int {
    val (h, s, l) = Triple(hsl[0], hsl[1], hsl[2])
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun byte(v: Float) = ((v + m) * 255f).let { kotlin.math.round(it) }.toInt().coerceIn(0, 255)
    return (alpha shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
}
