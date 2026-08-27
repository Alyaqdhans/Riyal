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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryBadge
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.compose.TxnEditSheet
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.popIn
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
fun CategoryDetailScreen(vm: MainViewModel, categoryId: String, onBack: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    val category = remember(categoryId) { Categories.byId(categoryId) }
    var slice by remember { mutableStateOf(TimeSlice.thisMonth()) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var confirmRemove by remember { mutableStateOf<Txn?>(null) }

    val inCategory = remember(txns, categoryId, slice) {
        txns.filter { it.categoryId == categoryId && slice.contains(it.atMillis) }
            .sortedByDescending { it.atMillis }
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
        topBar = {
            TopAppBar(
                title = { Text(category.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "period") {
                PeriodBar(slice = slice, onChange = { slice = it }, txns = txns)
            }

            item(key = "summary") {
                Card(Modifier.fillMaxWidth().popIn()) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(categoryId, size = 56.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                Money.format(total, currency),
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (category.income) successColor() else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${inCategory.size} record(s) in ${slice.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ComparisonLine(total, previousTotal, currency)
                        }
                    }
                }
            }

            if (merchants.isNotEmpty()) {
                item(key = "merchants-title") { SectionTitle("Where it went") }
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
                        Text(Money.format(amount, currency), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item(key = "records-title") { SectionTitle("Records") }
            if (inCategory.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        style = FaceStyle.SLEEPY,
                        title = "Nothing here in ${slice.label}",
                        subtitle = "Step back a period, or pick a wider one from the title above.",
                    )
                }
            } else {
                items(inCategory, key = { it.id }) { txn ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TxnRow(
                            txn,
                            onClick = { editing = txn },
                            modifier = Modifier.weight(1f),
                            accounts = accounts,
                        )
                        IconButton(onClick = { confirmRemove = txn }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove this transaction",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
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

    confirmRemove?.let { txn ->
        RemoveTxnDialog(
            txn = txn,
            onConfirm = {
                vm.ignoreTxn(txn)
                confirmRemove = null
            },
            onDismiss = { confirmRemove = null },
        )
    }
}

/** "up 18% on the period before", or an honest silence when there's nothing to compare. */
@Composable
private fun ComparisonLine(now: Long, before: Long, currency: String) {
    if (before <= 0L && now <= 0L) return
    val pct = Stats.deltaPct(now, before)
    val text = when {
        pct == null -> "nothing in the period before"
        abs(pct) < 0.005f -> "level with the period before"
        pct > 0 -> "up ${(pct * 100).roundToInt()}% on ${Money.format(before, currency)} before"
        else -> "down ${(-pct * 100).roundToInt()}% from ${Money.format(before, currency)} before"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Shared confirmation for "this isn't a real transaction". */
@Composable
fun RemoveTxnDialog(txn: Txn, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove this transaction?") },
        text = {
            Text(
                when {
                    txn.manual -> "This was added manually. It will be deleted."
                    txn.isTransfer ->
                        "This transfer stands for two messages, and both will be removed and " +
                            "kept out of future scans. Your SMS inbox is untouched."
                    else ->
                        "The app read this from an SMS but you're saying it isn't a real " +
                            "transaction. It'll be removed and kept out of future scans. " +
                            "Your SMS inbox is untouched."
                }
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
