package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Donut whose sweep grows on a lazy spring whenever the data changes. */
@Composable
fun DonutChart(
    slices: List<Pair<Float, Color>>, // fraction (0..1) to color
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        )
    }
    Canvas(modifier) {
        val d = size.minDimension
        val strokeWidth = d * 0.13f
        val inset = strokeWidth / 2f + 2.dp.toPx()
        val topLeft = Offset((size.width - d) / 2f + inset, (size.height - d) / 2f + inset)
        val arcSize = Size(d - inset * 2f, d - inset * 2f)

        if (slices.isEmpty()) {
            drawArc(
                trackColor, 0f, 360f, false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            return@Canvas
        }
        val p = progress.value.coerceIn(0f, 1f)
        val gap = if (slices.size > 1) 3f else 0f
        var start = -90f
        slices.forEach { (fraction, color) ->
            val fullSweep = fraction * 360f
            val sweep = max(fullSweep - gap, 0.5f) * p
            drawArc(
                color, start + gap / 2f, sweep, false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt),
            )
            start += fullSweep
        }
    }
}

data class BarGroup(val label: String, val expense: Float, val income: Float)

/** Paired monthly bars that spring up one after another. */
@Composable
fun BarChart(
    bars: List<BarGroup>,
    expenseColor: Color,
    incomeColor: Color,
    labelColor: Color,
    baselineColor: Color,
    modifier: Modifier = Modifier,
) {
    val fractions = remember(bars) { bars.map { Animatable(0f) } }
    LaunchedEffect(bars) {
        fractions.forEachIndexed { index, animatable ->
            launch {
                delay(index * 70L)
                animatable.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                )
            }
        }
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    Canvas(modifier) {
        if (bars.isEmpty()) return@Canvas
        val labelSpace = 18.dp.toPx()
        val chartHeight = size.height - labelSpace
        val maxValue = max(bars.maxOf { max(it.expense, it.income) }, 1f)
        val groupWidth = size.width / bars.size
        val barWidth = groupWidth * 0.26f
        val barGap = groupWidth * 0.08f
        val corner = CornerRadius(barWidth / 2.5f, barWidth / 2.5f)

        // baseline
        drawLine(
            baselineColor,
            Offset(0f, chartHeight),
            Offset(size.width, chartHeight),
            strokeWidth = 1.dp.toPx(),
        )

        bars.forEachIndexed { index, bar ->
            val fraction = fractions[index].value
            val centerX = groupWidth * index + groupWidth / 2f

            val expenseH = (bar.expense / maxValue) * (chartHeight * 0.92f) * fraction
            if (expenseH > 0.5f) {
                drawRoundRect(
                    expenseColor,
                    topLeft = Offset(centerX - barGap / 2f - barWidth, chartHeight - expenseH),
                    size = Size(barWidth, expenseH),
                    cornerRadius = corner,
                )
            }
            val incomeH = (bar.income / maxValue) * (chartHeight * 0.92f) * fraction
            if (incomeH > 0.5f) {
                drawRoundRect(
                    incomeColor,
                    topLeft = Offset(centerX + barGap / 2f, chartHeight - incomeH),
                    size = Size(barWidth, incomeH),
                    cornerRadius = corner,
                )
            }

            val layout = measurer.measure(AnnotatedString(bar.label), labelStyle)
            drawText(
                layout,
                topLeft = Offset(centerX - layout.size.width / 2f, chartHeight + 4.dp.toPx()),
            )
        }
    }
}
