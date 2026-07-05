@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.alyaqdhan.riyal.ui.compose

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val CURRENCIES = listOf("OMR", "SAR", "AED", "KWD", "BHD", "QAR", "USD", "EUR", "GBP", "INR")

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

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
                        JaggyFace(mood = 1f, modifier = Modifier.size(64.dp).popIn())
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
                    JaggyFace(mood = -1f, style = FaceStyle.DIZZY, modifier = Modifier.size(64.dp).popIn())
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
                TextButton(onClick = { clipboard.setText(AnnotatedString(Verbose.dump())) }) {
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

/** Category picker: icon chips + optional "always" rule for this merchant. */
@Composable
fun CategoryPickerSheet(
    txn: Txn,
    onApply: (categoryId: String, rulePattern: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var makeRule by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pick a category", style = MaterialTheme.typography.titleLarge)
            Column {
                Text(
                    txn.merchant ?: txn.sender,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    txn.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Categories.forDirection(txn.direction).forEach { cat ->
                    FilterChip(
                        selected = cat.id == txn.categoryId,
                        onClick = {
                            onApply(cat.id, if (makeRule) txn.merchant?.lowercase() else null)
                        },
                        label = { Text(cat.name) },
                        leadingIcon = { CategoryIcon(cat.id) },
                        modifier = Modifier.pressBounce(0.92f),
                    )
                }
            }
            if (!txn.merchant.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(checked = makeRule, onCheckedChange = { makeRule = it })
                    Text(
                        "Always: anything mentioning \"${txn.merchant}\" gets the category I pick",
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
    onSave: (amountMinor: Long, currency: String, direction: Direction, merchant: String?, categoryId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var direction by remember { mutableStateOf(Direction.EXPENSE) }
    var merchant by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(Categories.DEFAULT_EXPENSE) }
    LaunchedEffect(direction) {
        categoryId = if (direction == Direction.INCOME) Categories.DEFAULT_INCOME else Categories.DEFAULT_EXPENSE
    }
    val parsed = amount.trim().replace(",", "").toBigDecimalOrNull()
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd MMM uuuu, h:mm a") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = direction == Direction.EXPENSE,
                        onClick = { direction = Direction.EXPENSE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Money out") }
                    SegmentedButton(
                        selected = direction == Direction.INCOME,
                        onClick = { direction = Direction.INCOME },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Money in") }
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
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / from (optional)") },
                    singleLine = true,
                )
                DropdownField(
                    label = "Category",
                    value = Categories.byId(categoryId).name,
                    options = Categories.forDirection(direction).map { it.id },
                    display = { Categories.byId(it).name },
                    onSelect = { categoryId = it },
                )
                Text(
                    "Date: ${dateFmt.format(Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed.signum() > 0,
                onClick = {
                    onSave(
                        Money.toMinor(parsed!!, currency),
                        currency,
                        direction,
                        merchant.trim().ifBlank { null },
                        categoryId,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
