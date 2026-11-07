@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChipDefaults
import com.alyaqdhan.riyal.ui.compose.ScrollThumb
import com.alyaqdhan.riyal.ui.compose.typeIcon
import com.alyaqdhan.riyal.ui.compose.typeLabel
import com.alyaqdhan.riyal.ui.compose.typeTone
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.ui.compose.NoticeHost
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.MatchedIcon
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.MoneyAwareText
import com.alyaqdhan.riyal.ui.compose.FilterSheet
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.SwipeTour
import com.alyaqdhan.riyal.ui.compose.SwipeWay
import com.alyaqdhan.riyal.ui.compose.SwipeableTxnRow
import com.alyaqdhan.riyal.ui.compose.rememberSwipeTour
import com.alyaqdhan.riyal.ui.compose.TxnEditSheet
import com.alyaqdhan.riyal.ui.compose.toolbarSpace
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.SortChip
import com.alyaqdhan.riyal.ui.compose.TxnSort
import com.alyaqdhan.riyal.ui.compose.bidi
import com.alyaqdhan.riyal.ui.compose.dayLabel
import com.alyaqdhan.riyal.ui.compose.localDateOf
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Transactions' two pages: the records that count, and the ones archived. */
private enum class TxnPage { RECORDS, ARCHIVED }

@Composable
fun TransactionsScreen(vm: MainViewModel) {
    val txns by vm.txns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categoryUse by vm.categoryUse.collectAsState()
    val askEachTime by vm.askEachTime.collectAsState()
    // Any number of each; lists, as a screen's saved state keeps them.
    var categoryFilter by rememberSaveable { mutableStateOf(listOf<String>()) }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var accountFilter by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(TxnSort.NEWEST.name) }
    var picker by remember { mutableStateOf<Txn?>(null) }
    val archivedIds by vm.archivedIds.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Swiping is the only way to archive or remove a record and nothing on screen says
    // so, which left the gesture to be found by accident. The first time the list is seen,
    // its first row opens each way by itself with a bubble saying what it uncovered.
    val tour = rememberSwipeTour(
        seen = vm.prefs.swipeTourSeen,
        steps = listOf(
            SwipeTour.Step(SwipeWay.LEFT, R.string.tour_remove_title, R.string.tour_remove_text),
            SwipeTour.Step(SwipeWay.RIGHT, R.string.tour_archive_title, R.string.tour_archive_text),
        ),
        onDone = { vm.prefs.swipeTourSeen = true },
    )

    // In any of the chosen categories, touching any of the chosen accounts. The records
    // that count are one page and the archived ones the other, and the filters, the type
    // and the order apply to both.
    val matching = remember(txns, categoryFilter, typeFilter, accountFilter) {
        val categories = categoryFilter.toSet()
        txns.filter { t ->
            (categories.isEmpty() || t.categoryId in categories) &&
                (typeFilter == null || t.type.name == typeFilter) &&
                (accountFilter.isEmpty() || accountFilter.any(t::touches))
        }
    }
    val live = remember(matching, archivedIds) { matching.filter { it.id !in archivedIds } }
    val archive = remember(matching, archivedIds) { matching.filter { it.id in archivedIds } }
    val order = TxnSort.valueOf(sort)
    // How many of the sheet's choices are on, so the button can say so without
    // opening it.
    val hiddenFilterCount = categoryFilter.size + accountFilter.size
    // A row names its account unless the list is down to one.
    val showAccount = accountFilter.size != 1
    val categoriesPresent = remember(txns) {
        Categories.ALL.filter { cat -> txns.any { it.categoryId == cat.id } }
    }
    val pager = rememberPagerState { TxnPage.entries.size }

    /** One page of records: the ones that count, or the archive. */
    @Composable
    fun RecordPage(records: List<Txn>, archived: Boolean) {
        // Sorted by date the list reads as days; sorted by size it reads as one ranking,
        // and day headers would just chop the ranking into meaningless pieces.
        val grouped = remember(records, order) {
            if (order.byDate) records.groupBy { localDateOf(it.atMillis) } else emptyMap()
        }
        val ranked = remember(records, order) {
            if (order.byDate) emptyList() else order.applyTo(records)
        }
        // The archive's rows bring records back rather than archive them, which is not what
        // the bubbles say, so the tour plays on the records, once the pager rests there.
        val onRecords = pager.settledPage == TxnPage.RECORDS.ordinal && !pager.isScrollInProgress
        val touring = if (archived || !onRecords) null else {
            if (order.byDate) grouped.values.firstOrNull()?.firstOrNull()?.id else ranked.firstOrNull()?.id
        }
        if (records.isEmpty()) {
            // Scrollable so pull-to-refresh works even with nothing in the list.
            val scroll = rememberScrollState()
            ScrollThumb(scroll, Modifier.fillMaxSize(), bottom = toolbarSpace) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll),
            ) {
                EmptyState(
                    title = stringResource(R.string.txn_empty_title),
                    subtitle = stringResource(
                        when {
                            archived && typeFilter == null && hiddenFilterCount == 0 -> R.string.txn_empty_archived
                            typeFilter != null || hiddenFilterCount > 0 -> R.string.txn_empty_filtered
                            else -> R.string.txn_empty_none
                        }
                    ),
                )
            }
            }
            return
        }
        // Rows are list items with their own inset, so the list adds none at the
        // sides; the end leaves room for the bar and the Add button over it.
        val list = rememberLazyListState()
        ScrollThumb(list, bottom = toolbarSpace) {
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = 4.dp, bottom = toolbarSpace),
        ) {
            // Content types let a jump of the thumb build its new screen from the
            // rows it left, a record from a record and a day from a day.
            if (order.byDate) {
                grouped.forEach { (date, dayTxns) ->
                    item(key = "header-$date", contentType = "day") { DayHeader(date, dayTxns) }
                    items(dayTxns, key = { it.id }, contentType = { "record" }) { txn ->
                        SwipeableTxnRow(
                            archived = archived,
                            onArchive = { archiveWithUndo(vm, snackbar, scope, txn, !archived) },
                            onRemove = { removeForGood(vm, snackbar, scope, txn) },
                            removeLabel = removeLabelOf(txn),
                            tour = tour.takeIf { txn.id == touring },
                        ) {
                            TxnRow(
                                txn,
                                onClick = { picker = txn },
                                accounts = accounts,
                                showDate = false,
                                showAccount = showAccount,
                            )
                        }
                    }
                }
            } else {
                items(ranked, key = { it.id }, contentType = { "record" }) { txn ->
                    SwipeableTxnRow(
                        archived = archived,
                        onArchive = { archiveWithUndo(vm, snackbar, scope, txn, !archived) },
                        onRemove = { removeForGood(vm, snackbar, scope, txn) },
                        removeLabel = removeLabelOf(txn),
                        tour = tour.takeIf { txn.id == touring },
                    ) {
                        TxnRow(
                            txn,
                            onClick = { picker = txn },
                            accounts = accounts,
                            showAccount = showAccount,
                        )
                    }
                }
            }
        }
        }
    }

    Scaffold(
        // Clear of the floating toolbar, which is drawn over this screen by the activity
        // and otherwise sits on top of the message and its Undo.
        snackbarHost = {
            NoticeHost(snackbar, modifier = Modifier.padding(bottom = toolbarSpace))
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_transactions)) },
                actions = {
                    // Sort and Filters live here, not in the chip row: six controls in
                    // one row pushed the Transfers chip off the right edge of something
                    // that gives no sign it scrolls.
                    SortChip(current = order, onSelect = { sort = it.name })
                    // Export lives in Settings with backup; this bar is for looking.
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(
                            badge = {
                                if (hiddenFilterCount > 0) Badge { Text(hiddenFilterCount.toString()) }
                            },
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_sym_filter_list),
                                contentDescription = stringResource(R.string.txn_filters),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        ScanRefresh(vm, Modifier.padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            // The archive is a page beside the records, as Lists' and Analysis's are: a tab
            // slides over to it, and a finger can drag from one to the other.
            PrimaryTabRow(selectedTabIndex = pager.targetPage) {
                TxnPage.entries.forEach { p ->
                    Tab(
                        selected = pager.targetPage == p.ordinal,
                        onClick = { scope.launch { pager.animateScrollToPage(p.ordinal) } },
                        text = {
                            Text(
                                when {
                                    p == TxnPage.RECORDS -> stringResource(R.string.txn_tab_records)
                                    archivedIds.isEmpty() -> stringResource(R.string.txn_tab_archived)
                                    else -> stringResource(R.string.txn_archived_count, archivedIds.size)
                                }
                            )
                        },
                    )
                }
            }
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
                    label = { Text(stringResource(R.string.label_all)) },
                )
                TxnType.entries.forEach { type ->
                    // Named, marked and coloured as Home's totals are.
                    val (tone, onTone) = typeTone(type)
                    FilterChip(
                        selected = typeFilter == type.name,
                        onClick = { typeFilter = if (typeFilter == type.name) null else type.name },
                        label = { Text(stringResource(typeLabel(type))) },
                        leadingIcon = {
                            MatchedIcon(typeIcon(type), MaterialTheme.typography.labelLarge, Modifier.size(18.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tone,
                            selectedLabelColor = onTone,
                            selectedLeadingIconColor = onTone,
                        ),
                    )
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                // Both stay built, so neither loses its place or rebuilds as it slides in.
                beyondViewportPageCount = 1,
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (TxnPage.entries[page]) {
                    TxnPage.RECORDS -> RecordPage(live, archived = false)
                    TxnPage.ARCHIVED -> RecordPage(archive, archived = true)
                }
            }
        }
        }
    }

    if (showFilters) {
        FilterSheet(
            categories = categoriesPresent,
            accounts = accounts,
            selectedCategoryIds = categoryFilter.toSet(),
            selectedAccountIds = accountFilter.toSet(),
            onToggleCategory = { id -> categoryFilter = if (id in categoryFilter) categoryFilter - id else categoryFilter + id },
            onToggleAccount = { id -> accountFilter = if (id in accountFilter) accountFilter - id else accountFilter + id },
            onClearAll = {
                categoryFilter = emptyList()
                accountFilter = emptyList()
            },
            onDismiss = { showFilters = false },
        )
    }

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

@Composable
private fun DayHeader(date: LocalDate, dayTxns: List<Txn>) {
    val spentByCurrency = dayTxns
        .filter { it.type == TxnType.EXPENSE }
        .groupBy { it.currency }
        .map { (currency, list) -> bidi("− " + Money.format(list.sumOf { it.amountMinor }, currency)) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            dayLabel(date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (spentByCurrency.isNotEmpty()) {
            MoneyAwareText(
                spentByCurrency.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
