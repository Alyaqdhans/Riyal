@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDateRangePickerState
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
 * The budget: one plan per period, a cap per expense category, and bars that say not
 * just how much is gone but whether it is going faster than the calendar. Being at 85%
 * of a cap is fine on the 28th and alarming on the 8th, so the pace marker is the part
 * that actually earns its place.
 *
 * The period is the one the screen is already showing - Home's month selector drives it,
 * rather than a second selector of its own. A plan whose period is not that month says
 * so, and its period is changed from the editor.
 *
 * Switched on in Settings; the section simply isn't rendered while budgets are off.
 */
@Composable
fun BudgetSection(
    slice: TimeSlice,
    plans: List<BudgetPlan>,
    txns: List<Txn>,
    currency: String,
    onCreate: (label: String, start: Long, endExclusive: Long) -> Unit,
    onCopy: (source: BudgetPlan, label: String, start: Long, endExclusive: Long) -> Unit,
    onSetLine: (planId: String, categoryId: String, minor: Long) -> Unit,
    onSetPeriod: (planId: String, label: String, start: Long, endExclusive: Long) -> Unit,
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
    var showAll by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("Budget")
            if (plan != null) {
                Row {
                    TextButton(onClick = { showEditor = true }) { Text("Edit") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                }
            }
        }

        // Only worth saying when the plan is not the period on screen - otherwise the
        // month selector above has already said it.
        if (plan != null && (plan.startMillis != slice.start || plan.endExclusiveMillis != slice.endExclusive)) {
            Text(
                "Plan runs ${plan.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (plan == null || progress == null) {
            Text(
                "No plan for ${slice.label} yet.",
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
                "No caps yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = { showEditor = true },
                modifier = Modifier.pressBounce(),
            ) { Text("Add a cap") }
        } else {
            // With one capped category the total *is* that category: drawing both said
            // "OMR 4.500 of OMR 60.000" twice, once with an icon.
            val sole = progress.lines.singleOrNull()
            BudgetTotal(progress, currency, soleCategoryId = sole?.categoryId)
            if (sole == null) {
                // The total bar is the answer; two categories are the detail most people
                // want, and the rest is one tap away rather than a screenful of bars.
                //
                // Which two matters: the plan's own order is by cap size, and showing the
                // two biggest caps would hide a small category that is already over its
                // limit - the one thing on this card worth interrupting someone for. So the
                // summary shows whichever are closest to (or past) their cap.
                val shown = remember(progress, showAll) {
                    if (showAll) progress.lines else Stats.mostAtRisk(progress.lines, VISIBLE_LINES)
                }
                shown.forEach { line ->
                    BudgetBar(
                        categoryId = line.categoryId,
                        spent = line.spentMinor,
                        budget = line.capMinor,
                        currency = currency,
                        paceFraction = progress.elapsedFraction,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (progress.lines.size > VISIBLE_LINES) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(
                            if (showAll) "Show less"
                            else "Show all ${progress.lines.size}",
                        )
                    }
                }
                if (progress.unbudgetedMinor > 0) {
                    AssistChip(
                        onClick = { showEditor = true },
                        label = { Text("${Money.formatAmount(progress.unbudgetedMinor, currency)} uncapped") },
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
            onSetPeriod = { label, start, end -> onSetPeriod(plan.id, label, start, end) },
            onDismiss = { showEditor = false },
            // The section already has every record; no need to route the counts in
            // from the view model to reach the same answer.
            categoryUse = Stats.categoryUse(txns),
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

/** How many category bars the card shows before "Show all". */
private const val VISIBLE_LINES = 2

/** The whole plan at a glance, with the pace read that makes a percentage mean something. */
@Composable
private fun BudgetTotal(
    progress: Stats.BudgetProgress,
    currency: String,
    soleCategoryId: String? = null,
) {
    val used = progress.fraction
    val color = when {
        progress.over -> MaterialTheme.colorScheme.error
        progress.aheadOfPace -> MaterialTheme.colorScheme.primary
        else -> successColor()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (soleCategoryId != null) {
                    CategoryIcon(soleCategoryId, size = 16.dp)
                    Text(
                        Categories.byId(soleCategoryId).name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    Money.format(progress.totalSpentMinor, currency) + " of " +
                        Money.format(progress.totalCapMinor, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (soleCategoryId != null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
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
    onSetPeriod: (label: String, start: Long, endExclusive: Long) -> Unit,
    onDismiss: () -> Unit,
    categoryUse: Map<String, Int> = emptyMap(),
) {
    val order = rememberCategoryOrder(categoryUse)
    var selected by remember { mutableStateOf(order.sort(Categories.forType(TxnType.EXPENSE)).first().id) }
    var amount by remember { mutableStateOf("") }
    // Local view so the list updates live as caps are added or removed.
    var current by remember { mutableStateOf(plan.lines) }
    var label by remember { mutableStateOf(plan.label) }
    var showRange by remember { mutableStateOf(false) }
    val parsed = amount.trim().replace(",", "").toBigDecimalOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // A plan need not be a calendar month, so this is where a plan stops
                // being one - the screen itself only ever steps months.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Period", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { showRange = true }) { Text("Change") }
                }
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
                    order = order,
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

    if (showRange) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null &&
                        rangeState.selectedEndDateMillis != null,
                    onClick = {
                        val slice = TimeSlice.ofDays(
                            TimeSlice.utcDay(rangeState.selectedStartDateMillis!!),
                            TimeSlice.utcDay(rangeState.selectedEndDateMillis!!),
                        )
                        onSetPeriod(slice.label, slice.start, slice.endExclusive)
                        label = slice.label
                        showRange = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(460.dp))
        }
    }
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
