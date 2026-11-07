@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryBadge
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.MoneySign
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import com.alyaqdhan.riyal.ui.compose.ScrollThumb
import com.alyaqdhan.riyal.ui.compose.SortChip
import com.alyaqdhan.riyal.ui.compose.SwipeableTxnRow
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.TxnSort
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.title
import com.alyaqdhan.riyal.ui.compose.toolbarSpace
import com.alyaqdhan.riyal.ui.compose.unmasked

/**
 * Analysis's second tab: a period's spending by where it went, biggest first, and a
 * search over the names. It lives here rather than beside the records because a total
 * for a shop only means something over a period and an account, and Analysis has both.
 *
 * The period and the search scroll with the list, so the rows get the screen. Records
 * that name no place are added up under the list, so what it shows never quietly falls
 * short of the period's spending.
 */
@Composable
internal fun PlacesTab(
    txns: List<Txn>,
    slice: TimeSlice,
    onSlice: (TimeSlice) -> Unit,
    currency: String,
    accountId: String?,
    onOpen: (Stats.Place) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val spending = remember(txns, slice, currency, accountId) {
        Stats.placesIn(txns, slice.start, slice.endExclusive, currency, accountId)
    }
    val shown = remember(spending, query) { spending.places.filter { it.matches(query) } }
    val searching = query.isNotBlank()
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    // Edge to edge, the keyboard opens over the page: this keeps the last rows reachable.
    ScrollThumb(list, Modifier.imePadding(), bottom = toolbarSpace) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = toolbarSpace),
        ) {
            item(key = "period") {
                PeriodBar(slice = slice, onChange = onSlice, txns = txns, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.places_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                shown.isNotEmpty() -> {
                    item(key = "summary", contentType = "line") {
                        PlacesLine(counted(R.plurals.count_places, shown.size), shown.sumOf { it.amountMinor }, currency)
                    }
                    items(shown, key = { it.key }, contentType = { "place" }) { place ->
                        PlaceRow(place, currency, onClick = { onOpen(place) })
                    }
                }
                searching -> item(key = "no-match") {
                    EmptyState(title = stringResource(R.string.places_no_match, query.trim()))
                }
                spending.unnamedCount == 0 -> item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.places_empty_title, slice.title()),
                        subtitle = stringResource(R.string.cat_detail_empty_subtitle),
                    )
                }
            }
            // It is no one place, so a search has nothing to say about it.
            if (!searching && spending.unnamedCount > 0) {
                item(key = "unnamed", contentType = "line") {
                    PlacesLine(
                        counted(R.plurals.places_unnamed, spending.unnamedCount),
                        spending.unnamedMinor,
                        currency,
                    )
                }
            }
        }
    }
}

/** A quiet line in the list: what it is on one side, what it comes to on the other. */
@Composable
private fun PlacesLine(text: String, minor: Long, currency: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        MoneyText(
            minor,
            currency,
            sign = MoneySign.NONE,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One place: its name, how many records and under what, and what they came to. */
@Composable
private fun PlaceRow(place: Stats.Place, currency: String, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = { CategoryBadge(place.categoryId, size = 40.dp) },
        supportingContent = {
            Text(
                counted(R.plurals.count_records, place.count) + " · " + Categories.byId(place.categoryId).name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            MoneyText(
                place.amountMinor,
                currency,
                sign = MoneySign.NONE,
                style = MaterialTheme.typography.titleSmall,
            )
        },
    ) {
        Text(unmasked(place.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One place's records for a period: what they came to, how that compares with the period
 * before, and every record behind the number, which swipe and re-file as they do anywhere.
 */
@Composable
fun PlaceDetailScreen(
    vm: MainViewModel,
    placeKey: String,
    placeName: String,
    categoryId: String,
    onBack: () -> Unit,
    initialSlice: TimeSlice? = null,
    initialAccountId: String? = null,
) {
    val txns by vm.countedTxns.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    // The period and account the row was read under, as a category page opens with them.
    var slice by remember { mutableStateOf(initialSlice ?: TimeSlice.thisMonth()) }
    var accountId by rememberSaveable { mutableStateOf(initialAccountId) }
    var sort by rememberSaveable { mutableStateOf(TxnSort.NEWEST.name) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(accounts) {
        if (accountId != null && accounts.none { it.id == accountId }) accountId = null
    }

    val order = TxnSort.valueOf(sort)
    val records = remember(txns, placeKey, slice, accountId, currency, order) {
        order.applyTo(Stats.recordsAt(txns, placeKey, slice.start, slice.endExclusive, currency, accountId))
    }
    val total = remember(records) { records.sumOf { it.amountMinor } }
    val previousTotal = remember(txns, placeKey, slice, accountId, currency) {
        val (prevStart, prevEnd) = Stats.previousWindow(slice.start, slice.endExclusive)
        Stats.recordsAt(txns, placeKey, prevStart, prevEnd, currency, accountId).sumOf { it.amountMinor }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(unmasked(placeName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            Column {
                AccountChips(accounts, accountId, onSelect = { accountId = it })
                val list = rememberLazyListState()
                ScrollThumb(list) {
                    LazyColumn(
                        state = list,
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
                                        MoneyText(
                                            total,
                                            currency,
                                            sign = MoneySign.NONE,
                                            style = MaterialTheme.typography.headlineSmall,
                                        )
                                        Text(
                                            stringResource(
                                                R.string.cat_detail_in_period,
                                                counted(R.plurals.count_records, records.size),
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
                        if (records.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    title = stringResource(R.string.cat_detail_empty_title, slice.title()),
                                    subtitle = stringResource(R.string.cat_detail_empty_subtitle),
                                )
                            }
                        } else {
                            items(records, key = { it.id }, contentType = { "record" }) { txn ->
                                SwipeableTxnRow(
                                    archived = false,
                                    onArchive = { archiveWithUndo(vm, snackbar, scope, txn, archive = true) },
                                    onRemove = { removeForGood(vm, snackbar, scope, txn) },
                                    removeLabel = removeLabelOf(txn),
                                ) {
                                    TxnRow(txn, onClick = { editing = txn }, accounts = accounts, showAccount = accountId == null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { txn -> RecordSheet(vm, txn, accounts, onClose = { editing = null }) }
}
