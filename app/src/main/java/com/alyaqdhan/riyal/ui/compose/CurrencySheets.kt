@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Currencies
import com.alyaqdhan.riyal.core.CurrencyInfo
import com.alyaqdhan.riyal.core.Numerals
import java.math.BigDecimal
import java.math.MathContext

/** The flag, at the size a list row wants it. */
@Composable
private fun Flag(info: CurrencyInfo) {
    Text(info.flag, fontSize = 26.sp)
}

/**
 * Every currency the app knows, with its flag, its name in the app's language and its
 * sign, searchable by either the name or the code.
 */
@Composable
fun CurrencyPickerSheet(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember { Currencies.ALL }
    val shown = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) all
        else all.filter { it.code.contains(q, ignoreCase = true) || context.getString(it.name).contains(q, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(
                stringResource(R.string.cur_pick_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.cur_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.cur_none_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn {
                items(shown, key = { it.code }) { info ->
                    ListItem(
                        onClick = { onPick(info.code) },
                        supportingContent = { Text(info.code) },
                        leadingContent = { Flag(info) },
                        trailingContent = {
                            if (info.code == selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                CurrencySign(info.code, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    ) { Text(stringResource(info.name)) }
                }
            }
        }
    }
}

/**
 * A form field that shows a currency and opens [CurrencyPickerSheet] when tapped: the
 * stock outlined field, read-only, with the flag where a leading icon would go.
 */
@Composable
fun CurrencyField(
    label: String,
    code: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val info = Currencies.of(code)
    Box(modifier) {
        OutlinedTextField(
            value = info?.let { stringResource(it.name) } ?: code,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = info?.let { { Flag(it) } },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The field itself swallows taps while read-only, so the sheet is opened by a
        // layer over it that covers exactly the same area.
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true },
        )
    }
    if (open) {
        CurrencyPickerSheet(
            selected = code,
            onPick = {
                onPick(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/** "0.3845" rather than "0.38450000000": four significant figures, nothing trailing. */
private fun rateText(rate: BigDecimal): String =
    rate.round(MathContext(4)).stripTrailingZeros().toPlainString()

/**
 * What each currency is worth in [base], which is how purchases abroad are counted. The
 * pegged ones are fixed and say so; the rest can be corrected one at a time.
 */
@Composable
fun ExchangeRatesSheet(
    base: String,
    overrides: Map<String, BigDecimal>,
    onSetRate: (code: String, omrPerUnit: BigDecimal?) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<CurrencyInfo?>(null) }
    // Read again whenever an override changes, so a saved rate shows at once.
    val rows = remember(overrides, base) { Currencies.ALL.filter { it.code != base } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(
                stringResource(R.string.cur_rates_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.cur_rates_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(rows, key = { it.code }) { info ->
                    val rate = Currencies.rate(info.code, base)
                    val custom = info.code in overrides
                    ListItem(
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MoneyAwareText(
                                    stringResource(
                                        R.string.cur_rate_line,
                                        info.code,
                                        base + " " + (rate?.let(::rateText) ?: "?"),
                                    ) + if (custom) " · " + stringResource(R.string.cur_rate_custom) else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        leadingContent = { Flag(info) },
                        trailingContent = {
                            if (info.pegged) {
                                Text(
                                    stringResource(R.string.cur_fixed_peg),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                IconButton(onClick = { editing = info }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.cur_rate_edit_title, info.code),
                                    )
                                }
                            }
                        },
                    ) { Text(stringResource(info.name)) }
                }
            }
        }
    }

    editing?.let { info ->
        RateDialog(
            info = info,
            base = base,
            onSave = { perBase ->
                val baseInfo = Currencies.of(base)
                onSetRate(info.code, baseInfo?.let { perBase.multiply(it.omrPerUnit, MathContext.DECIMAL64) })
                editing = null
            },
            onReset = {
                onSetRate(info.code, null)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RateDialog(
    info: CurrencyInfo,
    base: String,
    onSave: (BigDecimal) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember {
        mutableStateOf(Currencies.rate(info.code, base)?.let(::rateText).orEmpty())
    }
    val value = Numerals.decimal(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cur_rate_edit_title, stringResource(info.name))) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.cur_rate_edit_label, info.code)) },
                    suffix = { CurrencySign(base) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (Currencies.isOverridden(info.code)) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.cur_rate_reset)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value != null && value.signum() > 0,
                onClick = { onSave(value!!) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
