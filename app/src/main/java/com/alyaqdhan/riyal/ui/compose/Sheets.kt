@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.ui.text.rememberTextMeasurer
import com.alyaqdhan.riyal.ui.theme.onSuccessContainer
import com.alyaqdhan.riyal.ui.theme.successContainer
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.annotation.StringRes
import com.alyaqdhan.riyal.R

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Numerals
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
import java.util.Locale
import kotlinx.coroutines.launch

/** Wraps text as a clipboard entry for the new suspend [androidx.compose.ui.platform.Clipboard] API. */
fun plainText(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("riyal", text))

/** How each operation type is named where the user picks one: as Home names its totals. */
@StringRes
fun typeLabel(type: TxnType): Int = when (type) {
    TxnType.EXPENSE -> R.string.label_expense
    TxnType.INCOME -> R.string.label_income
    TxnType.TRANSFER -> R.string.label_transfer
}

/** And the arrow Home's totals carry, so the same thing looks the same everywhere. */
@DrawableRes
fun typeIcon(type: TxnType): Int = when (type) {
    TxnType.EXPENSE -> R.drawable.ic_sym_arrow_up
    TxnType.INCOME -> R.drawable.ic_sym_arrow_down
    TxnType.TRANSFER -> R.drawable.ic_sym_swap
}

/** And Home's colours for it: spending red, income green, a transfer neither. */
@Composable
fun typeTone(type: TxnType): Pair<Color, Color> = when (type) {
    TxnType.EXPENSE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    TxnType.INCOME -> successContainer() to onSuccessContainer()
    TxnType.TRANSFER -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}

/**
 * One of a few, as Material 3 Expressive's connected button group: the chosen one fills
 * in [tone] and rounds fully, the others stay quiet. Replaces segmented buttons, which
 * read as a row of boxes.
 */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> Int)? = null,
    tone: @Composable (T) -> Pair<Color, Color> = {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    },
) {
    val labels = options.map { label(it) }
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // One row when every label fits whole; otherwise two to a row, so a narrow phone
        // gets "Expense | Income" and "Transfer" under them rather than three squeezed
        // to ellipses.
        val density = LocalDensity.current
        val needed = with(density) {
            labels.sumOf { measurer.measure(it, textStyle).size.width } +
                options.size * (16.dp + if (icon != null) 24.dp else 0.dp).roundToPx() +
                (options.size - 1) * ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx()
        }
        val perRow = if (needed <= constraints.maxWidth) options.size else 2
        Column(verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            options.indices.chunked(perRow).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    row.forEachIndexed { at, index ->
                        val option = options[index]
                        val (on, onOn) = tone(option)
                        ToggleButton(
                            checked = option == selected,
                            onCheckedChange = { onSelect(option) },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                            shapes = when {
                                row.size == 1 -> ToggleButtonDefaults.shapes()
                                at == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                at == row.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                checkedContainerColor = on,
                                checkedContentColor = onOn,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            if (icon != null) {
                                Icon(painterResource(icon(option)), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(labels[index], maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The scan bottom sheet: a progress indicator while working, the live verbose
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
                        FlatWheel(Modifier.size(48.dp))
                        Column {
                            Text(
                                stringResource(
                                    R.string.scan_phase_ellipsis,
                                    stringResource(s.phase),
                                ),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                if (s.total > 0) {
                                    stringResource(
                                        R.string.scan_progress,
                                        s.processed,
                                        s.total,
                                        stringResource(s.noun),
                                    )
                                } else {
                                    stringResource(R.string.scan_querying)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ScanBar(
                        progress = if (s.total > 0) ({ s.processed / s.total.toFloat() }) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is MainViewModel.ScanState.Done -> Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_sym_check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Column {
                            Text(
                                stringResource(R.string.scan_done),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                stringResource(
                                    R.string.scan_took,
                                    String.format(Locale.ROOT, "%.1f", s.summary.tookMs / 1000f),
                                    counted(R.plurals.count_messages, s.summary.scanned),
                                ),
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
                            stringResource(R.string.scan_pill_recorded, s.summary.parsed),
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (s.summary.transfers > 0) {
                            SummaryPill(
                                stringResource(
                                    R.string.scan_pill_transfers,
                                    counted(R.plurals.home_possible_transfers, s.summary.transfers),
                                ),
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        SummaryPill(
                            stringResource(R.string.scan_pill_need_you, s.summary.review),
                            if (s.summary.review > 0) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            if (s.summary.review > 0) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SummaryPill(
                            stringResource(R.string.scan_pill_skipped, s.summary.skipped),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is MainViewModel.ScanState.Failed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_sym_error), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Column {
                        Text(
                            stringResource(R.string.scan_failed),
                            style = MaterialTheme.typography.titleLarge,
                        )
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
                Text(
                    stringResource(R.string.scan_log_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = {
                    scope.launch { clipboard.setClipEntry(plainText(Verbose.dump())) }
                }) {
                    Text(stringResource(R.string.action_copy))
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
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    stringResource(
                        if (scan is MainViewModel.ScanState.Running) R.string.scan_hide
                        else R.string.action_close
                    )
                )
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
                stringResource(
                    if (txn.isTransfer) R.string.label_transfer else R.string.sheet_pick_category
                ),
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
                // Bought abroad: what the bank charged, and what it counts as here.
                if (txn.isConverted) {
                    MoneyAwareText(
                        stringResource(
                            R.string.cur_counted_as,
                            Money.format(txn.originalAmountMinor!!, txn.originalCurrency!!),
                            Money.format(txn.amountMinor, txn.currency),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (txn.isTransfer) {
                Text(
                    stringResource(R.string.sheet_transfer_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Only a transfer derived from real records can go back to being
                // them; one added by hand has nothing underneath it to restore.
                if (onSplitTransfer != null && txn.legIds.isNotEmpty()) {
                    Button(
                        onClick = onSplitTransfer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (txn.legIds.size == 2) R.string.sheet_split_back
                                else R.string.sheet_undo_transfer
                            )
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
                    // What happens to this name's other records, as one choice. It was a
                    // switch for "always" and a button below it for "ask each time":
                    // two controls about the same thing, which could disagree.
                    Text(
                        stringResource(R.string.sheet_rule_title, txn.merchant.orEmpty()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val choices = buildList {
                        add(R.string.sheet_rule_once)
                        add(R.string.sheet_rule_remember)
                        if (onAskEachTime != null || marked) add(R.string.sheet_rule_ask)
                    }
                    val current = when {
                        marked -> R.string.sheet_rule_ask
                        makeRule -> R.string.sheet_rule_remember
                        else -> R.string.sheet_rule_once
                    }
                    ConnectedChoice(
                        options = choices,
                        selected = current,
                        onSelect = { choice ->
                            if (choice == R.string.sheet_rule_ask) {
                                makeRule = false
                                if (!marked) onAskEachTime?.invoke(true)
                            } else {
                                if (marked) onAskEachTime?.invoke(false)
                                makeRule = choice == R.string.sheet_rule_remember
                            }
                        },
                        label = { stringResource(it) },
                    )
                }
            }

            if (live.isNotEmpty() && (onSetAccount != null || onMarkTransfer != null)) {
                HorizontalDivider()
                if (onSetAccount != null && !txn.isTransfer) {
                    Text(
                        stringResource(R.string.label_account),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(
                            if (txn.accountId == null) R.string.sheet_account_unknown
                            else R.string.sheet_account_move
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        live.forEach { acc ->
                            FilterChip(
                                selected = acc.id == txn.accountId,
                                onClick = { onSetAccount(acc.id) },
                                label = { Text(acc.displayName) },
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
                            Text(stringResource(R.string.sheet_was_transfer))
                        }
                    } else {
                        Text(
                            stringResource(
                                if (txn.isExpense) R.string.sheet_which_arrived
                                else R.string.sheet_which_left
                            ),
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
    /**
     * What the message already said, when this dialog is finishing a decision rather
     * than starting one. Retyping a figure printed in the message is the kind of work
     * the app exists to avoid.
     */
    initialAmountMinor: Long? = null,
    initialCurrency: String? = null,
    initialType: TxnType? = null,
) {
    val live = remember(accounts) { accounts.filter { !it.archived } }
    val order = rememberCategoryOrder(categoryUse)
    val startCurrency = initialCurrency ?: defaultCurrency
    var amount by remember {
        mutableStateOf(
            initialAmountMinor?.let { Money.formatAmount(it, startCurrency).replace(",", "") } ?: ""
        )
    }
    var currency by remember { mutableStateOf(startCurrency) }
    var type by remember { mutableStateOf(initialType ?: TxnType.EXPENSE) }
    var merchant by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(Categories.DEFAULT_EXPENSE) }
    var fromAccount by remember { mutableStateOf(live.firstOrNull()) }
    var toAccount by remember { mutableStateOf(live.getOrNull(1) ?: live.firstOrNull()) }

    LaunchedEffect(type) {
        categoryId = Categories.defaultFor(type)
    }
    val parsed = Numerals.decimal(amount)
    val dateFmt = Dates.pattern("dd MMM uuuu, h:mm a")
    // A transfer needs two different ends, otherwise it isn't a movement at all.
    val transferValid = type != TxnType.TRANSFER ||
        (fromAccount != null && toAccount != null && fromAccount?.id != toAccount?.id)

    val pop = rememberDialogPop()

    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectedChoice(
                    options = TxnType.entries,
                    selected = type,
                    onSelect = { type = it },
                    label = { stringResource(typeLabel(it)) },
                    icon = ::typeIcon,
                    tone = { typeTone(it) },
                )
                // The amount is what the form is for, so it is written large.
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.label_amount)) },
                    suffix = { CurrencySign(currency, height = 22.dp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                CurrencyField(
                    label = stringResource(R.string.label_currency),
                    code = currency,
                    onPick = { currency = it },
                )

                if (live.isNotEmpty()) {
                    when (type) {
                        TxnType.EXPENSE -> DropdownField(
                            label = stringResource(R.string.sheet_from_account),
                            value = fromAccount?.displayName ?: stringResource(R.string.sheet_no_account),
                            options = live,
                            display = { it.displayName },
                            onSelect = { fromAccount = it },
                        )

                        TxnType.INCOME -> DropdownField(
                            label = stringResource(R.string.sheet_to_account),
                            value = toAccount?.displayName ?: stringResource(R.string.sheet_no_account),
                            options = live,
                            display = { it.displayName },
                            onSelect = { toAccount = it },
                        )

                        TxnType.TRANSFER -> {
                            DropdownField(
                                label = stringResource(R.string.sheet_from_account),
                                value = fromAccount?.displayName ?: stringResource(R.string.sheet_no_account),
                                options = live,
                                display = { it.displayName },
                                onSelect = { fromAccount = it },
                            )
                            DropdownField(
                                label = stringResource(R.string.sheet_to_account),
                                value = toAccount?.displayName ?: stringResource(R.string.sheet_no_account),
                                options = live,
                                display = { it.displayName },
                                onSelect = { toAccount = it },
                            )
                            if (!transferValid) {
                                Text(
                                    stringResource(R.string.sheet_two_accounts),
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
                        label = { Text(stringResource(R.string.sheet_merchant_optional)) },
                        singleLine = true,
                    )
                    Text(
                        stringResource(R.string.label_category),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    CategoryChips(
                        type = type,
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                        order = order,
                    )
                } else {
                    Text(
                        stringResource(R.string.sheet_transfer_no_category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(
                        R.string.sheet_date,
                        dateFmt.format(
                            Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault())
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed.signum() > 0 && transferValid,
                onClick = {
                    val minor = Money.toMinor(parsed!!, currency)
                    val chosenCurrency = currency
                    val chosenType = type
                    val name = merchant.trim().ifBlank { null }
                    val category = categoryId
                    val from = if (type == TxnType.INCOME) null else fromAccount?.id
                    val to = if (type == TxnType.EXPENSE) null else toAccount?.id
                    pop.leave { onSave(minor, chosenCurrency, chosenType, name, category, from, to) }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = { pop.leave(onDismiss) }) { Text(stringResource(R.string.action_cancel)) }
        },
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

/** Rows of chips in the picker; the rest of the categories are a sideways scroll away. */
private const val CHIP_ROWS = 3

/**
 * The one category picker used everywhere: the correction sheet, the manual-entry
 * dialog, the budget editor and the backlog card, so "pick a category" is a single
 * gesture app-wide and no two of them can disagree about the order.
 *
 * Every category, in three rows that scroll sideways together - nothing folded away
 * behind a "more". They run down the columns in the order the user files into most, so
 * the first column holds the three likeliest and the rest are a swipe away. Whatever is
 * already selected is scrolled into view when the picker opens: re-filing a record must
 * never hide where it currently sits.
 */
@Composable
fun CategoryChips(
    type: TxnType,
    selectedId: String,
    onSelect: (String) -> Unit,
    order: CategoryOrder = CategoryOrder.Empty,
) {
    val all = remember(type, order) { order.sort(Categories.forType(type)) }
    CategoryRows(all, selected = { it.id == selectedId }, onClick = { onSelect(it.id) })
}

/**
 * Category chips in three rows that scroll sideways together. Each chip goes on whichever
 * row is shortest so far, so the rows end level instead of stepping down like stairs,
 * and Other always closes. The first selected chip is scrolled into view when they open.
 */
@Composable
fun CategoryRows(
    categories: List<Category>,
    selected: (Category) -> Boolean,
    onClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val rows = remember(categories, labelStyle, density) {
        val other = categories.filter { it.id == Categories.DEFAULT_EXPENSE || it.id == Categories.DEFAULT_INCOME }
        val chipFrame = with(density) { (8 + 18 + 8 + 16 + 8).dp.toPx() }
        fun width(cat: Category) = measurer.measure(cat.name, labelStyle).size.width + chipFrame
        val lines = List(CHIP_ROWS) { mutableListOf<Category>() }
        val lengths = FloatArray(CHIP_ROWS)
        (categories - other.toSet() + other).forEach { cat ->
            val r = lengths.indices.minBy { lengths[it] }
            lines[r] += cat
            lengths[r] += width(cat)
        }
        lines.filter { it.isNotEmpty() }
    }
    val scroll = rememberScrollState()
    var selectedAt by remember { mutableStateOf<Int?>(null) }
    val gutter = with(density) { 24.dp.roundToPx() }
    LaunchedEffect(selectedAt != null) {
        selectedAt?.let { scroll.scrollTo((it - gutter).coerceAtLeast(0)) }
    }
    Column(
        modifier.fillMaxWidth().horizontalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cat ->
                    val on = selected(cat)
                    FilterChip(
                        selected = on,
                        onClick = { onClick(cat) },
                        label = { Text(cat.name, maxLines = 1) },
                        leadingIcon = { CategoryIcon(cat.id) },
                        modifier = if (on && selectedAt == null) {
                            Modifier.onPlaced { selectedAt = it.positionInParent().x.toInt() }
                        } else {
                            Modifier
                        },
                    )
                }
            }
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
