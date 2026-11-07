@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.PlaceGroup
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryBadge
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.MatchedIcon
import com.alyaqdhan.riyal.ui.compose.MoneySign
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import com.alyaqdhan.riyal.ui.compose.ScrollThumb
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.SortChip
import com.alyaqdhan.riyal.ui.compose.SwipeableTxnRow
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.TxnSort
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.rememberDialogPop
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
 *
 * A chain the bank names by branch is several rows for one place. A long press starts
 * choosing, a tap adds or drops a row, and Merge makes the chosen rows one place under a
 * name, which [onMerge] keeps. The bar that says so lies over the top of the list rather
 * than pushing it down under the finger.
 */
@Composable
internal fun PlacesTab(
    txns: List<Txn>,
    slice: TimeSlice,
    onSlice: (TimeSlice) -> Unit,
    currency: String,
    accountId: String?,
    groups: List<PlaceGroup>,
    onOpen: (Stats.Place) -> Unit,
    onMerge: (keys: Set<String>, name: String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val spending = remember(txns, slice, currency, accountId, groups) {
        Stats.placesIn(txns, slice.start, slice.endExclusive, currency, accountId, groups)
    }
    val shown = remember(spending, query) { spending.places.filter { it.matches(query) } }
    val searching = query.isNotBlank()
    // A list, as a screen's saved state keeps one; read as a set.
    var chosen by rememberSaveable { mutableStateOf(listOf<String>()) }
    val choosing = chosen.isNotEmpty()
    var naming by remember { mutableStateOf(false) }
    // A row that left the list - a new period, a merge - is no longer chosen.
    LaunchedEffect(spending) {
        chosen = chosen.filter { key -> spending.places.any { it.key == key } }
    }
    BackHandler(enabled = choosing) { chosen = emptyList() }
    fun toggle(key: String) {
        chosen = if (key in chosen) chosen - key else chosen + key
    }
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current
    Box {
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
                        PlaceRow(
                            place,
                            currency,
                            chosen = if (choosing) place.key in chosen else null,
                            onClick = { if (choosing) toggle(place.key) else onOpen(place) },
                            onLongClick = { toggle(place.key) },
                        )
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
    if (choosing) {
        ChoosingBar(
            count = chosen.size,
            canChooseMore = shown.any { it.key !in chosen },
            onCancel = { chosen = emptyList() },
            onChooseAll = { chosen = (chosen + shown.map { it.key }).distinct() },
            onMerge = { naming = true },
        )
    }
    }

    if (naming) {
        val names = remember(chosen, spending) {
            spending.places.filter { it.key in chosen }.map { unmasked(it.name) }
        }
        PlaceNameDialog(
            title = R.string.places_merge_title,
            confirm = R.string.places_merge,
            body = stringResource(R.string.places_merge_body),
            initial = Stats.suggestedName(names),
            suggestions = names,
            onConfirm = { name ->
                onMerge(chosen.toSet(), name)
                chosen = emptyList()
                naming = false
            },
            onDismiss = { naming = false },
        )
    }
}

/**
 * What a long press began: how many rows are chosen, a way to take every row the search
 * left, and Merge, which wants two at least.
 */
@Composable
private fun ChoosingBar(
    count: Int,
    canChooseMore: Boolean,
    onCancel: () -> Unit,
    onChooseAll: () -> Unit,
    onMerge: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
            }
            Text(
                counted(R.plurals.places_selected, count),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (canChooseMore) {
                TextButton(onClick = onChooseAll, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.places_select_all))
                }
            }
            Button(onClick = onMerge, enabled = count >= 2, shapes = ButtonDefaults.shapes()) {
                MatchedIcon(R.drawable.ic_sym_merge, MaterialTheme.typography.labelLarge, Modifier.size(18.dp))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.places_merge))
            }
        }
    }
}

/**
 * A name for a merged place, written or picked from the names it is made of. Merging and
 * renaming both ask it, with their own title and button.
 */
@Composable
private fun PlaceNameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    body: String?,
    initial: String,
    suggestions: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val pop = rememberDialogPop()
    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
        icon = { Icon(painterResource(R.drawable.ic_sym_merge), contentDescription = null) },
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The names it is made of, a tap away, for when one of them is the name.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.distinct().take(MAX_SUGGESTIONS).forEach { s ->
                        SuggestionChip(
                            onClick = { name = s },
                            label = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { pop.leave { onConfirm(name.trim()) } },
                enabled = name.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { pop.leave(onDismiss) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Enough names to pick from without the dialog outgrowing the screen. */
private const val MAX_SUGGESTIONS = 6

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

/**
 * One place: its name, how many records and under what - or, merged, what it was made of -
 * and what they came to. [chosen] is null unless rows are being chosen, when the row
 * toggles and shows a box where its badge was.
 */
@Composable
private fun PlaceRow(
    place: Stats.Place,
    currency: String,
    chosen: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val detail = counted(R.plurals.count_records, place.count) + " · " +
        if (place.merged) place.mergedFrom.joinToString(", ") { unmasked(it) }
        else Categories.byId(place.categoryId).name
    val amount: @Composable () -> Unit = {
        MoneyText(place.amountMinor, currency, sign = MoneySign.NONE, style = MaterialTheme.typography.titleSmall)
    }
    val supporting: @Composable () -> Unit = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    val headline: @Composable () -> Unit = { Text(unmasked(place.name), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if (chosen == null) {
        ListItem(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.places_select),
            leadingContent = { CategoryBadge(place.categoryId, size = 40.dp) },
            supportingContent = supporting,
            trailingContent = amount,
            content = headline,
        )
    } else {
        ListItem(
            checked = chosen,
            onCheckedChange = { onClick() },
            leadingContent = { Checkbox(checked = chosen, onCheckedChange = null) },
            supportingContent = supporting,
            trailingContent = amount,
            content = headline,
        )
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
    val groups by vm.placeGroups.collectAsState()
    val group = groups.firstOrNull { it.id == placeKey }
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    var renaming by remember { mutableStateOf(false) }
    // Separated down to one name, a merged place is gone, and so is its page.
    LaunchedEffect(group) {
        if (placeKey.startsWith(PlaceGroup.ID_PREFIX) && group == null) onBack()
    }
    val mergedFrom = remember(txns, group) { group?.let { Stats.placeNames(txns, it.members) }.orEmpty() }
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
    val records = remember(txns, placeKey, slice, accountId, currency, order, groups) {
        order.applyTo(Stats.recordsAt(txns, placeKey, slice.start, slice.endExclusive, currency, accountId, groups))
    }
    val total = remember(records) { records.sumOf { it.amountMinor } }
    val previousTotal = remember(txns, placeKey, slice, accountId, currency, groups) {
        val (prevStart, prevEnd) = Stats.previousWindow(slice.start, slice.endExclusive)
        Stats.recordsAt(txns, placeKey, prevStart, prevEnd, currency, accountId, groups).sumOf { it.amountMinor }
    }
    val title = group?.name ?: placeName

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(unmasked(title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (group != null) {
                        IconButton(onClick = { renaming = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.place_rename))
                        }
                    }
                    SortChip(current = order, onSelect = { sort = it.name })
                },
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
                        if (group != null) {
                            item(key = "merged-title") { SectionTitle(stringResource(R.string.place_merged_from)) }
                            items(mergedFrom.entries.sortedBy { it.value }, key = { "member-" + it.key }) { (member, name) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        unmasked(name),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = { vm.separatePlace(group.id, member, name, group.name) },
                                        shapes = ButtonDefaults.shapes(),
                                    ) { Text(stringResource(R.string.place_separate)) }
                                }
                            }
                            item(key = "records-title") { SectionTitle(stringResource(R.string.cat_detail_records)) }
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

    if (renaming && group != null) {
        PlaceNameDialog(
            title = R.string.place_rename_title,
            confirm = R.string.action_save,
            body = null,
            initial = group.name,
            suggestions = mergedFrom.values.map { unmasked(it) },
            onConfirm = { name ->
                vm.renamePlace(group.id, name)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
}
