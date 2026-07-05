package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay

enum class FaceStyle { NORMAL, SLEEPY, CONFUSED, DIZZY }

// The launcher icon's smiling rial coin, exactly: gold fill, darker gold ring,
// brown features. Fixed colors so the mascot looks the same everywhere in the app.
private val CoinFill = Color(0xFFFFE082)
private val CoinRing = Color(0xFFF9A825)
private val CoinFeatures = Color(0xFF5D4037)

/**
 * The Riyal mascot: the same smiling rial coin as the app icon. mood runs -1
 * (worried) .. +1 (delighted) and moves on a bouncy spring; the face blinks on its
 * own. SLEEPY/CONFUSED/DIZZY dress the empty, review and error states around the app.
 */
@Composable
fun Face(
    mood: Float,
    modifier: Modifier = Modifier,
    style: FaceStyle = FaceStyle.NORMAL,
    blinking: Boolean = true,
) {
    val background = CoinFill
    val features = CoinFeatures
    val animatedMood by animateFloatAsState(
        targetValue = mood.coerceIn(-1f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "mood",
    )
    val blink = remember { Animatable(0f) }
    LaunchedEffect(style, blinking) {
        if (!blinking || (style != FaceStyle.NORMAL && style != FaceStyle.CONFUSED)) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2200, 5800))
            blink.animateTo(1f, tween(70))
            blink.animateTo(0f, tween(110))
        }
    }

    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f * 0.96f
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(background, radius = r, center = Offset(cx, cy))
        // Inner ring, same proportions as the launcher icon (r 21/26, stroke 3/26).
        drawCircle(
            CoinRing,
            radius = r * 0.81f,
            center = Offset(cx, cy),
            style = Stroke(r * 0.115f),
        )

        val strokeWidth = r * 0.085f
        val eyeY = cy - r * 0.18f
        val eyeDx = r * 0.34f
        val eyeR = r * 0.105f

        when (style) {
            FaceStyle.DIZZY -> {
                crossEye(features, cx - eyeDx, eyeY, eyeR, strokeWidth)
                crossEye(features, cx + eyeDx, eyeY, eyeR, strokeWidth)
            }
            FaceStyle.SLEEPY -> {
                closedEye(features, cx - eyeDx, eyeY, eyeR, strokeWidth)
                closedEye(features, cx + eyeDx, eyeY, eyeR, strokeWidth)
            }
            else -> {
                val openness = 1f - blink.value
                if (openness < 0.15f) {
                    closedEye(features, cx - eyeDx, eyeY, eyeR, strokeWidth)
                    closedEye(features, cx + eyeDx, eyeY, eyeR, strokeWidth)
                } else {
                    scale(scaleX = 1f, scaleY = openness, pivot = Offset(cx, eyeY)) {
                        drawCircle(features, eyeR, Offset(cx - eyeDx, eyeY))
                        drawCircle(features, eyeR, Offset(cx + eyeDx, eyeY))
                    }
                }
                if (style == FaceStyle.CONFUSED) {
                    drawLine(
                        features,
                        Offset(cx + eyeDx - eyeR * 1.4f, eyeY - eyeR * 2.6f),
                        Offset(cx + eyeDx + eyeR * 1.4f, eyeY - eyeR * 3.2f),
                        strokeWidth, StrokeCap.Round,
                    )
                }
            }
        }

        val mouthY = cy + r * 0.32f
        val mouthHalf = r * 0.38f
        when (style) {
            FaceStyle.SLEEPY -> drawCircle(
                features, r * 0.09f, Offset(cx, mouthY + r * 0.05f),
                style = Stroke(strokeWidth),
            )
            FaceStyle.DIZZY -> drawOval(
                features,
                topLeft = Offset(cx - r * 0.16f, mouthY - r * 0.06f),
                size = Size(r * 0.32f, r * 0.26f),
            )
            FaceStyle.CONFUSED -> {
                val path = Path().apply {
                    moveTo(cx - mouthHalf, mouthY)
                    cubicTo(
                        cx - mouthHalf / 2f, mouthY - r * 0.12f,
                        cx + mouthHalf / 2f, mouthY + r * 0.12f,
                        cx + mouthHalf, mouthY,
                    )
                }
                drawPath(path, features, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            FaceStyle.NORMAL -> {
                if (animatedMood > 0.65f) {
                    // big open smile
                    drawArc(
                        color = features,
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = true,
                        topLeft = Offset(cx - mouthHalf, mouthY - r * 0.30f),
                        size = Size(mouthHalf * 2f, r * 0.52f),
                    )
                } else {
                    val path = Path().apply {
                        moveTo(cx - mouthHalf, mouthY)
                        quadraticTo(cx, mouthY + r * 0.34f * animatedMood, cx + mouthHalf, mouthY)
                    }
                    drawPath(path, features, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                    if (animatedMood < -0.55f) {
                        // worried brows
                        drawLine(
                            features,
                            Offset(cx - eyeDx - eyeR, eyeY - eyeR * 2.9f),
                            Offset(cx - eyeDx + eyeR * 1.2f, eyeY - eyeR * 2.2f),
                            strokeWidth, StrokeCap.Round,
                        )
                        drawLine(
                            features,
                            Offset(cx + eyeDx - eyeR * 1.2f, eyeY - eyeR * 2.2f),
                            Offset(cx + eyeDx + eyeR, eyeY - eyeR * 2.9f),
                            strokeWidth, StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.closedEye(color: Color, x: Float, y: Float, r: Float, stroke: Float) {
    drawLine(color, Offset(x - r, y), Offset(x + r, y), stroke, StrokeCap.Round)
}

private fun DrawScope.crossEye(color: Color, x: Float, y: Float, r: Float, stroke: Float) {
    drawLine(color, Offset(x - r, y - r), Offset(x + r, y + r), stroke, StrokeCap.Round)
    drawLine(color, Offset(x - r, y + r), Offset(x + r, y - r), stroke, StrokeCap.Round)
}
