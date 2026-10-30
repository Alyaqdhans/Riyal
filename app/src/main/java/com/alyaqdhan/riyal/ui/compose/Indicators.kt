@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.ui.theme.successColor
import kotlin.math.PI
import kotlin.math.abs

/*
 * Material's wavy progress indicators, held flat.
 *
 * They are the stock M3 Expressive components - rounded ends, the gap in front of the
 * track - with the wave switched off. A wobbling line on a screen of money reads as
 * decoration, but the component underneath is the one the platform ships, not a
 * drawing of one.
 */

/** The spinner for "working on it", wherever a wait is shown. */
@Composable
fun FlatWheel(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
) {
    CircularWavyProgressIndicator(
        modifier = modifier,
        color = color,
        amplitude = 0f,
        waveSpeed = 0.dp,
    )
}

/** A determinate bar: budgets, category shares, a scan with a known total. */
@Composable
fun FlatBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        amplitude = { 0f },
        waveSpeed = 0.dp,
    )
}

/** An indeterminate bar, for a wait whose length is not known yet. */
@Composable
fun FlatBar(modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(modifier = modifier, amplitude = 0f, waveSpeed = 0.dp)
}

/**
 * The bar a scan shows while it reads the inbox: Material's wavy indicator with its wave
 * left on, the one bar that keeps it. A scan is the app at work, and the moving wave
 * says so; every other bar stands for an amount and stays flat. Determinate once the
 * number of messages is known.
 */
@Composable
fun ScanBar(progress: (() -> Float)?, modifier: Modifier = Modifier) {
    if (progress != null) {
        LinearWavyProgressIndicator(progress = progress, modifier = modifier)
    } else {
        LinearWavyProgressIndicator(modifier = modifier)
    }
}

/** What has waited on a page since it opened ([total]), and how much of it has gone ([done]). */
class Sifting(val done: Int, val total: Int) {
    val left: Int get() = total - done
}

/**
 * Keeps [Sifting] for a pile by identity: everything that has waited since the page
 * opened, and which of it is gone. An answer undone comes back as waiting, and what a
 * scan adds makes the pile bigger rather than counting as done.
 */
@Composable
fun rememberSifting(waiting: Set<String>): Sifting {
    val seen = remember { HashSet<String>() }
    seen.addAll(waiting)
    return Sifting(done = seen.count { it !in waiting }, total = seen.size)
}

/**
 * How far through a pile this visit has got - Review's messages, the records waiting for a
 * category - as Material's wavy bar with its wave left on, like the scan's: working through
 * a pile is the app at work too, and the bar filling as the cards go makes a long one
 * something to finish. Under it, what is done and what is left; green once nothing is.
 * Not shown when there was nothing to do.
 */
@Composable
fun SiftBar(sifting: Sifting, modifier: Modifier = Modifier) {
    if (sifting.total == 0) return
    val progress by animateFloatAsState(
        sifting.done.toFloat() / sifting.total,
        WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "sifted",
    )
    val finished = sifting.left == 0
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = if (finished) successColor() else WavyProgressIndicatorDefaults.indicatorColor,
        )
        Text(
            when {
                finished -> stringResource(R.string.sift_all_done, sifting.total)
                sifting.done == 0 -> stringResource(R.string.sift_left, sifting.left)
                else -> stringResource(R.string.sift_done_left, sifting.done, sifting.left)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Where each slice of the wheel goes: its share of the circle, except that no slice is
 * narrower than [minDegrees]. A round-ended arc is drawn [minDegrees] wide however small
 * its share, so without this a run of small categories would be dots drawn on top of
 * each other; the large slices give up the difference, in proportion.
 *
 * Pure, and kept apart from the drawing so it can be tested.
 */
internal fun wheelAngles(fractions: List<Float>, minDegrees: Float): List<Float> {
    val n = fractions.size
    if (n == 0) return emptyList()
    if (n * minDegrees >= 360f) return List(n) { 360f / n }
    var angles = fractions.map { (it * 360f).coerceAtLeast(0f) }
    // Pinning one slice at the minimum shrinks the others, which can push another one
    // under it, so this settles in at most n rounds.
    repeat(n) {
        val pinned = angles.map { it < minDegrees }
        val freeTotal = angles.filterIndexed { i, _ -> !pinned[i] }.sum()
        if (freeTotal <= 0f) return List(n) { 360f / n }
        val scale = (360f - pinned.count { it } * minDegrees) / freeTotal
        val next = angles.mapIndexed { i, a -> if (pinned[i]) minDegrees else a * scale }
        if (next.indices.all { abs(next[it] - angles[it]) < 0.01f }) return next
        angles = next
    }
    return angles
}

/**
 * The category wheel on Analysis: one flat [CircularWavyProgressIndicator] per category,
 * each turned to where its share begins and sweeping only its own width, over a track
 * drawn by the same component. The middle is free for [center].
 *
 * It fills clockwise from the top, slice after slice, with the rolling figures' timing:
 * when it is first drawn, each time its tab is brought into view ([LocalTabShows]), when
 * a scan or refresh lands ([Rolls]) and when its slices change. The fill is read only
 * while drawing, so it costs a redraw a frame.
 */
@Composable
fun CategoryWheel(
    slices: List<Stats.Slice>,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
    thickness: Dp = 16.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val strokePx = with(density) { thickness.toPx() }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    // Degrees of arc that one stroke-width covers on this ring: a round end reaches half
    // of that past each end of the arc it caps.
    val radius = (size - thickness).value / 2f
    val capDegrees = (thickness.value / radius) * (180f / PI.toFloat())
    val gapDegrees = (3f / radius) * (180f / PI.toFloat())
    val angles = wheelAngles(slices.map { it.fraction }, minDegrees = capDegrees + gapDegrees + 0.5f)
    val fill = remember { Animatable(0f) }
    LaunchedEffect(LocalTabShows.current, Rolls.generation, slices) {
        fill.snapTo(0f)
        fill.animateTo(1f, tween(SPIN_MS, easing = spinEasing))
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { 1f },
            modifier = Modifier.matchParentSize(),
            color = track,
            trackColor = Color.Transparent,
            stroke = Stroke(width = strokePx, cap = StrokeCap.Butt),
            trackStroke = Stroke(width = strokePx, cap = StrokeCap.Butt),
            gapSize = 0.dp,
            amplitude = { 0f },
            waveSpeed = 0.dp,
        )
        if (slices.size == 1) {
            // One category is the whole circle: a ring, with no ends to round.
            CircularWavyProgressIndicator(
                progress = { fill.value },
                modifier = Modifier.matchParentSize(),
                color = categoryColor(slices[0].categoryId),
                trackColor = Color.Transparent,
                stroke = Stroke(width = strokePx, cap = StrokeCap.Butt),
                trackStroke = Stroke(width = strokePx, cap = StrokeCap.Butt),
                gapSize = 0.dp,
                amplitude = { 0f },
                waveSpeed = 0.dp,
            )
        } else {
            var start = 0f
            slices.forEachIndexed { i, s ->
                val angle = angles[i]
                val sweep = (angle - capDegrees - gapDegrees).coerceAtLeast(0.01f)
                val from = start + (capDegrees + gapDegrees) / 2f
                // How much of this slice the fill has reached; one it has not reached yet
                // is hidden, so its round end is not left as a dot ahead of the fill.
                fun shown() = (fill.value * 360f - from).coerceIn(0f, sweep)
                CircularWavyProgressIndicator(
                    progress = { shown() / 360f },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = if (shown() > 0f) 1f else 0f }
                        .rotate(from),
                    color = categoryColor(s.categoryId),
                    trackColor = Color.Transparent,
                    stroke = Stroke(width = strokePx, cap = StrokeCap.Round),
                    trackStroke = Stroke(width = strokePx, cap = StrokeCap.Round),
                    gapSize = 0.dp,
                    amplitude = { 0f },
                    waveSpeed = 0.dp,
                )
                start += angle
            }
        }
        center()
    }
}
