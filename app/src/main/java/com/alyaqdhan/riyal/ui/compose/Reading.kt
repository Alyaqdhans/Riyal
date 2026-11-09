@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.FieldInference
import com.alyaqdhan.riyal.data.SmsParser
import com.alyaqdhan.riyal.data.TemplateFields

/**
 * What the user picked about one message: which of its amounts moved, which is the
 * balance after it ([TemplateFields.NO_BALANCE] for none), and who was paid.
 */
data class ReadingPick(
    val amount: Int? = null,
    val balance: Int? = null,
    val shop: String? = null,
    val noShop: Boolean = false,
)

/** A message's amounts and names, as the pickers offer them. */
class ReadingChoices(val body: String, defaultCurrency: String) {
    val parser = SmsParser(emptyList(), emptyList(), defaultCurrency)
    val amounts: List<SmsParser.AmountChoice> = parser.amountChoices(body)
    val shops: List<String> = parser.shopChoices(body)

    /** Something to pick between. */
    val worthAsking: Boolean get() = amounts.size >= 2 || shops.isNotEmpty()

    /** How the reader took it, to start from: [amountMinor] in [currency], and [merchant]. */
    fun start(amountMinor: Long?, currency: String?, merchant: String?): ReadingPick {
        val amount = FieldInference.indexOf(amounts, amountMinor, currency)
        return ReadingPick(
            amount = amount,
            balance = amounts.indices.lastOrNull { it != amount && amounts[it].nearBalance } ?: TemplateFields.NO_BALANCE,
            shop = merchant?.let { m -> shops.firstOrNull { it.equals(m, ignoreCase = true) } },
            noShop = false,
        )
    }

    /**
     * What [pick] changed from [start], as the fields of the message's kind: only the
     * parts the user moved, so the rest is still the reader's to decide. [amountNeeded]
     * keeps the amount even unmoved, for a message the reader found none in.
     */
    fun fields(start: ReadingPick, pick: ReadingPick, amountNeeded: Boolean = false): TemplateFields? {
        var f = TemplateFields(
            amount = pick.amount?.takeIf { amountNeeded || it != start.amount },
            balance = pick.balance?.takeIf { it != start.balance },
            noShop = pick.noShop && !start.noShop,
        )
        val shop = pick.shop
        if (shop != null && shop != start.shop) f = parser.placeShop(body, shop, f) ?: f
        return f.takeIf { it.placesAnything }
    }
}

/**
 * Chips for which of a message's amounts moved, which is the balance after it, and who
 * was paid - the parts of a message the reader can get wrong for a whole kind of them.
 */
@Composable
fun ReadingPicker(choices: ReadingChoices, pick: ReadingPick, onPick: (ReadingPick) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (choices.amounts.isNotEmpty()) {
            Text(stringResource(R.string.reading_amount), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.amounts.forEachIndexed { i, c ->
                    FilterChip(
                        selected = pick.amount == i,
                        onClick = {
                            // An amount cannot be its own balance.
                            onPick(pick.copy(amount = i, balance = pick.balance?.takeIf { it != i } ?: TemplateFields.NO_BALANCE))
                        },
                        label = { AmountLabel(c) },
                    )
                }
            }
        }
        if (choices.amounts.size >= 2) {
            Text(stringResource(R.string.reading_balance), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = pick.balance == null || pick.balance == TemplateFields.NO_BALANCE,
                    onClick = { onPick(pick.copy(balance = TemplateFields.NO_BALANCE)) },
                    label = { Text(stringResource(R.string.reading_no_balance)) },
                )
                choices.amounts.forEachIndexed { i, c ->
                    if (i != pick.amount) {
                        FilterChip(
                            selected = pick.balance == i,
                            onClick = { onPick(pick.copy(balance = i)) },
                            label = { AmountLabel(c) },
                        )
                    }
                }
            }
        }
        if (choices.shops.isNotEmpty()) {
            Text(stringResource(R.string.reading_shop), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = pick.noShop,
                    onClick = { onPick(pick.copy(noShop = true, shop = null)) },
                    label = { Text(stringResource(R.string.reading_no_shop)) },
                )
                choices.shops.forEach { s ->
                    FilterChip(
                        selected = pick.shop == s,
                        onClick = { onPick(pick.copy(shop = s, noShop = false)) },
                        label = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountLabel(c: SmsParser.AmountChoice) {
    val minor = c.minor
    if (minor != null) {
        MoneyText(minor, c.currency, sign = MoneySign.NONE, style = MaterialTheme.typography.labelLarge)
    } else {
        Text(c.raw)
    }
}

/**
 * "Read it differently": the message, and the pickers. Saved, every message written like
 * it is read the same way.
 */
@Composable
fun ReadingDialog(
    choices: ReadingChoices,
    start: ReadingPick,
    onSave: (TemplateFields) -> Unit,
    onDismiss: () -> Unit,
) {
    val pop = rememberDialogPop()
    var pick by remember { mutableStateOf(start) }
    val fields = choices.fields(start, pick)
    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
        title = { Text(stringResource(R.string.reading_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.reading_body), style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        choices.body,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                ReadingPicker(choices, pick) { pick = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { fields?.let { f -> pop.leave { onSave(f) } } },
                enabled = fields != null,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = { pop.leave(onDismiss) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
