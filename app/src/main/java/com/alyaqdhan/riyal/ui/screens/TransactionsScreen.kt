@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FilterSheet
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.ScanSheetHost
import com.alyaqdhan.riyal.ui.compose.SwipeableTxnRow
import com.alyaqdhan.riyal.ui.compose.TxnEditSheet
import com.alyaqdhan.riyal.ui.compose.toolbarSpace
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.SortChip
import com.alyaqdhan.riyal.ui.compose.TxnSort
import com.alyaqdhan.riyal.ui.compose.dayLabel
import com.alyaqdhan.riyal.ui.compose.localDateOf
import com.alyaqdhan.riyal.ui.compose.popIn
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun TransactionsScreen(vm: MainViewModel, onExport: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val scan by vm.scanState.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categoryUse by vm.categoryUse.collectAsState()
    val askEachTime by vm.askEachTime.collectAsState()
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var accountFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(TxnSort.NEWEST.name) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Txn?>(null) }
    val archivedIds by vm.archivedIds.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()


    val filtered = remember(txns, categoryFilter, typeFilter, accountFilter, archivedIds, showArchived) {
        txns.filter { t ->
            (categoryFilter == null || t.categoryId == categoryFilter) &&
                (typeFilter == null || t.type.name == typeFilter) &&
                (accountFilter == null || t.touches(accountFilter!!)) &&
                (showArchived == (t.id in archivedIds))
        }
    }
    val order = TxnSort.valueOf(sort)
    // How many of the sheet's filters are on, so the button can say so without
    // opening it.
    val hiddenFilterCount = listOfNotNull(categoryFilter, accountFilter).size
    val categoriesPresent = remember(txns) {
        Categories.ALL.filter { cat -> txns.any { it.categoryId == cat.id } }
    }
    // Sorted by date the list reads as days; sorted by size it reads as one ranking,
    // and day headers would just chop the ranking into meaningless pieces.
    val grouped = remember(filtered, order) {
        if (order.byDate) filtered.groupBy { localDateOf(it.atMillis) } else emptyMap()
    }
    val ranked = remember(filtered, order) {
        if (order.byDate) emptyList() else order.applyTo(filtered)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                actions = {
                    // Sort and Filters live here, not in the chip row: six controls in
                    // one row pushed the Transfers chip off the right edge of something
                    // that gives no sign it scrolls.
                    SortChip(current = order, onSelect = { sort = it.name })
                    TextButton(onClick = { showFilters = true }) {
                        Text(if (hiddenFilterCount > 0) "Filters ($hiddenFilterCount)" else "Filters")
                    }
                    IconButton(onClick = onExport, enabled = txns.isNotEmpty()) {
                        Icon(Icons.Filled.Share, contentDescription = "Export CSV")
                    }
                },
            )
        },
    ) { padding ->
        val ptrState = rememberPullToRefreshState()
        val refreshing = scan is MainViewModel.ScanState.Running
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.startScan(showSheet = false) },
            state = ptrState,
            modifier = Modifier.padding(padding),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = ptrState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
        Column(Modifier.fillMaxSize()) {
            // One row, four chips: the answers to "what kind of thing am I looking at?".
            // Categories and accounts have dozens of options each and live in the sheet.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = typeFilter == null,
                    onClick = { typeFilter = null },
                    label = { Text("All") },
                )
                TxnType.entries.forEach { type ->
                    FilterChip(
                        selected = typeFilter == type.name,
                        onClick = { typeFilter = if (typeFilter == type.name) null else type.name },
                        label = { Text(shortTypeLabel(type)) },
                    )
                }
            }
            if (archivedIds.isNotEmpty()) {
                TextButton(
                    onClick = { showArchived = !showArchived },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(
                        if (showArchived) "Back to your transactions"
                        else "Archived (${archivedIds.size})",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (filtered.isEmpty()) {
                // Scrollable so pull-to-refresh works even with nothing in the list.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    EmptyState(
                        style = FaceStyle.SLEEPY,
                        title = "No transactions here",
                        subtitle = when {
                            showArchived -> "Nothing archived. Swipe a row right to put it here."
                            typeFilter != null || hiddenFilterCount > 0 -> "Nothing matches these filters."
                            else -> "Pull down to scan, or add one manually with +."
                        },
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 10.dp, bottom = toolbarSpace,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (order.byDate) {
                        grouped.forEach { (date, dayTxns) ->
                            item(key = "header-$date") { DayHeader(date, dayTxns) }
                            items(dayTxns, key = { it.id }) { txn ->
                                SwipeableTxnRow(
                                    archived = txn.id in archivedIds,
                                    onArchive = { archiveWithUndo(vm, snackbar, scope, txn, txn.id !in archivedIds) },
                                    onDelete = { removeForGood(vm, snackbar, scope, txn) },
                                    deletePrompt = deletePromptFor(txn),
                                    modifier = Modifier.animateItem(),
                                ) {
                                    TxnRow(txn, onClick = { picker = txn }, accounts = accounts)
                                }
                            }
                        }
                    } else {
                        items(ranked, key = { it.id }) { txn ->
                            SwipeableTxnRow(
                                archived = txn.id in archivedIds,
                                onArchive = { archiveWithUndo(vm, snackbar, scope, txn, txn.id !in archivedIds) },
                                onDelete = { removeForGood(vm, snackbar, scope, txn) },
                                deletePrompt = deletePromptFor(txn),
                                modifier = Modifier.animateItem(),
                            ) {
                                TxnRow(txn, onClick = { picker = txn }, accounts = accounts)
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showFilters) {
        FilterSheet(
            categories = categoriesPresent,
            accounts = accounts,
            selectedCategoryId = categoryFilter,
            selectedAccountId = accountFilter,
            onCategory = { categoryFilter = it },
            onAccount = { accountFilter = it },
            onClearAll = {
                categoryFilter = null
                accountFilter = null
            },
            onDismiss = { showFilters = false },
        )
    }

    ScanSheetHost(vm)
    picker?.let { txn ->
        TxnEditSheet(
            txn = txn,
            accounts = accounts,
            onApply = { categoryId, rulePattern ->
                vm.setCategory(txn, categoryId, rulePattern)
                picker = null
            },
            onDismiss = { picker = null },
            rememberByDefault = vm.prefs.smartRules,
            categoryUse = categoryUse,
            askEachTime = askEachTime,
            onAskEachTime = { vm.setAskEachTime(txn.merchant.orEmpty(), it) },
            onSetAccount = {
                vm.setTxnAccount(txn, it)
                picker = null
            },
            onMarkTransfer = { from, to ->
                vm.markAsTransfer(txn, from, to)
                picker = null
            },
            onSplitTransfer = {
                vm.splitTransfer(txn)
                picker = null
            },
        )
    }
}

private fun shortTypeLabel(type: TxnType): String = when (type) {
    TxnType.EXPENSE -> "Out"
    TxnType.INCOME -> "In"
    TxnType.TRANSFER -> "Transfers"
}

@Composable
private fun DayHeader(date: LocalDate, dayTxns: List<Txn>) {
    val spentByCurrency = dayTxns
        .filter { it.type == TxnType.EXPENSE }
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
