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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
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
    onSetAccount: ((String) -> Unit)? = null,
    onMarkTransfer: ((fromAccountId: String?, toAccountId: String?) -> Unit)? = null,
    onSplitTransfer: (() -> Unit)? = null,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var makeRule by remember { mutableStateOf(rememberByDefault && !txn.merchant.isNullOrBlank()) }
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
                    onSelect = { onApply(it, if (makeRule) txn.merchant?.lowercase() else null) },
                )
                if (!txn.merchant.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Switch(checked = makeRule, onCheckedChange = { makeRule = it })
                        Text(
                            "Always: anything mentioning \"${txn.merchant}\" gets the category I pick",
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
) {
    val live = remember(accounts) { accounts.filter { !it.archived } }
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
 * The one category picker used everywhere: a chip per category for the given
 * operation type, the current one selected. Same look in the correction sheet and the
 * manual-entry dialog, so "pick a category" is a single gesture app-wide.
 */
@Composable
fun CategoryChips(
    type: TxnType,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Categories.forType(type).forEach { cat ->
            FilterChip(
                selected = cat.id == selectedId,
                onClick = { onSelect(cat.id) },
                label = { Text(cat.name) },
                leadingIcon = { CategoryIcon(cat.id) },
                modifier = Modifier.pressBounce(0.92f),
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
