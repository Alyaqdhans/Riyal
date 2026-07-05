@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.BarChart
import com.alyaqdhan.riyal.ui.compose.BarGroup
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.theme.successColor
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val monthTitleFmt = DateTimeFormatter.ofPattern("MMMM uuuu")
private val barLabelFmt = DateTimeFormatter.ofPattern("MMM")

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
                            // M3 Expressive wavy gauge: how much of the month's income
                            // is already spent, in semantic color (green → orange → red).
                            val target = when {
                                totals.received > 0L ->
                                    (totals.spent.toFloat() / totals.received.toFloat()).coerceIn(0f, 1f)
                                totals.spent > 0L -> 1f
                                else -> 0f
                            }
                            val gaugeProgress by animateFloatAsState(
                                targetValue = target,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessVeryLow,
                                ),
                                label = "gauge",
                            )
                            CircularWavyProgressIndicator(
                                progress = { gaugeProgress },
                                color = when {
                                    target >= 0.99f -> MaterialTheme.colorScheme.error
                                    target > 0.8f -> MaterialTheme.colorScheme.primary
                                    else -> successColor()
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(210.dp),
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "spent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(Money.format(totals.spent, currency), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    when {
                                        totals.received > 0L -> "${(target * 100).roundToInt()}% of income"
                                        slices.isEmpty() -> "no expenses"
                                        else -> "across ${slices.size} categories"
                                    },
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

                // 6-month bars
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
                        BarChart(
                            bars = series.map {
                                BarGroup(it.month.format(barLabelFmt), it.spent.toFloat(), it.received.toFloat())
                            },
                            expenseColor = MaterialTheme.colorScheme.error,
                            incomeColor = successColor(),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            baselineColor = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
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
                Spacer(Modifier.height(8.dp))
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
