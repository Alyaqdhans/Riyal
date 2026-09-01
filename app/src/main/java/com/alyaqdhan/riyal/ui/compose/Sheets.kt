@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.alyaqdhan.riyal.ui.compose

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Category
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.data.UserRule
import com.alyaqdhan.riyal.ui.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

val CURRENCIES = listOf("OMR", "SAR", "AED", "KWD", "BHD", "QAR", "USD", "EUR", "GBP", "INR")

/** Wraps text as a clipboard entry for the new suspend [androidx.compose.ui.platform.Clipboard] API. */
fun plainText(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("riyal", text))

/** How each operation type is labelled everywhere the user picks one. */
fun typeLabel(type: TxnType): String = when (type) {
    TxnType.EXPENSE -> "Money out"
    TxnType.INCOME -> "Money in"
    TxnType.TRANSFER -> "Transfer"
}

/**
 * The scan bottom sheet: expressive LoadingIndicator while working, the live verbose
 * log streaming underneath, and a plain-language summary when done. Dismissable at any
 * time, the scan itself keeps running; the user is in charge of the window, not the work.
 */
@Composable
fun ScanSheetHost(vm: MainViewModel) {
    val visible by vm.scanSheetVisible.collectAsState()
    if (!visible) return

    val scan by vm.scanState.collectAsState()
    val lines by Verbose.lines.collectAsState()
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val listState = rememberLazyListState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    ModalBottomSheet(onDismissRequest = { vm.closeScanSheet() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp),
        ) {
            when (val s = scan) {
                is MainViewModel.ScanState.Running -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Material 3 Expressive shape-morphing loading indicator
                        LoadingIndicator(Modifier.size(52.dp))
                        Column {
                            Text("Scanning your inbox…", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (s.total > 0) "${s.processed} / ${s.total} messages" else "querying the inbox…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Expressive squiggly progress, determinate once the total is known
                    if (s.total > 0) {
                        LinearWavyProgressIndicator(
                            progress = { s.processed / s.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                is MainViewModel.ScanState.Done -> Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Face(mood = 1f, modifier = Modifier.size(64.dp).popIn())
                        Column {
                            Text("Scan complete", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "took ${"%.1f".format(s.summary.tookMs / 1000f)}s · ${s.summary.scanned} messages checked",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FlowRow(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SummaryPill(
                            "✓ ${s.summary.parsed} recorded",
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (s.summary.transfers > 0) {
                            SummaryPill(
                                "⇄ ${s.summary.transfers} possible transfer(s)",
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        SummaryPill(
                            "? ${s.summary.review} need you",
                            if (s.summary.review > 0) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            if (s.summary.review > 0) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SummaryPill(
                            "${s.summary.skipped} skipped, never stored",
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is MainViewModel.ScanState.Failed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Face(mood = -1f, style = FaceStyle.DIZZY, modifier = Modifier.size(64.dp).popIn())
                    Column {
                        Text("Scan didn't finish", style = MaterialTheme.typography.titleLarge)
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                MainViewModel.ScanState.Idle -> {}
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Verbose processing log", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = {
                    scope.launch { clipboard.setClipEntry(plainText(Verbose.dump())) }
                }) {
                    Text("Copy")
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                ) {
                    items(lines) { LogRow(it) }
                }
            }
            Button(
                onClick = { vm.closeScanSheet() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .pressBounce(),
            ) {
                Text(if (scan is MainViewModel.ScanState.Running) "Hide, scanning continues" else "Close")
            }
        }
    }
}

/**
 * Everything you can do to one record from a tap: re-file it, tell the app which
 * account it belongs to, or say it wasn't spending at all but a move between your own
 * accounts. With smart rules on (the default), remembering starts enabled, so one
 * correction teaches the app.
 */
@Composable
fun TxnEditSheet(
    txn: Txn,
    accounts: List<Account>,
    onApply: (categoryId: String, rulePattern: String?) -> Unit,
    onDismiss: () -> Unit,
    rememberByDefault: Boolean = false,
    categoryUse: Map<String, Int> = emptyMap(),
    askEachTime: Set<String> = emptySet(),
    onAskEachTime: ((Boolean) -> Unit)? = null,
    onSetAccount: ((String) -> Unit)? = null,
    onMarkTransfer: ((fromAccountId: String?, toAccountId: String?) -> Unit)? = null,
    onSplitTransfer: (() -> Unit)? = null,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    // Frozen for this sheet session: applying a category updates the counts at once,
    // and the chips must not rearrange behind the sheet that is doing the applying.
    val order = rememberCategoryOrder(categoryUse)
    val pattern = txn.merchant?.takeIf { it.isNotBlank() }?.let { UserRule.patternOf(it) }
    val marked = pattern != null && pattern in askEachTime
    var makeRule by remember { mutableStateOf(rememberByDefault && pattern != null && !marked) }
    var transferTarget by remember { mutableStateOf(false) }
    val live = accounts.filter { !it.archived }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (txn.isTransfer) "Transfer" else "Pick a category",
                style = MaterialTheme.typography.titleLarge,
            )
            Column {
                Text(
                    txn.merchant ?: txn.sender,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    txn.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (txn.isTransfer) {
                Text(
                    "This is recorded as money moved between your own accounts, so it counts " +
                        "as neither spending nor income.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Only a transfer derived from real records can go back to being
                // them; one added by hand has nothing underneath it to restore.
                if (onSplitTransfer != null && txn.legIds.isNotEmpty()) {
                    Button(
                        onClick = onSplitTransfer,
                        modifier = Modifier.fillMaxWidth().pressBounce(),
                    ) {
                        Text(
                            if (txn.legIds.size == 2) "It wasn't a transfer, split it back"
                            else "It wasn't a transfer, undo this"
                        )
                    }
                }
            } else {
                CategoryChips(
                    type = txn.type,
                    selectedId = txn.categoryId,
                    onSelect = { onApply(it, if (makeRule && !marked) pattern else null) },
                    order = order,
                )
                if (pattern != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Switch(
                            checked = makeRule && !marked,
                            enabled = !marked,
                            onCheckedChange = { makeRule = it },
                        )
                        Text(
                            "Always: anything mentioning \"${txn.merchant}\" gets the category I pick",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (marked) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (marked) {
                        Text(
                            "You asked to be asked each time for this name.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Offered right here because this is where the problem shows itself:
                    // picking a category for the same person for the second time, and
                    // seeing that the first answer was not a fact about them.
                    if (onAskEachTime != null) {
                        TextButton(onClick = { onAskEachTime(!marked) }) {
                            Text(
                                if (marked) "Remember this name after all"
                                else "Always ask for this name"
                            )
                        }
                    }
                }
            }

            if (live.isNotEmpty() && (onSetAccount != null || onMarkTransfer != null)) {
                HorizontalDivider()
                if (onSetAccount != null && !txn.isTransfer) {
                    Text("Account", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (txn.accountId == null) {
                            "The bank didn't say which account this was, so it isn't in any balance yet."
                        } else {
                            "Move this record to a different account."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        live.forEach { acc ->
                            FilterChip(
                                selected = acc.id == txn.accountId,
                                onClick = { onSetAccount(acc.id) },
                                label = { Text(acc.displayName) },
                                modifier = Modifier.pressBounce(0.92f),
                            )
                        }
                    }
                }
                // Needs somewhere for the money to have gone: at least one account
                // that isn't the one this record already sits in.
                val otherAccounts = live.filter { it.id != txn.accountId }
                if (onMarkTransfer != null && !txn.isTransfer && otherAccounts.isNotEmpty()) {
                    if (!transferTarget) {
                        TextButton(onClick = { transferTarget = true }) {
                            Text("⇄  This was a transfer between my accounts")
                        }
                    } else {
                        Text(
                            if (txn.isExpense) "Which account did it arrive in?"
                            else "Which account did it leave?",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            otherAccounts.forEach { acc ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        if (txn.isExpense) {
                                            onMarkTransfer(txn.accountId, acc.id)
                                        } else {
                                            onMarkTransfer(acc.id, txn.accountId)
                                        }
                                    },
                                    label = { Text(acc.displayName) },
                                    modifier = Modifier.pressBounce(0.92f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Full-control manual entry, used to resolve unreadable messages or add from scratch. */
@Composable
fun ManualTxnDialog(
    title: String,
    atMillis: Long,
    defaultCurrency: String,
    accounts: List<Account>,
    onSave: (
        amountMinor: Long,
        currency: String,
        type: TxnType,
        merchant: String?,
        categoryId: String,
        fromAccountId: String?,
        toAccountId: String?,
    ) -> Unit,
    onDismiss: () -> Unit,
    categoryUse: Map<String, Int> = emptyMap(),
) {
    val live = remember(accounts) { accounts.filter { !it.archived } }
    val order = rememberCategoryOrder(categoryUse)
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var type by remember { mutableStateOf(TxnType.EXPENSE) }
    var merchant by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(Categories.DEFAULT_EXPENSE) }
    var fromAccount by remember { mutableStateOf(live.firstOrNull()) }
    var toAccount by remember { mutableStateOf(live.getOrNull(1) ?: live.firstOrNull()) }

    LaunchedEffect(type) {
        categoryId = Categories.defaultFor(type)
    }
    val parsed = amount.trim().replace(",", "").toBigDecimalOrNull()
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd MMM uuuu, h:mm a") }
    // A transfer needs two different ends, otherwise it isn't a movement at all.
    val transferValid = type != TxnType.TRANSFER ||
        (fromAccount != null && toAccount != null && fromAccount?.id != toAccount?.id)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TxnType.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = type == option,
                            onClick = { type = option },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = TxnType.entries.size),
                        ) { Text(typeLabel(option)) }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    suffix = { Text(currency) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                DropdownField(
                    label = "Currency",
                    value = currency,
                    options = CURRENCIES,
                    display = { it },
                    onSelect = { currency = it },
                )

                if (live.isNotEmpty()) {
                    when (type) {
                        TxnType.EXPENSE -> DropdownField(
                            label = "From account",
                            value = fromAccount?.displayName ?: "None",
                            options = live,
                            display = { it.displayName },
                            onSelect = { fromAccount = it },
                        )

                        TxnType.INCOME -> DropdownField(
                            label = "To account",
                            value = toAccount?.displayName ?: "None",
                            options = live,
                            display = { it.displayName },
                            onSelect = { toAccount = it },
                        )

                        TxnType.TRANSFER -> {
                            DropdownField(
                                label = "From account",
                                value = fromAccount?.displayName ?: "None",
                                options = live,
                                display = { it.displayName },
                                onSelect = { fromAccount = it },
                            )
                            DropdownField(
                                label = "To account",
                                value = toAccount?.displayName ?: "None",
                                options = live,
                                display = { it.displayName },
                                onSelect = { toAccount = it },
                            )
                            if (!transferValid) {
                                Text(
                                    "Pick two different accounts, money can't move to where it already is.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                if (type != TxnType.TRANSFER) {
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant / from (optional)") },
                        singleLine = true,
                    )
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    CategoryChips(
                        type = type,
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                        order = order,
                    )
                } else {
                    Text(
                        "A transfer has no category: it isn't spending or income, it's your own " +
                            "money changing pocket.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Date: ${dateFmt.format(Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed.signum() > 0 && transferValid,
                onClick = {
                    onSave(
                        Money.toMinor(parsed!!, currency),
                        currency,
                        type,
                        merchant.trim().ifBlank { null },
                        categoryId,
                        if (type == TxnType.INCOME) null else fromAccount?.id,
                        if (type == TxnType.EXPENSE) null else toAccount?.id,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A frozen snapshot of how often the user files into each category, taken once by the
 * composition that owns a picker and not updated afterwards.
 *
 * Frozen deliberately. Filing a record changes these counts the instant it happens, so
 * a live ranking would reorder the chips between two taps - and a chip that moves under
 * a finger is worse than one sitting further down the list.
 */
@Immutable
class CategoryOrder private constructor(private val use: Map<String, Int>) {
    fun sort(cats: List<Category>): List<Category> = Stats.rankCategories(cats, use)

    companion object {
        val Empty = CategoryOrder(emptyMap())
        internal fun of(use: Map<String, Int>) = CategoryOrder(use)
    }
}

/**
 * Freezes [use] for as long as this composition lives. Call it at whatever scope should
 * hold the order still: inside a sheet, and it lasts that sheet session; at the top of
 * the backlog screen, and the whole backlog can be worked without the chips moving.
 */
@Composable
fun rememberCategoryOrder(use: Map<String, Int>): CategoryOrder =
    remember { CategoryOrder.of(use) }

/** How many chips the folded picker shows before it offers the rest. */
private const val TOP_CHIPS = 6

/** Past this many categories, reading the list stops being faster than typing. */
private const val SEARCH_THRESHOLD = 20

/**
 * The one category picker used everywhere: the correction sheet, the manual-entry
 * dialog, the budget editor and the backlog card, so "pick a category" is a single
 * gesture app-wide and no two of them can disagree about the order.
 *
 * It opens folded to the categories the user files into most, because the common case
 * is one tap and every extra chip is one more thing to read past. Whatever is already
 * selected stays visible even when it is not in that first handful - re-filing a record
 * must never hide where it currently sits.
 *
 * [allowSearch] is off for the backlog card, which draws one picker per merchant and
 * has to stay compact. Even where it is on, the field only appears once the list is
 * long enough that reading it is the slower option.
 */
@Composable
fun CategoryChips(
    type: TxnType,
    selectedId: String,
    onSelect: (String) -> Unit,
    order: CategoryOrder = CategoryOrder.Empty,
    allowSearch: Boolean = true,
) {
    val all = remember(type, order) { order.sort(Categories.forType(type)) }
    var expanded by rememberSaveable(type) { mutableStateOf(false) }
    var query by rememberSaveable(type) { mutableStateOf("") }

    val searchable = allowSearch && all.size > SEARCH_THRESHOLD
    val filtering = expanded && searchable && query.isNotBlank()
    val shown = when {
        filtering -> all.filter { it.name.contains(query.trim(), ignoreCase = true) }
        expanded -> all
        else -> {
            val top = all.take(TOP_CHIPS)
            if (top.none { it.id == selectedId }) top + all.filter { it.id == selectedId } else top
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (expanded && searchable) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Find a category") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.forEach { cat ->
                FilterChip(
                    selected = cat.id == selectedId,
                    onClick = { onSelect(cat.id) },
                    label = { Text(cat.name) },
                    leadingIcon = { CategoryIcon(cat.id) },
                    modifier = Modifier.pressBounce(0.92f),
                )
            }
            if (!expanded && all.size > shown.size) {
                AssistChip(
                    onClick = { expanded = true },
                    label = { Text("More (${all.size - shown.size})") },
                    modifier = Modifier.pressBounce(0.92f),
                )
            }
            if (expanded && !filtering) {
                AssistChip(
                    onClick = { expanded = false; query = "" },
                    label = { Text("Less") },
                    modifier = Modifier.pressBounce(0.92f),
                )
            }
        }
        if (filtering && shown.isEmpty()) {
            Text(
                "No category matches that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    value: String,
    options: List<T>,
    display: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
