@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import com.alyaqdhan.riyal.ui.theme.onSuccessContainer
import com.alyaqdhan.riyal.ui.theme.successContainer
import com.alyaqdhan.riyal.ui.compose.iconFor
import com.alyaqdhan.riyal.ui.compose.typeTone
import com.alyaqdhan.riyal.ui.compose.typeIcon
import com.alyaqdhan.riyal.ui.compose.typeLabel
import com.alyaqdhan.riyal.ui.compose.ConnectedChoice
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.compose.MoneySign
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.compose.MoneyAwareText
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryWheel
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.bidi
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.ToolbarSpacer
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
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
private val nextDueFmt: DateTimeFormatter get() = Dates.pattern("d MMM")
private val dayFmt: DateTimeFormatter get() = Dates.pattern("d MMM")

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

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.an_title)) }) },
    ) { padding ->
        ScanRefresh(vm, Modifier.padding(padding)) {
        Column {
            if (txns.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EmptyState(
                        title = stringResource(R.string.an_empty_title),
                        subtitle = stringResource(R.string.an_empty_subtitle),
                    )
                }
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
                            label = { Text(stringResource(R.string.an_all_accounts)) },
                        )
                    }
                    items(accounts) { acc ->
                        FilterChip(
                            selected = accountId == acc.id,
                            onClick = { accountId = if (accountId == acc.id) null else acc.id },
                            label = { Text(acc.displayName) },
                            leadingIcon = {
                                Icon(painterResource(iconFor(acc.kind)), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
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

                // ── the headline numbers, each against the period before: net large, as
                // Home has it, and what went out and came in under it in Home's colours.
                NetCard(totals.net, previous.net, currency)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindTile(TxnType.EXPENSE, totals.spent, previous.spent, currency, Modifier.weight(1f))
                    KindTile(TxnType.INCOME, totals.received, previous.received, currency, Modifier.weight(1f))
                }
                if (moved > 0) {
                    MoneyAwareText(
                        stringResource(
                            R.string.an_moved_note,
                            bidi(Money.format(moved, currency)),
                        ),
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
                        ConnectedChoice(
                            options = listOf(TxnType.EXPENSE, TxnType.INCOME),
                            selected = donutType,
                            onSelect = { donutType = it },
                            label = { stringResource(typeLabel(it)) },
                            icon = ::typeIcon,
                            tone = { typeTone(it) },
                        )
                        // Restored to M3's own wavy indicator, held flat: one arc per category.
                        CategoryWheel(slices = slices) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(
                                        if (donutType == TxnType.EXPENSE) R.string.an_donut_spent
                                        else R.string.an_donut_received
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MoneyText(
                                    if (donutType == TxnType.EXPENSE) totals.spent
                                    else totals.received,
                                    currency,
                                    sign = MoneySign.NONE,
                                    roll = true,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    if (slices.isEmpty()) {
                                        stringResource(R.string.an_donut_nothing)
                                    } else {
                                        counted(R.plurals.count_categories, slices.size)
                                    },
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
                                    .padding(vertical = 4.dp),
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
                                MoneyText(
                                    s.amountMinor,
                                    currency,
                                    sign = MoneySign.NONE,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(
                                        R.string.an_percent,
                                        (s.fraction * 100).roundToInt(),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.cats_open, cat.name),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (slices.isEmpty()) {
                            Text(
                                stringResource(
                                    if (donutType == TxnType.EXPENSE) R.string.an_no_spending
                                    else R.string.an_no_income
                                ),
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
                            Text(
                                stringResource(R.string.an_pacing),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.an_pacing_line,
                                    (progress.elapsedFraction * 100).roundToInt(),
                                    progress.plan.label,
                                    (progress.fraction * 100).roundToInt(),
                                ) + stringResource(
                                    when {
                                        progress.over -> R.string.an_pacing_over
                                        progress.aheadOfPace -> R.string.an_pacing_ahead
                                        else -> R.string.an_pacing_on_track
                                    }
                                ),
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
                            Text(
                                stringResource(R.string.an_movers),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.an_movers_against,
                                    counted(R.plurals.count_days, slice.lengthDays.toInt()),
                                ),
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
                                    MoneyAwareText(
                                        stringResource(
                                            if (up) R.string.an_up_amount
                                            else R.string.an_down_amount,
                                            bidi(Money.format(abs(mover.deltaMinor), currency)),
                                        ),
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
                            Text(
                                stringResource(R.string.an_recurring),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.an_recurring_note),
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
                                            stringResource(
                                                R.string.an_recurring_line,
                                                cadenceLabel(r.intervalDays),
                                                r.occurrences,
                                                nextDueFmt.format(
                                                    Instant.ofEpochMilli(r.nextAtMillis)
                                                        .atZone(ZoneId.systemDefault())
                                                ),
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    MoneyText(r.typicalMinor, currency,
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
                        Text(
                            stringResource(R.string.an_stats),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        InsightRow(
                            R.drawable.ic_insight_bolt,
                            stringResource(R.string.an_biggest_expense),
                            biggest?.let {
                                stringResource(
                                    R.string.an_named_amount,
                                    it.merchant ?: Categories.byId(it.categoryId).name,
                                    bidi(Money.format(it.amountMinor, it.currency)),
                                )
                            } ?: stringResource(R.string.an_none_yet),
                        )
                        InsightRow(
                            R.drawable.ic_insight_calendar,
                            stringResource(R.string.an_heaviest_day),
                            spread.busiestDayMillis?.let {
                                stringResource(
                                    R.string.an_named_amount,
                                    dayFmt.format(
                                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                                    ),
                                    bidi(Money.format(spread.busiestDayMinor, currency)),
                                )
                            } ?: stringResource(R.string.an_none_yet),
                        )
                        InsightRow(
                            R.drawable.ic_insight_store,
                            stringResource(R.string.an_normal_payment),
                            if (spread.payments == 0) {
                                stringResource(R.string.an_none_yet)
                            } else {
                                stringResource(
                                    R.string.an_median_average,
                                    bidi(Money.format(spread.medianMinor, currency)),
                                    bidi(Money.format(spread.averageMinor, currency)),
                                )
                            },
                        )
                        // The rest as plain figures: they are read, not compared, and
                        // one shape each would turn a card into a list of badges.
                        StatLine(
                            stringResource(R.string.an_payments),
                            stringResource(
                                R.string.an_payments_value,
                                spread.payments,
                                spread.deposits,
                            ),
                        )
                        StatLine(
                            stringResource(R.string.an_days_with_spending),
                            stringResource(
                                R.string.an_days_value,
                                spread.activeDays,
                                spread.periodDays,
                            ),
                        )
                        StatLine(
                            stringResource(R.string.an_average_per_day),
                            bidi(
                                Money.format(
                                    Stats.avgSpentPerDayIn(
                                        totals.spent,
                                        slice.start,
                                        slice.endExclusive,
                                    ),
                                    currency,
                                )
                            ),
                        )
                        // Said in whichever direction is true: "kept −92%" is a riddle,
                        // "spent 92% beyond what came in" is the same fact, read once.
                        spread.savedFraction(totals.received, totals.spent)?.let { saved ->
                            if (saved >= 0f) {
                                StatLine(
                                    stringResource(R.string.an_kept),
                                    stringResource(
                                        R.string.an_percent,
                                        (saved * 100).roundToInt(),
                                    ),
                                    color = successColor(),
                                )
                            } else {
                                StatLine(
                                    stringResource(R.string.an_overspent),
                                    stringResource(
                                        R.string.an_percent,
                                        (-saved * 100).roundToInt(),
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (totals.otherCurrencyCount > 0) {
                            Text(
                                stringResource(
                                    R.string.an_other_currencies,
                                    currency,
                                    counted(
                                        R.plurals.count_transactions,
                                        totals.otherCurrencyCount,
                                    ),
                                ),
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
            ConnectedChoice(
                options = listOf(false, true),
                selected = showTrend,
                onSelect = { showTrend = it },
                label = { stringResource(if (it) R.string.an_running_total else R.string.an_cashflow) },
                tone = { MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer },
            )
            Text(
                stringResource(
                    if (showTrend) R.string.an_running_total_note else R.string.an_cashflow_note
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val enough = if (showTrend) trend.size >= 2 else flow.isNotEmpty()
            if (!enough) {
                Text(
                    stringResource(R.string.an_not_enough),
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
                    // Drawn where it lands: no growing bars, no lines sweeping in.
                    animationSpec = null,
                    animateIn = false,
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
                    // Drawn where it lands: no growing bars, no lines sweeping in.
                    animationSpec = null,
                    animateIn = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(MaterialTheme.colorScheme.error, stringResource(R.string.an_legend_out))
                LegendDot(successColor(), stringResource(R.string.an_legend_in))
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
 * One headline figure with how it compares to the equally long period before.
 *
 * The currency sits under the number rather than in front of it: three tiles side by
 * side leave each one about a third of the screen, and "OMR " ahead of the digits was
 * spending that width on a word that is the same on all three.
 */
/** The period's net, large and bold, with how it compares to the period before. */
@Composable
private fun NetCard(net: Long, previous: Long, currency: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.home_net),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(
                net,
                currency,
                sign = MoneySign.NEGATIVE,
                roll = true,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = if (net < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = MaterialTheme.typography.headlineMedium.fontSize),
            )
            DeltaPill(net, previous, upIsGood = true)
        }
    }
}

/**
 * What went out, or came in: on the same card as net, with only the figure and its arrow
 * in the side's colour - red out, green in - and the trend in its pill.
 */
@Composable
private fun KindTile(type: TxnType, amount: Long, previous: Long, currency: String, modifier: Modifier) {
    val ink = if (type == TxnType.EXPENSE) MaterialTheme.colorScheme.error else successColor()
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(typeIcon(type)), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(typeLabel(type)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            MoneyText(
                amount,
                currency,
                sign = MoneySign.NONE,
                roll = true,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ink,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
            )
            DeltaPill(amount, previous, upIsGood = type == TxnType.INCOME)
        }
    }
}

/**
 * "▲ 18%" against the previous period, on a small pill coloured by whether that
 * direction is welcome - spending more is red, earning more is green. A plain pill
 * when there is no baseline, since a percentage change from nothing is not a fact.
 */
@Composable
private fun DeltaPill(now: Long, previous: Long, upIsGood: Boolean) {
    val pct = Stats.deltaPct(now, previous)
    val flat = pct == null || abs(pct) < 0.005f
    val good = !flat && (pct!! > 0) == upIsGood
    val (container, content) = when {
        flat -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        good -> successContainer() to onSuccessContainer()
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            when {
                pct == null -> stringResource(R.string.an_no_baseline)
                flat -> stringResource(R.string.an_level)
                pct > 0 -> stringResource(R.string.an_delta_up, (abs(pct) * 100).roundToInt())
                else -> stringResource(R.string.an_delta_down, (abs(pct) * 100).roundToInt())
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
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
            stringResource(if (pct == null) R.string.an_no_baseline else R.string.an_level),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val up = pct > 0
    val good = up == upIsGood
    Text(
        stringResource(
            if (up) R.string.an_delta_up else R.string.an_delta_down,
            (abs(pct) * 100).roundToInt(),
        ),
        style = MaterialTheme.typography.labelSmall,
        color = if (good) successColor() else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun cadenceLabel(days: Int): String = when (days) {
    in 6..8 -> stringResource(R.string.cadence_week)
    in 13..16 -> stringResource(R.string.cadence_fortnight)
    in 26..35 -> stringResource(R.string.cadence_month)
    in 85..95 -> stringResource(R.string.cadence_quarter)
    in 355..375 -> stringResource(R.string.cadence_year)
    else -> counted(R.plurals.count_days, days)
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
        MoneyAwareText(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        )
    }
}

@Composable
private fun InsightRow(
    @DrawableRes iconRes: Int,
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
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
            MoneyAwareText(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
