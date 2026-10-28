@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.compose.MoneyAwareText
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryBadge
import com.alyaqdhan.riyal.ui.compose.bidi
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.SortChip
import com.alyaqdhan.riyal.ui.compose.SwipeableTxnRow
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.compose.title
import com.alyaqdhan.riyal.ui.compose.TxnEditSheet
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.TxnSort
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import com.alyaqdhan.riyal.ui.theme.successColor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Everything filed under one category for a period: what it came to, how that compares
 * with the period before, which merchants drove it, and every record behind the number.
 *
 * Tapping a row re-files it, which is how a misclassified message gets fixed right
 * where you notice it - the reason this page exists rather than a chart tooltip.
 */
@Composable
fun CategoryDetailScreen(
    vm: MainViewModel,
    categoryId: String,
    onBack: () -> Unit,
    initialSlice: TimeSlice? = null,
) {
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categoryUse by vm.categoryUse.collectAsState()
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    val category = remember(categoryId) { Categories.byId(categoryId) }
    // The period the caller was reading, not today's: this page is opened by tapping a
    // figure that belongs to a month, and answering for a different one is a wrong answer.
    var slice by remember { mutableStateOf(initialSlice ?: TimeSlice.thisMonth()) }
    var sort by rememberSaveable { mutableStateOf(TxnSort.NEWEST.name) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val archivedIds by vm.archivedIds.collectAsState()
    val askEachTime by vm.askEachTime.collectAsState()

    val order = TxnSort.valueOf(sort)
    val inCategory = remember(txns, categoryId, slice, order) {
        order.applyTo(txns.filter { it.categoryId == categoryId && slice.contains(it.atMillis) })
    }
    val total = remember(inCategory, currency) {
        inCategory.filter { it.currency == currency }.sumOf { it.amountMinor }
    }
    val previousTotal = remember(txns, categoryId, slice, currency) {
        val (prevStart, prevEnd) = Stats.previousWindow(slice.start, slice.endExclusive)
        Stats.categoryTotalIn(txns, categoryId, prevStart, prevEnd, currency)
    }
    val merchants = remember(inCategory) {
        inCategory.filter { !it.merchant.isNullOrBlank() && it.currency == currency }
            .groupBy { it.merchant!!.trim() }
            .map { (m, list) -> m to list.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }
            .take(5)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(category.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = { SortChip(current = order, onSelect = { sort = it.name }) },
            )
        },
    ) { padding ->
        ScanRefresh(vm, Modifier.padding(padding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "period") {
                PeriodBar(slice = slice, onChange = { slice = it }, txns = txns)
            }

            item(key = "summary") {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(categoryId, size = 56.dp)
                        Column(Modifier.weight(1f)) {
                            MoneyText(total, currency,
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (category.income) successColor() else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(
                                    R.string.cat_detail_in_period,
                                    counted(R.plurals.count_records, inCategory.size),
                                    slice.title(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ComparisonLine(total, previousTotal, currency)
                        }
                    }
                }
            }

            if (merchants.isNotEmpty()) {
                item(key = "merchants-title") {
                    SectionTitle(stringResource(R.string.cat_detail_where))
                }
                items(merchants, key = { "m-" + it.first }) { (merchant, amount) ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            merchant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        MoneyText(amount, currency,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item(key = "records-title") {
                SectionTitle(stringResource(R.string.cat_detail_records))
            }
            if (inCategory.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        style = FaceStyle.SLEEPY,
                        title = stringResource(R.string.cat_detail_empty_title, slice.title()),
                        subtitle = stringResource(R.string.cat_detail_empty_subtitle),
                    )
                }
            } else {
                items(inCategory, key = { it.id }) { txn ->
                    // Swipe, like every other transaction list: the little × next to
                    // each row was permanent deletion one mis-tap away.
                    SwipeableTxnRow(
                        archived = txn.id in archivedIds,
                        onArchive = { archiveWithUndo(vm, snackbar, scope, txn, txn.id !in archivedIds) },
                        onDelete = { removeForGood(vm, snackbar, scope, txn) },
                        deleteLabel = deleteLabelFor(txn),
                    ) {
                        TxnRow(txn, onClick = { editing = txn }, accounts = accounts)
                    }
                }
            }
        }
        }
    }

    editing?.let { txn ->
        TxnEditSheet(
            txn = txn,
            accounts = accounts,
            onApply = { newCategoryId, rulePattern ->
                vm.setCategory(txn, newCategoryId, rulePattern)
                editing = null
            },
            onDismiss = { editing = null },
            rememberByDefault = vm.prefs.smartRules,
            categoryUse = categoryUse,
            askEachTime = askEachTime,
            onAskEachTime = { vm.setAskEachTime(txn.merchant.orEmpty(), it) },
            onSetAccount = {
                vm.setTxnAccount(txn, it)
                editing = null
            },
            onMarkTransfer = { from, to ->
                vm.markAsTransfer(txn, from, to)
                editing = null
            },
            onSplitTransfer = {
                vm.splitTransfer(txn)
                editing = null
            },
        )
    }

}

/** "up 18% on the period before", or an honest silence when there's nothing to compare. */
@Composable
private fun ComparisonLine(now: Long, before: Long, currency: String) {
    if (before <= 0L && now <= 0L) return
    val pct = Stats.deltaPct(now, before)
    val text = when {
        pct == null -> stringResource(R.string.compare_nothing_before)
        abs(pct) < 0.005f -> stringResource(R.string.compare_level)
        pct > 0 -> stringResource(
            R.string.compare_up,
            (pct * 100).roundToInt(),
            bidi(Money.format(before, currency)),
        )
        else -> stringResource(
            R.string.compare_down,
            (-pct * 100).roundToInt(),
            bidi(Money.format(before, currency)),
        )
    }
    MoneyAwareText(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
