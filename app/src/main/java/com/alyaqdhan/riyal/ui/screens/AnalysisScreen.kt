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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryBadge
import com.alyaqdhan.riyal.ui.compose.CategoryChips
import com.alyaqdhan.riyal.ui.compose.CategoryPickerSheet
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.TxnRow
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
    // Tapping a category legend row opens its transactions for this slice, to fix wrong ones.
    var drillCategoryId by remember { mutableStateOf<String?>(null) }
    // A row inside the drill-down opens the shared category picker to re-categorize it.
    var recategorize by remember { mutableStateOf<Txn?>(null) }
    // Or the user marks the whole transaction as not real; confirm before removing.
    var confirmRemove by remember { mutableStateOf<Txn?>(null) }
    // Per-category monthly budgets live in Prefs; this local mirror re-renders the bars
    // as the user edits them in the dialog below.
    var budgets by remember { mutableStateOf(vm.prefs.categoryBudgets) }
    var showBudgetEditor by remember { mutableStateOf(false) }

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
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { drillCategoryId = cat.id }
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
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Review ${cat.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
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
                            R.drawable.ic_insight_store, MaterialShapes.Cookie9Sided, "Top merchant",
                            topMerchant?.let { "${it.first} · ${Money.format(it.second, currency)}" } ?: "none yet",
                        )
                        InsightRow(
                            R.drawable.ic_insight_bolt, MaterialShapes.SoftBurst, "Biggest expense",
                            biggest?.let {
                                "${it.merchant ?: Categories.byId(it.categoryId).name} · ${Money.format(it.amountMinor, it.currency)}"
                            } ?: "none yet",
                        )
                        InsightRow(
                            R.drawable.ic_insight_calendar, MaterialShapes.Clover4Leaf, "Average per day",
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
                // Budgets: only meaningful against a single calendar month, since a
                // budget is a monthly cap. For other slices, offer the editor only.
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Category budgets", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { showBudgetEditor = true }) { Text("Edit") }
                        }
                        if (slice.month == null) {
                            Text(
                                "Pick a single month to see budget progress. Budgets are monthly caps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (budgets.isEmpty()) {
                            Text(
                                "No category budgets yet. Tap Edit to cap a category's monthly spend.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            budgets.entries
                                .sortedByDescending { it.value }
                                .forEach { (catId, budget) ->
                                    val spent = Stats.categorySpent(txns, catId, slice.month!!, currency)
                                    BudgetBar(catId, spent, budget, currency)
                                }
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

    drillCategoryId?.let { catId ->
        val cat = Categories.byId(catId)
        // The exact transactions behind this slice of the donut, newest first.
        val inCategory = remember(txns, catId, slice) {
            txns.filter {
                it.categoryId == catId &&
                    it.atMillis >= slice.start && it.atMillis < slice.endExclusive
            }.sortedByDescending { it.atMillis }
        }
        CategoryTxnsSheet(
            categoryId = catId,
            title = cat.name,
            txns = inCategory,
            onTxnClick = { recategorize = it },
            onTxnRemove = { confirmRemove = it },
            onDismiss = { drillCategoryId = null },
        )
    }

    confirmRemove?.let { txn ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove this transaction?") },
            text = {
                Text(
                    if (txn.manual) {
                        "This was added manually. It will be deleted."
                    } else {
                        "The app read this from an SMS but you're saying it isn't a real " +
                            "transaction. It'll be removed and kept out of future scans. " +
                            "Your SMS inbox is untouched."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.ignoreTxn(txn)
                    confirmRemove = null
                    drillCategoryId = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            },
        )
    }

    if (showBudgetEditor) {
        BudgetEditorDialog(
            currency = currency,
            initial = budgets,
            onSave = { catId, minor ->
                vm.prefs.setCategoryBudget(catId, minor)
                budgets = vm.prefs.categoryBudgets
            },
            onDismiss = { showBudgetEditor = false },
        )
    }

    recategorize?.let { txn ->
        CategoryPickerSheet(
            txn = txn,
            onApply = { categoryId, rulePattern ->
                vm.setCategory(txn, categoryId, rulePattern)
                recategorize = null
                // The transaction left this category; drop back to the charts so the
                // list can't show a now-stale membership.
                drillCategoryId = null
            },
            onDismiss = { recategorize = null },
            rememberByDefault = vm.prefs.smartRules,
        )
    }
}

/**
 * Drill-down from the Analysis donut: every transaction the app filed under one
 * category for the selected period. Tapping a row re-categorizes it, which is how a
 * misclassified message gets corrected right where you spot it.
 */
@Composable
private fun CategoryTxnsSheet(
    categoryId: String,
    title: String,
    txns: List<Txn>,
    onTxnClick: (Txn) -> Unit,
    onTxnRemove: (Txn) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxHeight(0.82f)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryBadge(categoryId)
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${txns.size} transaction(s) · tap to recategorize, ✕ to remove a wrong one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(txns, key = { it.id }) { txn ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TxnRow(txn, onClick = { onTxnClick(txn) }, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onTxnRemove(txn) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove this transaction",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One category's month-to-date spend against its budget cap, with a colored bar. */
@Composable
private fun BudgetBar(categoryId: String, spent: Long, budget: Long, currency: String) {
    val cat = Categories.byId(categoryId)
    val fraction = if (budget > 0) (spent.toFloat() / budget.toFloat()) else 0f
    val over = spent > budget
    val barColor = when {
        over -> MaterialTheme.colorScheme.error
        fraction >= 0.85f -> MaterialTheme.colorScheme.tertiary
        else -> Color(Categories.colorFor(categoryId))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(Categories.colorFor(categoryId))),
            )
            Text(cat.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${Money.format(spent, currency)} / ${Money.format(budget, currency)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
        )
        if (over) {
            Text(
                "Over by ${Money.format(spent - budget, currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Set or clear a monthly cap per category: pick a category chip, type an amount,
 * add it. Existing budgets are listed with a remove control. Expense categories only,
 * since income isn't something you cap.
 */
@Composable
private fun BudgetEditorDialog(
    currency: String,
    initial: Map<String, Long>,
    onSave: (categoryId: String, minor: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(Categories.forDirection(Direction.EXPENSE).first().id) }
    var amount by remember { mutableStateOf("") }
    // Local view so the list updates live as budgets are added/removed.
    var current by remember { mutableStateOf(initial) }
    val parsed = amount.trim().replace(",", "").toBigDecimalOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category budgets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current.isNotEmpty()) {
                    current.entries.sortedByDescending { it.value }.forEach { (catId, minor) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${Categories.byId(catId).name}: ${Money.format(minor, currency)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                onSave(catId, 0L)
                                current = current - catId
                            }) { Text("Remove") }
                        }
                    }
                }
                Text("Add / change a budget", style = MaterialTheme.typography.labelLarge)
                CategoryChips(
                    direction = Direction.EXPENSE,
                    selectedId = selected,
                    onSelect = { selected = it },
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monthly cap") },
                    suffix = { Text(currency) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed.signum() > 0,
                onClick = {
                    val minor = Money.toMinor(parsed!!, currency)
                    onSave(selected, minor)
                    current = current + (selected to minor)
                    amount = ""
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
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
