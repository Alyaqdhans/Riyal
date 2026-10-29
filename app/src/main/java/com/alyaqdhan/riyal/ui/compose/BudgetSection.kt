@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Numerals
import com.alyaqdhan.riyal.data.BudgetPlan
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.theme.successColor
import kotlin.math.roundToInt

/**
 * The budget, on Home under the net: a cap per expense category for the period on
 * screen, what is left of them, and whether the money is going faster than the days.
 * Being at 85% of a cap is fine on the 28th and alarming on the 8th, so the pace is the
 * part that earns its place.
 *
 * The period is the one Home is showing - its date tiles drive it, rather than a
 * selector of its own. A plan made for other dates that overlap them says so.
 *
 * Switched on in Settings; the card isn't drawn while budgets are off.
 */
@Composable
fun BudgetCard(
    slice: TimeSlice,
    plans: List<BudgetPlan>,
    txns: List<Txn>,
    currency: String,
    /** Saves a plan, new or edited; [copiedFrom] is the plan its caps were taken from. */
    onSave: (plan: BudgetPlan, copiedFrom: BudgetPlan?) -> Unit,
    onDelete: (planId: String) -> Unit,
    modifier: Modifier = Modifier,
    categoryUse: Map<String, Int> = emptyMap(),
) {
    // A plan belongs to the period it was made for: the one whose bounds match what the
    // user is looking at, not merely one that overlaps it.
    val plan = remember(plans, slice) {
        plans.firstOrNull { it.startMillis == slice.start && it.endExclusiveMillis == slice.endExclusive }
            ?: plans.firstOrNull { it.overlaps(slice.start, slice.endExclusive) }
    }
    val progress = remember(plan, txns, currency) {
        plan?.takeIf { it.lines.isNotEmpty() }?.let { Stats.budgetProgress(it, txns, currency) }
    }
    val sliceTitle = slice.title()
    // A plan with no caps (an earlier build made them empty) is edited, not added to.
    val draft = plan ?: BudgetPlan(
        id = "",
        label = sliceTitle,
        startMillis = slice.start,
        endExclusiveMillis = slice.endExclusive,
    )
    var editing by remember { mutableStateOf(false) }

    Card(modifier.fillMaxWidth()) {
        if (progress == null) {
            val previous = remember(plans, slice) {
                plans.firstOrNull { it.endExclusiveMillis <= slice.start && it.lines.isNotEmpty() }
            }
            NoBudget(
                title = sliceTitle,
                previous = previous,
                onCopy = { from -> onSave(draft.copy(lines = from.lines), from) },
                onStart = { editing = true },
            )
        } else {
            PlanSummary(
                progress = progress,
                currency = currency,
                runs = plan?.takeIf {
                    it.startMillis != slice.start || it.endExclusiveMillis != slice.endExclusive
                }?.label,
                onEdit = { editing = true },
            )
        }
    }

    if (editing) {
        val before = remember(slice) { slice.shifted(back = true) }
        BudgetEditor(
            plan = draft,
            currency = currency,
            txns = txns,
            before = before,
            categoryUse = categoryUse,
            onSave = { lines ->
                editing = false
                onSave(draft.copy(lines = lines), null)
            },
            onDelete = {
                editing = false
                onDelete(draft.id)
            },
            onDismiss = { editing = false },
        )
    }
}

/** How many category rows the card shows before "Show all". */
private const val VISIBLE_LINES = 3

private const val DAY_MS = 24L * 60 * 60 * 1000

/** No caps for these dates yet: what a budget is for, and the one tap that starts it. */
@Composable
private fun NoBudget(
    title: String,
    previous: BudgetPlan?,
    onCopy: (BudgetPlan) -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_sym_savings),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.budget_none, title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.budget_none_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Last period's caps are usually this one's too, so carrying them over is the
        // first offer, one tap; starting afresh is the other.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (previous != null) {
                Button(onClick = { onCopy(previous) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.budget_copy_from, previous.label))
                }
                TextButton(onClick = onStart, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.budget_start_fresh))
                }
            } else {
                FilledTonalButton(onClick = onStart, shapes = ButtonDefaults.shapes()) {
                    Icon(
                        painterResource(R.drawable.ic_sym_add),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.budget_start))
                }
            }
        }
    }
}

/**
 * A plan at a glance: what is left to spend, large, the way the net is above it; the
 * whole plan as one bar with where it should be by now; and the categories closest to
 * their caps.
 */
@Composable
private fun PlanSummary(
    progress: Stats.BudgetProgress,
    currency: String,
    /** The plan's own dates, when they are not the ones on screen. */
    runs: String?,
    onEdit: () -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    val plan = progress.plan
    val now = System.currentTimeMillis()
    val ended = now >= plan.endExclusiveMillis
    val tone = when {
        progress.over -> MaterialTheme.colorScheme.error
        progress.aheadOfPace -> MaterialTheme.colorScheme.primary
        else -> successColor()
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.budget_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_edit)) }
    }
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                stringResource(if (progress.over) R.string.budget_over_label else R.string.budget_left_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(
                if (progress.over) progress.totalSpentMinor - progress.totalCapMinor
                else progress.totalCapMinor - progress.totalSpentMinor,
                currency,
                sign = MoneySign.NONE,
                roll = true,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = if (progress.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 18.sp,
                    maxFontSize = MaterialTheme.typography.headlineMedium.fontSize,
                ),
            )
            MoneyAwareText(
                stringResource(R.string.budget_of_total, bidi(Money.format(progress.totalCapMinor, currency))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (runs != null) {
                Text(
                    stringResource(R.string.budget_plan_runs, runs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PaceBar(fraction = progress.fraction, pace = if (ended) 0f else progress.elapsedFraction, color = tone)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(paceIcon(progress)),
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(16.dp),
            )
            val pace = when {
                progress.over -> stringResource(R.string.budget_status_over)
                ended -> stringResource(R.string.budget_status_within)
                progress.aheadOfPace -> stringResource(R.string.budget_status_ahead)
                else -> stringResource(R.string.budget_status_on_pace)
            }
            val days = when {
                ended -> stringResource(R.string.budget_ended)
                now < plan.startMillis -> stringResource(R.string.budget_not_started)
                else -> counted(
                    R.plurals.budget_days_left,
                    ((plan.endExclusiveMillis - now + DAY_MS - 1) / DAY_MS).toInt(),
                )
            }
            Text(
                "$pace · $days",
                style = MaterialTheme.typography.labelLarge,
                color = tone,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.budget_spent_pct, (progress.fraction * 100).roundToInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()
        // The plan's own order is by cap size, and showing the biggest caps would hide a
        // small category already over its limit - the one thing on this card worth
        // interrupting someone for. So the rows first shown are the closest to their caps.
        val shown = remember(progress) { Stats.mostAtRisk(progress.lines, VISIBLE_LINES) }
        val rest = remember(progress, shown) { progress.lines - shown.toSet() }
        shown.forEach { BudgetLine(it, currency) }
        if (rest.isNotEmpty()) {
            AnimatedVisibility(
                visible = showAll,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rest.forEach { BudgetLine(it, currency) }
                }
            }
        }
        if (rest.isNotEmpty() || progress.unbudgetedMinor > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress.unbudgetedMinor > 0) {
                    MoneyAwareText(
                        stringResource(
                            R.string.budget_outside,
                            bidi(Money.format(progress.unbudgetedMinor, currency)),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (rest.isNotEmpty()) {
                    TextButton(onClick = { showAll = !showAll }, shapes = ButtonDefaults.shapes()) {
                        Text(
                            if (showAll) stringResource(R.string.budget_show_less)
                            else stringResource(R.string.budget_show_all, progress.lines.size),
                        )
                    }
                }
            }
        }
    }
}

@DrawableRes
private fun paceIcon(progress: Stats.BudgetProgress): Int = when {
    progress.over -> R.drawable.ic_sym_error
    progress.aheadOfPace -> R.drawable.ic_sym_info
    else -> R.drawable.ic_sym_check_circle
}

/**
 * The whole plan as one bar, with a thin mark where it would be if the money went
 * evenly across the days: being ahead shows long before the bar is full.
 */
@Composable
private fun PaceBar(fraction: Float, pace: Float, color: Color) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        FlatBar(progress = { fraction.coerceIn(0f, 1f) }, color = color, modifier = Modifier.fillMaxWidth())
        if (pace > 0.02f && pace < 0.98f) {
            Box(Modifier.fillMaxWidth(pace), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

/**
 * One category against its cap, laid out as Home's top spending rows are: its badge,
 * its name with what is spent of the cap, and a bar in its colour - the error colour
 * once it is over.
 */
@Composable
fun BudgetLine(line: Stats.BudgetLineProgress, currency: String, modifier: Modifier = Modifier) {
    val category = Categories.byId(line.categoryId)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category.id, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MoneyAwareText(
                    stringResource(
                        R.string.budget_bar_amounts,
                        bidi(Money.format(line.spentMinor, currency)),
                        bidi(Money.format(line.capMinor, currency)),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (line.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlatBar(
                progress = { line.fraction.coerceIn(0f, 1f) },
                color = if (line.over) MaterialTheme.colorScheme.error else categoryColor(category.id),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The caps of one plan, set together and saved together. The category chips are the
 * list of what is capped - tap one to cap it, again to drop it - and each capped
 * category gets its amount field, with what went on it the period before as a guide.
 */
@Composable
private fun BudgetEditor(
    plan: BudgetPlan,
    currency: String,
    txns: List<Txn>,
    /** The period before this one, whose spending guides each cap. */
    before: TimeSlice,
    categoryUse: Map<String, Int>,
    onSave: (Map<String, Long>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val order = rememberCategoryOrder(categoryUse)
    val categories = remember(order) { order.sort(Categories.forType(TxnType.EXPENSE)) }
    // Each capped category with what is typed for it, biggest caps first, new ones last.
    var caps by remember {
        mutableStateOf(
            plan.lines.entries.sortedByDescending { it.value }
                .map { (id, minor) -> id to Money.toMajor(minor, currency).stripTrailingZeros().toPlainString() }
        )
    }
    var added by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val spentBefore = remember(txns, before, currency) {
        categories.associate { it.id to Stats.categoryTotalIn(txns, it.id, before.start, before.endExclusive, currency) }
    }
    val beforeTitle = before.title()
    val amounts = caps.map { (_, text) -> Numerals.decimal(text)?.takeIf { it.signum() > 0 } }
    val complete = caps.isNotEmpty() && amounts.all { it != null }
    val total = amounts.sumOf { it?.let { a -> Money.toMinor(a, currency) } ?: 0L }
    val pop = rememberDialogPop()

    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
        title = { Text(stringResource(R.string.budget_editor_title, plan.label)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.budget_editor_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CategoryRows(
                    categories,
                    selected = { c -> caps.any { it.first == c.id } },
                    onClick = { c ->
                        if (caps.any { it.first == c.id }) {
                            caps = caps.filter { it.first != c.id }
                        } else {
                            caps = caps + (c.id to "")
                            added = c.id
                        }
                    },
                )
                caps.forEach { (id, text) ->
                    key(id) {
                        val focus = remember { FocusRequester() }
                        // A category just tapped goes straight to its amount.
                        LaunchedEffect(Unit) { if (id == added) focus.requestFocus() }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryBadge(id, size = 36.dp)
                            val spent = spentBefore[id] ?: 0L
                            OutlinedTextField(
                                value = text,
                                onValueChange = { v -> caps = caps.map { if (it.first == id) id to v else it } },
                                label = { Text(Categories.byId(id).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                suffix = { CurrencySign(currency) },
                                supportingText = if (spent > 0) {
                                    {
                                        MoneyAwareText(
                                            stringResource(
                                                R.string.budget_spent_before,
                                                bidi(Money.format(spent, currency)),
                                                beforeTitle,
                                            ),
                                        )
                                    }
                                } else {
                                    null
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).focusRequester(focus),
                            )
                            IconButton(onClick = { caps = caps.filter { it.first != id } }) {
                                Icon(
                                    painterResource(R.drawable.ic_sym_close),
                                    contentDescription = stringResource(R.string.action_remove),
                                )
                            }
                        }
                    }
                }
                if (caps.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.budget_total),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        MoneyText(total, currency, sign = MoneySign.NONE, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (plan.id.isNotBlank()) DeleteButton(onClick = { confirmDelete = true })
            }
        },
        confirmButton = {
            Button(
                enabled = complete,
                onClick = {
                    val lines = caps.zip(amounts).associate { (cap, amount) -> cap.first to Money.toMinor(amount!!, currency) }
                    pop.leave { onSave(lines) }
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = { pop.leave(onDismiss) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )

    if (confirmDelete) {
        val confirmPop = rememberDialogPop()
        AlertDialog(
            onDismissRequest = { confirmPop.leave { confirmDelete = false } },
            modifier = Modifier.popIn(confirmPop),
            title = { Text(stringResource(R.string.budget_delete_title)) },
            text = { Text(stringResource(R.string.budget_delete_body, plan.label)) },
            confirmButton = {
                TextButton(onClick = {
                    // The question goes, then the editor it was asked over.
                    confirmPop.leave {
                        confirmDelete = false
                        pop.leave(onDelete)
                    }
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPop.leave { confirmDelete = false } }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
