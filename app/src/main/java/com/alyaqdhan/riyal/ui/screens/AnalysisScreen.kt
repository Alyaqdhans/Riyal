@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.theme.successColor
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val monthTitleFmt = DateTimeFormatter.ofPattern("MMMM uuuu")
private val barLabelFmt = DateTimeFormatter.ofPattern("MMM")
private val MonthLabelsKey = ExtraStore.Key<List<String>>()

@Composable
fun AnalysisScreen(vm: MainViewModel) {
    val txns by vm.txns.collectAsState()
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    val months = remember(txns) { Stats.monthsWithData(txns).ifEmpty { listOf(YearMonth.now()) } }
    var monthIndex by remember(months) { mutableIntStateOf(months.lastIndex) }
    val month = months[monthIndex.coerceIn(0, months.lastIndex)]

    val totals = remember(txns, month, currency) { Stats.totalsFor(txns, month, currency) }
    val slices = remember(txns, month, currency) { Stats.breakdown(txns, month, currency) }
    val series = remember(txns, month, currency) { Stats.series(txns, currency, month) }
    val topMerchant = remember(txns, month, currency) { Stats.topMerchant(txns, month, currency) }
    val biggest = remember(txns, month, currency) { Stats.biggestExpense(txns, month, currency) }

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
                // month selector
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { monthIndex-- }, enabled = monthIndex > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier month")
                    }
                    AnimatedContent(targetState = month, label = "monthTitle") { m ->
                        Text(m.format(monthTitleFmt), style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { monthIndex++ }, enabled = monthIndex < months.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later month")
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
                                "No spending recorded for this month.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 6-month grouped columns, drawn by Vico
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
                        val spentVals = remember(series) { series.map { it.spent } }
                        val receivedVals = remember(series) { series.map { it.received } }
                        val monthLabels = remember(series) { series.map { it.month.format(barLabelFmt) } }
                        val modelProducer = remember { CartesianChartModelProducer() }
                        LaunchedEffect(spentVals, receivedVals, monthLabels) {
                            modelProducer.runTransaction {
                                columnModel {
                                    series(spentVals)
                                    series(receivedVals)
                                }
                                extras { it[MonthLabelsKey] = monthLabels }
                            }
                        }
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                rememberColumnCartesianLayer(
                                    ColumnCartesianLayer.ColumnProvider.series(
                                        rememberLineComponent(Fill(MaterialTheme.colorScheme.error), 10.dp),
                                        rememberLineComponent(Fill(successColor()), 10.dp),
                                    ),
                                ),
                                bottomAxis = HorizontalAxis.rememberBottom(
                                    valueFormatter = CartesianValueFormatter { context, x, _ ->
                                        context.model.extraStore[MonthLabelsKey].getOrNull(x.toInt()) ?: ""
                                    },
                                ),
                            ),
                            modelProducer = modelProducer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
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
                            Money.format(Stats.avgSpentPerDay(totals.spent, month), currency),
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
