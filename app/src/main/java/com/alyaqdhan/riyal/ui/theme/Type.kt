package com.alyaqdhan.riyal.ui.theme

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.alyaqdhan.riyal.R
import java.nio.ByteBuffer
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle

/**
 * How much heavier Arabic is drawn than the weight a style asks for: Regular text is set
 * in Medium, Medium in SemiBold.
 */
private const val ARABIC_HEAVIER = 100

/**
 * The type scale in the app's own faces: Noto Sans for Latin, Noto Sans Arabic UI for
 * Arabic, the pair drawn to sit together, in either language.
 *
 * The phone's own fonts were the default before: Roboto, or whatever font the phone is set
 * to, and for Arabic Noto Naskh on most phones, a calligraphic hand whose thin strokes and
 * small letters read faint beside the Latin at the same size. Noto Sans Arabic UI has even
 * strokes and UI line heights, and is drawn a step heavier ([ARABIC_HEAVIER]) than the
 * Latin at each weight.
 *
 * The files in `res/font` (OFL, the licence in each name table) are the upstream variable
 * fonts with their width axis pinned, each cut to its own script: the Arabic file has no
 * Latin letters or digits, so the figures in an Arabic line are Noto Sans's. Whatever
 * neither holds - emoji, Cyrillic - comes from the phone's sans-serif.
 */
val RiyalTypography = Typography(
    fontFamily = FontFamily((100..900 step 100).map { RiyalFont(FontWeight(it)) }),
)

/**
 * The same faces as a platform typeface at [weight], for text an Android view draws (the
 * release notes).
 */
fun riyalTypeface(context: Context, weight: Int = 400): Typeface =
    Typeface.create(RiyalLoader.base(context), weight, false)

/**
 * One weight, asked of the one typeface that holds both files ([RiyalLoader.base]). A
 * resource font would not do: Compose loads one at the file's default weight and varies
 * it through the paint, which varies every font the line falls back to as well.
 */
private class RiyalFont(override val weight: FontWeight) :
    AndroidFont(FontLoadingStrategy.Blocking, RiyalLoader, FontVariation.Settings()) {
    override val style: FontStyle = FontStyle.Normal
    override fun toString() = "RiyalFont(${weight.weight})"
}

private object RiyalLoader : AndroidFont.TypefaceLoader {

    private var base: Typeface? = null

    /**
     * Noto Sans, then Noto Sans Arabic UI, then the phone's sans-serif, each at every
     * weight from 100 to 900, so a weight asked of it picks the same weight from each.
     */
    @Synchronized
    fun base(context: Context): Typeface = base ?: run {
        val res = context.resources
        Typeface.CustomFallbackBuilder(family(res, R.font.noto_sans, heavier = 0))
            .addCustomFallback(family(res, R.font.noto_sans_arabic_ui, heavier = ARABIC_HEAVIER))
            .setSystemFallback("sans-serif")
            .setStyle(PlatformFontStyle())
            .build()
    }.also { base = it }

    /**
     * Every weight of one variable file, each drawn [heavier] than it is named. The file is
     * read once and shared by its weights, as Android shares its own fonts'.
     */
    private fun family(res: Resources, @FontRes id: Int, heavier: Int): PlatformFontFamily {
        val bytes = res.openRawResource(id).use { it.readBytes() }
        val file = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { rewind() }
        val fonts = (100..900 step 100).map { weight ->
            PlatformFont.Builder(file)
                .setWeight(weight)
                .setFontVariationSettings("'wght' ${(weight + heavier).coerceAtMost(900)}")
                .build()
        }
        return PlatformFontFamily.Builder(fonts.first()).apply { fonts.drop(1).forEach(::addFont) }.build()
    }

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        Typeface.create(base(context), font.weight.weight, false)

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loadBlocking(context, font)
}
