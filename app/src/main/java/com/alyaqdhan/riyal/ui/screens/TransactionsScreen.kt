@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryPickerSheet
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.ManualTxnDialog
import com.alyaqdhan.riyal.ui.compose.ScanSheetHost
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.dayLabel
import com.alyaqdhan.riyal.ui.compose.localDateOf
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun TransactionsScreen(vm: MainViewModel, onExport: () -> Unit) {
    val txns by vm.txns.collectAsState()
    var filter by rememberSaveable { mutableStateOf("all") }
    var picker by remember { mutableStateOf<Txn?>(null) }
    var showManual by remember { mutableStateOf(false) }

    val filtered = remember(txns, filter) {
        if (filter == "all") txns else txns.filter { it.categoryId == filter }
    }
    val categoriesPresent = remember(txns) {
        Categories.ALL.filter { cat -> txns.any { it.categoryId == cat.id } }
    }
    val grouped = remember(filtered) { filtered.groupBy { localDateOf(it.atMillis) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                actions = {
                    IconButton(onClick = { vm.startScan() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Scan messages")
                    }
                    IconButton(onClick = { showManual = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add manually")
                    }
                    IconButton(onClick = onExport, enabled = txns.isNotEmpty()) {
                        Icon(Icons.Filled.Share, contentDescription = "Export CSV")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filter == "all",
                        onClick = { filter = "all" },
                        label = { Text("All (${txns.size})") },
                    )
                }
                items(categoriesPresent) { cat ->
                    FilterChip(
                        selected = filter == cat.id,
                        onClick = { filter = if (filter == cat.id) "all" else cat.id },
                        label = { Text("${cat.emoji} ${cat.name}") },
                    )
                }
            }
            if (filtered.isEmpty()) {
                EmptyState(
                    style = FaceStyle.SLEEPY,
                    title = "No transactions here",
                    subtitle = "Scan your messages, add one manually with +, or change the filter.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { (date, dayTxns) ->
                        item(key = "header-$date") { DayHeader(date, dayTxns) }
                        items(dayTxns, key = { it.id }) { txn ->
                            TxnRow(txn, onClick = { picker = txn }, modifier = Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }

    ScanSheetHost(vm)
    picker?.let { txn ->
        CategoryPickerSheet(
            txn = txn,
            onApply = { categoryId, rulePattern ->
                vm.setCategory(txn, categoryId, rulePattern)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
    if (showManual) {
        ManualTxnDialog(
            title = "Add transaction",
            atMillis = System.currentTimeMillis(),
            defaultCurrency = vm.prefs.defaultCurrency,
            onSave = { amountMinor, currency, direction, merchant, categoryId ->
                vm.addManual(amountMinor, currency, direction, merchant, categoryId)
                showManual = false
            },
            onDismiss = { showManual = false },
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, dayTxns: List<Txn>) {
    val spentByCurrency = dayTxns
        .filter { it.direction == Direction.EXPENSE }
        .groupBy { it.currency }
        .map { (currency, list) -> "− ${Money.format(list.sumOf { it.amountMinor }, currency)}" }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            dayLabel(date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (spentByCurrency.isNotEmpty()) {
            Text(
                spentByCurrency.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
