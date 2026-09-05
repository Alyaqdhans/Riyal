@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.alyaqdhan.riyal.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.alyaqdhan.riyal.ui.compose.countOf
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.ToolbarSpacer
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.theme.successColor
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val ChartLabelsKey = ExtraStore.Key<List<String>>()
private val nextDueFmt = DateTimeFormatter.ofPattern("d MMM")
private val dayFmt = DateTimeFormatter.ofPattern("d MMM")

/**
 * Analysis answers three questions the rest of the app can't: where the money went,
 * whether that is more or less than usual, and what is going to happen again.
 *
 * Everything on the screen obeys two controls at the top - the account filter and the
 * period - and every figure is measured against the equally long period before it,
 * because a number with nothing to compare it to is just a number.
 */
@Composable
fun AnalysisScreen(vm: MainViewModel, onOpenCategory: (String, TimeSlice) -> Unit) {
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val budgetsOn by vm.budgetsOn.collectAsState()
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }

    val slice by vm.analysisSlice.collectAsState()
    var accountId by remember { mutableStateOf<String?>(null) }
    var donutType by remember { mutableStateOf(TxnType.EXPENSE) }

    // Accounts can be deleted while a filter is pinned to one; drop back to All rather
    // than showing an empty screen with no obvious cause.
    LaunchedEffect(accounts) {
        if (accountId != null && accounts.none { it.id == accountId }) accountId = null
    }

    val totals = remember(txns, slice, currency, accountId) {
        Stats.totalsIn(txns, slice.start, slice.endExclusive, currency, accountId)
    }
    val previous = remember(txns, slice, currency, accountId) {
        val (s, e) = Stats.previousWindow(slice.start, slice.endExclusive)
        Stats.totalsIn(txns, s, e, currency, accountId)
    }
    val slices = remember(txns, slice, currency, accountId, donutType) {
        Stats.breakdownIn(txns, slice.start, slice.endExclusive, currency, accountId, donutType)
    }
    val previousSlices = remember(txns, slice, currency, accountId, donutType) {
        val (s, e) = Stats.previousWindow(slice.start, slice.endExclusive)
        Stats.breakdownIn(txns, s, e, currency, accountId, donutType)
            .associate { it.categoryId to it.amountMinor }
    }
    val trend = remember(txns, slice, currency, accountId) {
        Stats.cumulativeTrend(txns, slice.start, slice.endExclusive, currency, accountId)
    }
    val flow = remember(txns, slice, currency, accountId) {
        Stats.cashflow(txns, slice.start, slice.endExclusive, currency, accountId)
    }
    val movers = remember(txns, slice, currency, accountId) {
        Stats.biggestMovers(txns, slice.start, slice.endExclusive, currency, accountId)
    }
    val spread = remember(txns, slice, currency, accountId) {
        Stats.spread(txns, slice.start, slice.endExclusive, currency, accountId)
    }
    val recurring = remember(txns, currency, accountId) {
        Stats.recurring(txns, currency, accountId)
    }
    val biggest = remember(txns, slice, currency, accountId) {
        Stats.biggestExpenseIn(txns, slice.start, slice.endExclusive, currency, accountId)
    }
    val moved = remember(txns, slice, currency, accountId) {
        Stats.transferTotalIn(txns, slice.start, slice.endExclusive, currency, accountId)
    }
    val budgetProgress = remember(budgets, txns, slice, currency, accountId, budgetsOn) {
        if (!budgetsOn) null else {
            budgets.firstOrNull { it.overlaps(slice.start, slice.endExclusive) && it.lines.isNotEmpty() }
                ?.let { Stats.budgetProgress(it, txns, currency, accountId) }
        }
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

            // The account filter sits outside the scroll: it scopes everything below,
            // so it should never scroll out of sight while you read the numbers.
            if (accounts.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = accountId == null,
                            onClick = { accountId = null },
                            label = { Text("All accounts") },
                        )
                    }
                    items(accounts) { acc ->
                        FilterChip(
                            selected = accountId == acc.id,
                            onClick = { accountId = if (accountId == acc.id) null else acc.id },
                            label = { Text(acc.displayName) },
                        )
                    }
                }
            }

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PeriodBar(slice = slice, onChange = { vm.setAnalysisSlice(it) }, txns = txns)

                // ── the three headline numbers, each against the period before
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryTile(
                        label = "Spent",
                        amount = totals.spent,
                        previous = previous.spent,
                        currency = currency,
                        // Spending less than last period is the good direction.
                        upIsGood = false,
                        modifier = Modifier.weight(1f).popIn(),
                    )
                    SummaryTile(
                        label = "Received",
                        amount = totals.received,
                        previous = previous.received,
                        currency = currency,
                        upIsGood = true,
                        modifier = Modifier.weight(1f).popIn(50),
                    )
                    SummaryTile(
                        label = "Net",
                        amount = totals.net,
                        previous = previous.net,
                        currency = currency,
                        upIsGood = true,
                        signed = true,
                        modifier = Modifier.weight(1f).popIn(100),
                    )
                }
                if (moved > 0) {
                    Text(
                        "${Money.format(moved, currency)} moved between your own accounts and is " +
                            "counted in neither figure.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── donut + legend, either side of the ledger
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = donutType == TxnType.EXPENSE,
                                onClick = { donutType = TxnType.EXPENSE },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                modifier = Modifier.weight(1f),
                            ) { Text("Spending", softWrap = false, maxLines = 1) }
                            SegmentedButton(
                                selected = donutType == TxnType.INCOME,
                                onClick = { donutType = TxnType.INCOME },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                modifier = Modifier.weight(1f),
                            ) { Text("Income", softWrap = false, maxLines = 1) }
                        }
                        Box(contentAlignment = Alignment.Center) {
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
                            // The empty track, then each category as its own arc on top
                            // of it. The ring underneath is what draws the circle when
                            // there is nothing to show.
                            DonutRing(
                                progress = { 0f },
                                color = Color.Transparent,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            var startFraction = 0f
                            slices.forEach { s ->
                                val from = startFraction
                                startFraction += s.fraction
                                DonutRing(
                                    // Each arc is turned to where its share begins and
                                    // sweeps only its own width, so the colours sit side
                                    // by side instead of being stacked and masked.
                                    rotationDegrees = from * 360f + DonutGapDegrees / 2f,
                                    progress = {
                                        val sweep = s.fraction * 360f - DonutGapDegrees
                                        (sweep.coerceAtLeast(1f) / 360f) * grow.value
                                    },
                                    color = Color(Categories.colorFor(s.categoryId)),
                                    trackColor = Color.Transparent,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (donutType == TxnType.EXPENSE) "spent" else "received",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    Money.formatAmount(
                                        if (donutType == TxnType.EXPENSE) totals.spent else totals.received,
                                        currency,
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    currency + if (slices.isEmpty()) " · nothing recorded"
                                    else " · ${slices.size} categories",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        slices.forEachIndexed { index, s ->
                            val cat = Categories.byId(s.categoryId)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onOpenCategory(cat.id, slice) }
                                    .padding(vertical = 4.dp)
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
                                Column(Modifier.weight(1f)) {
                                    Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                                    DeltaText(s.amountMinor, previousSlices[s.categoryId] ?: 0L)
                                }
                                Text(
                                    // Bare figure: the donut's own centre names the
                                    // currency directly above, so printing "OMR" again
                                    // on all six rows is six readings of one fact.
                                    Money.formatAmount(s.amountMinor, currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${(s.fraction * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open ${cat.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (slices.isEmpty()) {
                            Text(
                                if (donutType == TxnType.EXPENSE) "No spending recorded for this period."
                                else "No income recorded for this period.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ── cashflow bars and the running total, two views of the same period
                ChartsCard(flow = flow, trend = trend, currency = currency)

                // ── budget pacing, when there is a plan to pace against
                budgetProgress?.let { progress ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Budget pacing", style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append((progress.elapsedFraction * 100).roundToInt())
                                    append("% of \"")
                                    append(progress.plan.label)
                                    append("\" gone, ")
                                    append((progress.fraction * 100).roundToInt())
                                    append("% of the budget spent")
                                    if (progress.over) append(" · over")
                                    else if (progress.aheadOfPace) append(" · running ahead")
                                    else append(" · on track")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    progress.over -> MaterialTheme.colorScheme.error
                                    progress.aheadOfPace -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            progress.lines.forEach { line ->
                                com.alyaqdhan.riyal.ui.compose.BudgetBar(
                                    categoryId = line.categoryId,
                                    spent = line.spentMinor,
                                    budget = line.capMinor,
                                    currency = currency,
                                    paceFraction = progress.elapsedFraction,
                                )
                            }
                        }
                    }
                }

                // ── what changed most since last period
                if (movers.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Biggest movers", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Against the " + countOf(slice.lengthDays.toInt(), "day") + " before this period.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            movers.forEach { mover ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onOpenCategory(mover.categoryId, slice) }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(Categories.colorFor(mover.categoryId))),
                                    )
                                    Text(
                                        Categories.byId(mover.categoryId).name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val up = mover.deltaMinor > 0
                                    Text(
                                        (if (up) "▲ " else "▼ ") + Money.format(abs(mover.deltaMinor), currency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (up) MaterialTheme.colorScheme.error else successColor(),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── what is going to happen again
                if (recurring.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Looks recurring", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Same merchant, steady amount, steady rhythm, so it will very " +
                                    "likely land again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            recurring.forEach { r ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    com.alyaqdhan.riyal.ui.compose.CategoryIcon(r.categoryId, size = 18.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(r.merchant, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "every ${cadenceLabel(r.intervalDays)} · ${r.occurrences} so far · " +
                                                "next around ${nextDueFmt.format(
                                                    Instant.ofEpochMilli(r.nextAtMillis).atZone(ZoneId.systemDefault())
                                                )}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        Money.format(r.typicalMinor, currency),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── the numbers behind the total
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Statistics", style = MaterialTheme.typography.titleMedium)
                        InsightRow(
                            R.drawable.ic_insight_bolt, MaterialShapes.SoftBurst, "Biggest expense",
                            biggest?.let {
                                "${it.merchant ?: Categories.byId(it.categoryId).name} · ${Money.format(it.amountMinor, it.currency)}"
                            } ?: "none yet",
                        )
                        InsightRow(
                            R.drawable.ic_insight_calendar, MaterialShapes.Clover4Leaf, "Heaviest day",
                            spread.busiestDayMillis?.let {
                                "${dayFmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))} · " +
                                    Money.format(spread.busiestDayMinor, currency)
                            } ?: "none yet",
                        )
                        InsightRow(
                            R.drawable.ic_insight_store, MaterialShapes.Cookie9Sided, "A normal payment",
                            if (spread.payments == 0) "none yet" else
                                "${Money.format(spread.medianMinor, currency)} · " +
                                    "average ${Money.format(spread.averageMinor, currency)}",
                        )
                        // The rest as plain figures: they are read, not compared, and
                        // one shape each would turn a card into a list of badges.
                        StatLine("Payments", "${spread.payments} out · ${spread.deposits} in")
                        StatLine(
                            "Days with spending",
                            "${spread.activeDays} of ${spread.periodDays}",
                        )
                        StatLine(
                            "Average per day",
                            Money.format(
                                Stats.avgSpentPerDayIn(totals.spent, slice.start, slice.endExclusive),
                                currency,
                            ),
                        )
                        // Said in whichever direction is true: "kept −92%" is a riddle,
                        // "spent 92% beyond what came in" is the same fact, read once.
                        spread.savedFraction(totals.received, totals.spent)?.let { saved ->
                            if (saved >= 0f) {
                                StatLine(
                                    "Kept of what came in",
                                    "${(saved * 100).roundToInt()}%",
                                    color = successColor(),
                                )
                            } else {
                                StatLine(
                                    "Spent beyond what came in",
                                    "${(-saved * 100).roundToInt()}%",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (totals.otherCurrencyCount > 0) {
                            Text(
                                "Charts show $currency only, " + countOf(totals.otherCurrencyCount, "transaction") + " in other currencies are listed in Activity.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Room for the floating toolbar hovering over the content.
                ToolbarSpacer()
            }
        }
    }
}

/**
 * Cashflow bars and the cumulative line, tabbed rather than stacked: they answer
 * different questions about the same period ("when did it happen" vs "how did it add
 * up"), and showing both at once buries whichever one you came for.
 */
@Composable
private fun ChartsCard(
    flow: List<Stats.CashflowPoint>,
    trend: List<Stats.TrendPoint>,
    currency: String,
) {
    var showTrend by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !showTrend,
                    onClick = { showTrend = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.weight(1f),
                ) { Text("Cashflow", softWrap = false, maxLines = 1) }
                SegmentedButton(
                    selected = showTrend,
                    onClick = { showTrend = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.weight(1f),
                ) { Text("Running total", softWrap = false, maxLines = 1) }
            }
            Text(
                if (showTrend) {
                    "Running totals: steep red = heavy spending days, the gap to green is what's left."
                } else {
                    "Money in against money out, side by side, so a lean stretch is obvious."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val enough = if (showTrend) trend.size >= 2 else flow.isNotEmpty()
            if (!enough) {
                Text(
                    "Not enough activity in this period to draw it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (showTrend) {
                val spentVals = remember(trend) { trend.map { it.spentCumulative } }
                val receivedVals = remember(trend) { trend.map { it.receivedCumulative } }
                val labels = remember(trend) { trend.map { it.label } }
                val producer = remember { CartesianChartModelProducer() }
                LaunchedEffect(spentVals, receivedVals, labels) {
                    producer.runTransaction {
                        lineModel {
                            series(spentVals)
                            series(receivedVals)
                        }
                        extras { it[ChartLabelsKey] = labels }
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
                            valueFormatter = amountAxisFormatter(currency),
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = labelAxisFormatter(),
                        ),
                    ),
                    modelProducer = producer,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            } else {
                val spentVals = remember(flow) { flow.map { it.spent } }
                val receivedVals = remember(flow) { flow.map { it.received } }
                val labels = remember(flow) { flow.map { it.label } }
                val producer = remember { CartesianChartModelProducer() }
                LaunchedEffect(spentVals, receivedVals, labels) {
                    producer.runTransaction {
                        columnModel {
                            series(spentVals)
                            series(receivedVals)
                        }
                        extras { it[ChartLabelsKey] = labels }
                    }
                }
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(
                            ColumnCartesianLayer.ColumnProvider.series(
                                rememberLineComponent(
                                    fill = Fill(MaterialTheme.colorScheme.error),
                                    thickness = 10.dp,
                                ),
                                rememberLineComponent(
                                    fill = Fill(successColor()),
                                    thickness = 10.dp,
                                ),
                            ),
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = amountAxisFormatter(currency),
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = labelAxisFormatter(),
                        ),
                    ),
                    modelProducer = producer,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(MaterialTheme.colorScheme.error, "money out")
                LegendDot(successColor(), "money in")
            }
        }
    }
}

/** Axis labels in whole units: minor units would make every tick unreadable. */
private fun amountAxisFormatter(currency: String) = CartesianValueFormatter { _, y, _ ->
    Money.toMajor(y.toLong(), currency).toBigInteger().toString()
}

private fun labelAxisFormatter() = CartesianValueFormatter { context, x, _ ->
    context.model.extraStore[ChartLabelsKey].getOrNull(x.toInt()) ?: ""
}

/**
 * The gap between two neighbouring arcs, in degrees. Butt caps and a hairline of
 * background between the colours read as separate slices; rounded caps would have to
 * be pushed several times this far apart before they stopped overlapping each other.
 */
private const val DonutGapDegrees = 2f

/** One arc of the donut: a stock determinate ring, turned to where its share starts. */
@Composable
private fun DonutRing(
    progress: () -> Float,
    color: Color,
    trackColor: Color,
    rotationDegrees: Float = 0f,
) {
    CircularProgressIndicator(
        progress = progress,
        modifier = Modifier
            .size(210.dp)
            .rotate(rotationDegrees),
        color = color,
        strokeWidth = 16.dp,
        trackColor = trackColor,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
    )
}

/**
 * One headline figure with how it compares to the equally long period before.
 *
 * The currency sits under the number rather than in front of it: three tiles side by
 * side leave each one about a third of the screen, and "OMR " ahead of the digits was
 * spending that width on a word that is the same on all three.
 */
@Composable
private fun SummaryTile(
    label: String,
    amount: Long,
    previous: Long,
    currency: String,
    upIsGood: Boolean,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // The currency used to have a line of its own in all three tiles - one line of
        // height each to say the same three letters. It shares the label's line, which
        // has room to spare, rather than the figure's, which does not: a negative Net
        // is wide enough that "OMR" beside it was cut off.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                currency,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (signed && amount < 0) "− " else "") + Money.formatAmount(abs(amount), currency),
            style = MaterialTheme.typography.titleMedium,
            color = if (signed && amount < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            maxLines = 1,
        )
        DeltaText(amount, previous, upIsGood = upIsGood)
    }
}

/**
 * "▲ 18%" against the previous period, coloured by whether that direction is welcome -
 * spending more is red, earning more is green. Silent when there is no baseline, since
 * a percentage change from nothing is not a fact.
 */
@Composable
private fun DeltaText(now: Long, previous: Long, upIsGood: Boolean = false) {
    val pct = Stats.deltaPct(now, previous)
    if (pct == null || abs(pct) < 0.005f) {
        Text(
            if (pct == null) "no baseline" else "level",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val up = pct > 0
    val good = up == upIsGood
    Text(
        (if (up) "▲ " else "▼ ") + "${(abs(pct) * 100).roundToInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = if (good) successColor() else MaterialTheme.colorScheme.error,
    )
}

private fun cadenceLabel(days: Int): String = when (days) {
    in 6..8 -> "week"
    in 13..16 -> "2 weeks"
    in 26..35 -> "month"
    in 85..95 -> "3 months"
    in 355..375 -> "year"
    else -> "$days days"
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

/** A plain "label … value" row, for figures that are read rather than compared. */
@Composable
private fun StatLine(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        )
    }
}

@Composable
private fun InsightRow(
    @DrawableRes iconRes: Int,
    shape: RoundedPolygon,
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Each insight sits on its own imperfect M3 shape, like the category badges.
        Box(
            Modifier
                .size(38.dp)
                .clip(shape.toShape())
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
