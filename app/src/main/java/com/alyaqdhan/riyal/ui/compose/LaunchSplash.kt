package com.alyaqdhan.riyal.ui.compose

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.ui.theme.legibleOn
import com.alyaqdhan.riyal.ui.theme.TEXT_CONTRAST
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** The Omani rial sign, as the app icon draws it (ic_launcher_foreground). */
private const val RIAL_SIGN =
    "M259.9,219.89c-.63-49.2,11.44-95.41,35.76-137.75C331.7,19.4,371.24-.36,439.78,34.99c10.67,5.5," +
        "53.6,35.43,57.81,44.54,5.03,10.87-27.48,103.87-29.11,122.3-34.69-37.51-99.37-98.66-154.85-69.62" +
        "-45.05,23.58-12.02,62.54,11.46,87.68h406.25l-39.14,70.23-289.2-2c-1.11,4.66.87,3.3,2.53,4.6," +
        "12.44,9.72,80.97,31.54,94.75,31.54l172.05,1.99-39.49,71.25H10.03l39.24-71.24h272.14l-37.11-36.13" +
        "H69.33l39.23-70.23h151.33Z"

/** firePOS's splash easing and timing: a deliberate trace, a hold, a fade, again. */
private val traceEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private const val TRACE_MS = 1_100
private const val HOLD_MS = 700
private const val FADE_MS = 350
private const val LEAVE_MS = 350

/** However slow the phone, the app opens after this; the store is nearly always far sooner. */
private const val LONGEST_MS = 4_000L

/**
 * The app opening, after firePOS's splash: the rial sign drawn as a line, its outline
 * traced by a pen that runs once round it, then holds, fades and starts again for as
 * long as the app is still loading. Once [ready] - the records read from disk - the
 * trace in hand finishes, the sign fills in, and the splash scales down a touch as it
 * fades ([onLeaving], then [onGone]).
 *
 * It sits over the tabs from the first frame on the system splash's own background, so
 * the system's (iconless) splash hands over to it without a seam, and Home is built
 * underneath it while it plays: when it lifts, the figures roll in on a phone with
 * nothing left to do.
 */
@Composable
fun LaunchSplash(ready: () -> Boolean, onLeaving: () -> Unit, onGone: () -> Unit) {
    val glyph = remember { PathParser().parsePathString(RIAL_SIGN).toPath() }
    val bounds = remember { glyph.getBounds() }
    val measure = remember { PathMeasure().apply { setPath(glyph, false) } }
    val traced = remember { Path() }

    val trace = remember { Animatable(0f) }
    val ink = remember { Animatable(1f) }
    val fill = remember { Animatable(0f) }
    val brand = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        launch {
            delay(450)
            brand.animateTo(1f, tween(500, easing = traceEasing))
        }
        while (true) {
            ink.snapTo(1f)
            trace.snapTo(0f)
            trace.animateTo(1f, tween(TRACE_MS, easing = traceEasing))
            if (ready() || SystemClock.uptimeMillis() - start > LONGEST_MS) break
            delay(HOLD_MS.toLong())
            if (ready()) break
            ink.animateTo(0f, tween(FADE_MS, easing = traceEasing))
            delay(150)
        }
        fill.animateTo(1f, tween(300, easing = traceEasing))
        delay(120)
        onLeaving()
        leave.animateTo(1f, tween(LEAVE_MS, easing = traceEasing))
        onGone()
    }

    // The brand teal, lightened on the dark splash (3.5:1 as it comes) or darkened on a
    // light wallpaper colour, until the name reads as text does: 4.5:1.
    val background = colorResource(R.color.splash_background)
    val brandTeal = colorResource(R.color.brand_teal)
    val teal = remember(brandTeal, background) {
        Color(legibleOn(brandTeal.toArgb(), background.toArgb(), TEXT_CONTRAST))
    }
    val pen = Brush.linearGradient(
        listOf(teal, MaterialTheme.colorScheme.tertiary),
        start = Offset(bounds.left, bounds.top),
        end = Offset(bounds.right, bounds.bottom),
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - leave.value
                val s = 1f - 0.06f * leave.value
                scaleX = s
                scaleY = s
            }
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Canvas(Modifier.size(width = 150.dp, height = 80.dp)) {
                val line = 2.5.dp.toPx()
                val k = min((size.width - 2 * line) / bounds.width, (size.height - 2 * line) / bounds.height)
                translate(
                    left = (size.width - bounds.width * k) / 2f - bounds.left * k,
                    top = (size.height - bounds.height * k) / 2f - bounds.top * k,
                ) {
                    scale(k, k, pivot = Offset.Zero) {
                        if (fill.value > 0f) drawPath(glyph, pen, alpha = fill.value)
                        traced.reset()
                        measure.getSegment(0f, measure.length * trace.value, traced, true)
                        drawPath(
                            traced,
                            pen,
                            alpha = ink.value,
                            style = Stroke(width = line / k, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = teal,
                modifier = Modifier.graphicsLayer {
                    alpha = brand.value
                    translationY = (1f - brand.value) * 10.dp.toPx()
                },
            )
        }
    }
}
