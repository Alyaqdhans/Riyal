package com.alyaqdhan.riyal.ui.theme

import android.content.Context
import android.graphics.Typeface
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
private const val HEAVIER = 100

/**
 * The type scale in Arabic: every style's Arabic in Noto Sans Arabic UI, a step heavier.
 *
 * Android's own Arabic is Noto Naskh on most phones, a calligraphic hand whose thin
 * strokes and small letters read faint beside Roboto at the same size. Noto Sans Arabic
 * UI is the Arabic drawn to sit beside Roboto, with even strokes and UI line heights, and
 * it is a variable font, so each weight can be cut a little heavier without jumping to
 * bold.
 *
 * The file (`res/font/noto_sans_arabic_ui.ttf`, OFL, its licence in its name table) holds
 * the Arabic script only: Latin letters and digits were cut from it, so the figures and
 * English words in an Arabic sentence still come from the phone's own sans-serif, at the
 * weight the style asks for.
 */
val ArabicTypography = Typography(
    fontFamily = FontFamily((100..900 step 100).map { ArabicFont(FontWeight(it)) }),
)

/**
 * One weight. Each is a typeface of its own, with the phone's sans-serif behind it for
 * what the file lacks. A resource font would not do: Compose loads it at the file's
 * default weight and then varies it through the paint, which draws every letter falling
 * back to the phone's font at that one weight, or varies that font along with it.
 */
private class ArabicFont(override val weight: FontWeight) :
    AndroidFont(FontLoadingStrategy.Blocking, ArabicLoader, FontVariation.Settings()) {
    override val style: FontStyle = FontStyle.Normal
    override fun toString() = "ArabicFont(${weight.weight})"
}

private object ArabicLoader : AndroidFont.TypefaceLoader {

    /** The file, read once and shared by every weight, as Android shares its own fonts'. */
    private var file: ByteBuffer? = null

    @Synchronized
    private fun file(context: Context): ByteBuffer = file ?: run {
        val bytes = context.resources.openRawResource(R.font.noto_sans_arabic_ui).use { it.readBytes() }
        ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { rewind() }.also { file = it }
    }

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        val weight = font.weight.weight
        val arabic = PlatformFont.Builder(file(context))
            .setWeight(weight)
            .setFontVariationSettings("'wght' ${(weight + HEAVIER).coerceAtMost(900)}")
            .build()
        return Typeface.CustomFallbackBuilder(PlatformFontFamily.Builder(arabic).build())
            .setSystemFallback("sans-serif")
            .setStyle(PlatformFontStyle(weight, PlatformFontStyle.FONT_SLANT_UPRIGHT))
            .build()
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loadBlocking(context, font)
}
