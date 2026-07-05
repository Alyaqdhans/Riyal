@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.theme.successColor
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val monthTitleFmt = DateTimeFormatter.ofPattern("MMMM uuuu")
private val TrendLabelsKey = ExtraStore.Key<List<String>>()

@Composable
fun AnalysisScreen(vm: MainViewModel) {
    val txns by vm.txns.collectAsState()
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }

    // The whole screen follows one time slice: a month by default, or whatever period
    // the user picks by tapping the title (presets or a calendar range).
    var slice by remember { mutableStateOf(TimeSlice.ofMonth(YearMonth.now())) }
    var showSlicePicker by remember { mutableStateOf(false) }
    var showCustomRange by remember { mutableStateOf(false) }

    val totals = remember(txns, slice, currency) {
        Stats.totalsIn(txns, slice.start, slice.endExclusive, currency)
    }
    val slices = remember(txns, slice, currency) {
        Stats.breakdownIn(txns, slice.start, slice.endExclusive, currency)
    }
    val trend = remember(txns, slice, currency) {
        Stats.cumulativeTrend(txns, slice.start, slice.endExclusive, currency)
    }
    val topMerchant = remember(txns, slice, currency) {
        Stats.topMerchantIn(txns, slice.start, slice.endExclusive, currency)
    }
    val biggest = remember(txns, slice, currency) {
        Stats.biggestExpenseIn(txns, slice.start, slice.endExclusive, currency)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Analysis") }) }) { padding ->
        Column(Modifier.padding(padding)) {
            if (txns.isEmpty()) {
                EmptyState(
                    style = FaceStyle.SLEEPY,
                    title = "Nothing to analyze yet",
                    subtitle = "Once you scan your messages, the charts light up here.",
                )
                return@Column
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // period selector: chevrons step, the title opens the period picker
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { slice = slice.shifted(back = true) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier period")
                    }
                    AnimatedContent(targetState = slice.label, label = "sliceTitle") { label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showSlicePicker = true },
                        ) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pick a period")
                        }
                    }
                    IconButton(
                        onClick = { slice = slice.shifted(back = false) },
                        enabled = slice.endExclusive <= System.currentTimeMillis(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later period")
                    }
                }

                // donut + legend
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Multi-color category donut out of stock M3 wavy indicators:
                            // one layer per category at its cumulative fraction, drawn
                            // largest-first so each layer on top masks the start of the
                            // one below, leaving exactly that category's share visible.
                            val grow = remember(slices) { Animatable(0f) }
                            LaunchedEffect(slices) {
                                grow.animateTo(
                                    1f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessVeryLow,
                                    ),
                                )
                            }
                            // Thick stroke + forced amplitude: the defaults are a thin
                            // 4dp line whose wave flattens near 0% and 100%. Long
                            // wavelength so the thick ring carries few, broad waves.
                            val gaugeStroke = Stroke(
                                width = with(LocalDensity.current) { 14.dp.toPx() },
                                cap = StrokeCap.Round,
                            )
                            val cumulative = remember(slices) {
                                var acc = 0f
                                slices.map { s ->
                                    acc += s.fraction
                                    acc.coerceAtMost(1f) to Categories.colorFor(s.categoryId)
                                }
                            }
                            if (cumulative.isEmpty()) {
                                CircularWavyProgressIndicator(
                                    progress = { 0f },
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    stroke = gaugeStroke,
                                    trackStroke = gaugeStroke,
                                    amplitude = { 1f },
                                    wavelength = 42.dp,
                                    modifier = Modifier.size(210.dp),
                                )
                            } else {
                                cumulative.asReversed().forEachIndexed { index, (fraction, colorInt) ->
                                    CircularWavyProgressIndicator(
                                        progress = { fraction * grow.value },
                                        color = Color(colorInt),
                                        trackColor = if (index == 0) {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        } else {
                                            Color.Transparent
                                        },
                                        stroke = gaugeStroke,
                                        trackStroke = gaugeStroke,
                                        amplitude = { 1f },
                                        wavelength = 42.dp,
                                        // Static wave: layered rings must share the exact
                                        // same phase or the color boundaries shimmer.
                                        waveSpeed = 0.dp,
                                        modifier = Modifier.size(210.dp),
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "spent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(Money.format(totals.spent, currency), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    if (slices.isEmpty()) "no expenses" else "across ${slices.size} categories",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        slices.forEachIndexed { index, slice ->
                            val cat = Categories.byId(slice.categoryId)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .popIn(index * 40),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(Categories.colorFor(cat.id))),
                                )
                                Text(
                                    cat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    Money.format(slice.amountMinor, currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${(slice.fraction * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        if (slices.isEmpty()) {
                            Text(
                                "No spending recorded for this period.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Money over time: running totals across the period, drawn by Vico.
                // Where the red line steepens is where the money went; the gap between
                // green and red is what's left.
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Money over time", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Running totals: steep red = heavy spending days, the gap to green is what's left.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (trend.size < 2) {
                            Text(
                                "Not enough activity in this period to draw a trend.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val spentVals = remember(trend) { trend.map { it.spentCumulative } }
                            val receivedVals = remember(trend) { trend.map { it.receivedCumulative } }
                            val labels = remember(trend) { trend.map { it.label } }
                            val modelProducer = remember { CartesianChartModelProducer() }
                            LaunchedEffect(spentVals, receivedVals, labels) {
                                modelProducer.runTransaction {
                                    lineModel {
                                        series(spentVals)
                                        series(receivedVals)
                                    }
                                    extras { it[TrendLabelsKey] = labels }
                                }
                            }
                            CartesianChartHost(
                                chart = rememberCartesianChart(
                                    rememberLineCartesianLayer(
                                        LineCartesianLayer.LineProvider.series(
                                            LineCartesianLayer.Line(
                                                LineCartesianLayer.LineFill.single(Fill(MaterialTheme.colorScheme.error)),
                                            ),
                                            LineCartesianLayer.Line(
                                                LineCartesianLayer.LineFill.single(Fill(successColor())),
                                            ),
                                        ),
                                    ),
                                    startAxis = VerticalAxis.rememberStart(
                                        valueFormatter = CartesianValueFormatter { _, y, _ ->
                                            Money.toMajor(y.toLong(), currency).toBigInteger().toString()
                                        },
                                    ),
                                    bottomAxis = HorizontalAxis.rememberBottom(
                                        valueFormatter = CartesianValueFormatter { context, x, _ ->
                                            context.model.extraStore[TrendLabelsKey].getOrNull(x.toInt()) ?: ""
                                        },
                                    ),
                                ),
                                modelProducer = modelProducer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LegendDot(MaterialTheme.colorScheme.error, "money out")
                            LegendDot(successColor(), "money in")
                        }
                    }
                }

                // insights
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Insights", style = MaterialTheme.typography.titleMedium)
                        InsightRow(
                            R.drawable.ic_insight_store, "Top merchant",
                            topMerchant?.let { "${it.first} · ${Money.format(it.second, currency)}" } ?: "none yet",
                        )
                        InsightRow(
                            R.drawable.ic_insight_bolt, "Biggest expense",
                            biggest?.let {
                                "${it.merchant ?: Categories.byId(it.categoryId).name} · ${Money.format(it.amountMinor, it.currency)}"
                            } ?: "none yet",
                        )
                        InsightRow(
                            R.drawable.ic_insight_calendar, "Average per day",
                            Money.format(
                                Stats.avgSpentPerDayIn(totals.spent, slice.start, slice.endExclusive),
                                currency,
                            ),
                        )
                        if (totals.otherCurrencyCount > 0) {
                            Text(
                                "Charts show $currency only, ${totals.otherCurrencyCount} transaction(s) in other currencies are listed in Activity.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Room for the floating toolbar hovering over the content.
                Spacer(Modifier.height(88.dp))
            }
        }
    }

    if (showSlicePicker) {
        AlertDialog(
            onDismissRequest = { showSlicePicker = false },
            title = { Text("Pick a period") },
            text = {
                Column {
                    val now = YearMonth.now()
                    PickerOption("This month") { slice = TimeSlice.ofMonth(now); showSlicePicker = false }
                    PickerOption("Last month") { slice = TimeSlice.ofMonth(now.minusMonths(1)); showSlicePicker = false }
                    PickerOption("Last 3 months") { slice = TimeSlice.lastMonths(3); showSlicePicker = false }
                    PickerOption("Last 6 months") { slice = TimeSlice.lastMonths(6); showSlicePicker = false }
                    PickerOption("This year") { slice = TimeSlice.thisYear(); showSlicePicker = false }
                    PickerOption("All time") { slice = TimeSlice.allTime(txns); showSlicePicker = false }
                    PickerOption("Custom range…") { showSlicePicker = false; showCustomRange = true }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSlicePicker = false }) { Text("Close") }
            },
        )
    }

    if (showCustomRange) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustomRange = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null &&
                        rangeState.selectedEndDateMillis != null,
                    onClick = {
                        slice = TimeSlice.ofDays(
                            utcDay(rangeState.selectedStartDateMillis!!),
                            utcDay(rangeState.selectedEndDateMillis!!),
                        )
                        showCustomRange = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRange = false }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(
                state = rangeState,
                modifier = Modifier.height(460.dp),
            )
        }
    }
}

@Composable
private fun PickerOption(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

/** The picker returns UTC-midnight millis for a calendar day. */
private fun utcDay(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * One selected period of time. Chevrons shift it by its own length; a slice that is
 * exactly one calendar month keeps stepping through calendar months.
 */
private data class TimeSlice(
    val start: Long,
    val endExclusive: Long,
    val label: String,
    val month: YearMonth? = null,
) {
    fun shifted(back: Boolean): TimeSlice {
        if (month != null) return ofMonth(if (back) month.minusMonths(1) else month.plusMonths(1))
        val zone = ZoneId.systemDefault()
        val startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(endExclusive - 1).atZone(zone).toLocalDate()
        val lengthDays = endDay.toEpochDay() - startDay.toEpochDay() + 1
        val shift = if (back) -lengthDays else lengthDays
        return ofDays(startDay.plusDays(shift), endDay.plusDays(shift))
    }

    companion object {
        private val zone: ZoneId get() = ZoneId.systemDefault()
        private val dayFmt = DateTimeFormatter.ofPattern("d MMM uu")

        private fun dayStart(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

        fun ofMonth(m: YearMonth) = TimeSlice(
            start = dayStart(m.atDay(1)),
            endExclusive = dayStart(m.plusMonths(1).atDay(1)),
            label = m.format(monthTitleFmt),
            month = m,
        )

        fun ofDays(startDay: LocalDate, endDay: LocalDate) = TimeSlice(
            start = dayStart(startDay),
            endExclusive = dayStart(endDay.plusDays(1)),
            label = "${dayFmt.format(startDay)} – ${dayFmt.format(endDay)}",
        )

        fun lastMonths(n: Int): TimeSlice {
            val end = YearMonth.now()
            return TimeSlice(
                start = dayStart(end.minusMonths(n - 1L).atDay(1)),
                endExclusive = dayStart(end.plusMonths(1).atDay(1)),
                label = "Last $n months",
            )
        }

        fun thisYear(): TimeSlice {
            val year = LocalDate.now().year
            return TimeSlice(
                start = dayStart(LocalDate.of(year, 1, 1)),
                endExclusive = dayStart(LocalDate.of(year + 1, 1, 1)),
                label = "$year",
            )
        }

        fun allTime(txns: List<Txn>): TimeSlice {
            val oldest = txns.minOfOrNull { it.atMillis } ?: System.currentTimeMillis()
            return TimeSlice(
                start = dayStart(Instant.ofEpochMilli(oldest).atZone(zone).toLocalDate()),
                endExclusive = dayStart(LocalDate.now().plusDays(1)),
                label = "All time",
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsightRow(@DrawableRes iconRes: Int, label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
