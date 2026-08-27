@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.BudgetPlan
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.theme.successColor
import kotlin.math.roundToInt

/**
 * The budget card: one plan per period, a cap per expense category, and bars that say
 * not just how much is gone but whether it is going faster than the calendar. Being at
 * 85% of a cap is fine on the 28th and alarming on the 8th, so the pace marker is the
 * part that actually earns its place.
 *
 * Switched on in Settings; the section simply isn't rendered while budgets are off.
 */
@Composable
fun BudgetSection(
    slice: TimeSlice,
    onSliceChange: (TimeSlice) -> Unit,
    plans: List<BudgetPlan>,
    txns: List<Txn>,
    currency: String,
    onCreate: (label: String, start: Long, endExclusive: Long) -> Unit,
    onCopy: (source: BudgetPlan, label: String, start: Long, endExclusive: Long) -> Unit,
    onSetLine: (planId: String, categoryId: String, minor: Long) -> Unit,
    onDelete: (planId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A plan belongs to the period it was made for: the one whose bounds match what the
    // user is looking at, not merely one that overlaps it.
    val plan = remember(plans, slice) {
        plans.firstOrNull { it.startMillis == slice.start && it.endExclusiveMillis == slice.endExclusive }
            ?: plans.firstOrNull { it.overlaps(slice.start, slice.endExclusive) }
    }
    val progress = remember(plan, txns, currency) {
        plan?.let { Stats.budgetProgress(it, txns, currency) }
    }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Budget", style = MaterialTheme.typography.titleMedium)
                if (plan != null) {
                    Row {
                        TextButton(onClick = { showEditor = true }) { Text("Edit") }
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                    }
                }
            }

            PeriodBar(slice = slice, onChange = onSliceChange, txns = txns, allowFuture = true)

            if (plan == null || progress == null) {
                Text(
                    "No plan for ${slice.label} yet. Set a cap per category and this card tracks " +
                        "how much of it you've used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onCreate(slice.label, slice.start, slice.endExclusive) },
                        modifier = Modifier.pressBounce(),
                    ) { Text("Create plan") }
                    val previous = plans.firstOrNull { it.endExclusiveMillis <= slice.start }
                    if (previous != null && previous.lines.isNotEmpty()) {
                        TextButton(onClick = {
                            onCopy(previous, slice.label, slice.start, slice.endExclusive)
                        }) { Text("Copy ${previous.label}") }
                    }
                }
            } else if (plan.lines.isEmpty()) {
                Text(
                    "This plan has no caps yet. Tap Edit to cap what you spend on a category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = { showEditor = true },
                    modifier = Modifier.pressBounce(),
                ) { Text("Add a cap") }
            } else {
                BudgetTotal(progress, currency)
                progress.lines.forEach { line ->
                    BudgetBar(
                        categoryId = line.categoryId,
                        spent = line.spentMinor,
                        budget = line.capMinor,
                        currency = currency,
                        paceFraction = progress.elapsedFraction,
                    )
                }
                if (progress.unbudgetedMinor > 0) {
                    Text(
                        "${Money.format(progress.unbudgetedMinor, currency)} more went to categories " +
                            "you haven't capped, so it isn't in the bars above.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showEditor && plan != null) {
        BudgetEditorDialog(
            plan = plan,
            currency = currency,
            onSetLine = { categoryId, minor -> onSetLine(plan.id, categoryId, minor) },
            onDismiss = { showEditor = false },
        )
    }

    if (confirmDelete && plan != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this plan?") },
            text = { Text("\"${plan.label}\" and its caps will be removed. Your transactions are untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(plan.id)
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** The whole plan at a glance, with the pace read that makes a percentage mean something. */
@Composable
private fun BudgetTotal(progress: Stats.BudgetProgress, currency: String) {
    val used = progress.fraction
    val color = when {
        progress.over -> MaterialTheme.colorScheme.error
        progress.aheadOfPace -> MaterialTheme.colorScheme.primary
        else -> successColor()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                Money.format(progress.totalSpentMinor, currency) + " of " +
                    Money.format(progress.totalCapMinor, currency),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${(used * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
        }
        LinearWavyProgressIndicator(
            progress = { used.coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            amplitude = { 1f },
            modifier = Modifier.fillMaxWidth(),
        )
        val elapsedPct = (progress.elapsedFraction * 100).roundToInt()
        Text(
            when {
                progress.over ->
                    "Over by ${Money.format(progress.totalSpentMinor - progress.totalCapMinor, currency)}"
                progress.aheadOfPace ->
                    "$elapsedPct% of the period gone, ${(used * 100).roundToInt()}% of the budget spent — running ahead"
                else ->
                    "$elapsedPct% of the period gone, ${Money.format(progress.totalCapMinor - progress.totalSpentMinor, currency)} still to spend"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (progress.over) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One category's spend against its cap. The thin marker is where the bar *should* be if
 * the money were going evenly across the period, so being ahead is visible at a glance
 * rather than only once the bar is full.
 */
@Composable
fun BudgetBar(
    categoryId: String,
    spent: Long,
    budget: Long,
    currency: String,
    paceFraction: Float? = null,
) {
    val cat = Categories.byId(categoryId)
    val fraction = if (budget > 0) (spent.toFloat() / budget.toFloat()) else 0f
    val over = spent > budget
    val barColor = when {
        over -> MaterialTheme.colorScheme.error
        fraction >= 0.85f -> MaterialTheme.colorScheme.tertiary
        else -> Color(Categories.colorFor(categoryId))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIcon(categoryId, size = 16.dp)
            Text(cat.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${Money.format(spent, currency)} / ${Money.format(budget, currency)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
            )
            if (paceFraction != null && paceFraction > 0.02f && paceFraction < 0.98f) {
                Box(
                    Modifier
                        .fillMaxWidth(paceFraction)
                        .height(8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
        }
        if (over) {
            Text(
                "Over by ${Money.format(spent - budget, currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Set or clear a cap per category: pick a category chip, type an amount, add it.
 * Existing caps are listed with a remove control. Expense categories only, since income
 * isn't something you cap.
 */
@Composable
private fun BudgetEditorDialog(
    plan: BudgetPlan,
    currency: String,
    onSetLine: (categoryId: String, minor: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(Categories.forType(TxnType.EXPENSE).first().id) }
    var amount by remember { mutableStateOf("") }
    // Local view so the list updates live as caps are added or removed.
    var current by remember { mutableStateOf(plan.lines) }
    val parsed = amount.trim().replace(",", "").toBigDecimalOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plan.label) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (current.isNotEmpty()) {
                    current.entries.sortedByDescending { it.value }.forEach { (catId, minor) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(catId, size = 18.dp)
                            Text(
                                "${Categories.byId(catId).name}: ${Money.format(minor, currency)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                onSetLine(catId, 0L)
                                current = current - catId
                            }) { Text("Remove") }
                        }
                    }
                }
                Text("Add or change a cap", style = MaterialTheme.typography.labelLarge)
                CategoryChips(
                    type = TxnType.EXPENSE,
                    selectedId = selected,
                    onSelect = { selected = it },
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Cap for this period") },
                    suffix = { Text(currency) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed.signum() > 0,
                onClick = {
                    val minor = Money.toMinor(parsed!!, currency)
                    onSetLine(selected, minor)
                    current = current + (selected to minor)
                    amount = ""
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Small colored dot used by legends and budget rows. */
@Composable
fun ColorDot(color: Color, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
