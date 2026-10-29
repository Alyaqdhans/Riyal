@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.ui.compose.DeleteButton
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.rememberDialogPop
import com.alyaqdhan.riyal.ui.compose.bidi
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Category
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.data.UserRule
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.compose.MoneyAwareText
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.FlatBar
import com.alyaqdhan.riyal.ui.compose.HelpAction
import com.alyaqdhan.riyal.ui.compose.CategoryBadge
import com.alyaqdhan.riyal.ui.compose.CategoryChips
import com.alyaqdhan.riyal.ui.compose.ConnectedChoice
import com.alyaqdhan.riyal.ui.compose.CategoryVisuals
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.compose.rememberCategoryOrder
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import com.alyaqdhan.riyal.ui.compose.typeIcon
import com.alyaqdhan.riyal.ui.compose.typeLabel
import com.alyaqdhan.riyal.ui.compose.typeTone
import com.alyaqdhan.riyal.ui.theme.successColor
import kotlin.math.roundToInt

/**
 * Every category, split into what you spend and what you earn, each showing what it
 * actually cost or brought in over the chosen period. Tapping one opens its records.
 *
 * This is also where custom categories are created and edited - it used to be a block
 * buried in Settings, which is a strange place to manage something you look at daily.
 */
@Composable
fun CategoriesScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenCategory: (String, TimeSlice) -> Unit,
) {
    val txns by vm.txns.collectAsState()
    // Read so a create/rename/delete recomposes the list built from the registry.
    val custom by vm.categories.collectAsState()
    val rules by vm.rules.collectAsState()
    val askEachTime by vm.askEachTime.collectAsState()
    val categoryUse by vm.categoryUse.collectAsState()
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    var slice by remember { mutableStateOf(TimeSlice.thisMonth()) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var showEmpty by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Category?>(null) }
    var addingKeyword by remember { mutableStateOf(false) }

    val expenses = remember(txns, slice, currency, custom) {
        Stats.breakdownIn(txns, slice.start, slice.endExclusive, currency, type = TxnType.EXPENSE)
    }
    val incomes = remember(txns, slice, currency, custom) {
        Stats.breakdownIn(txns, slice.start, slice.endExclusive, currency, type = TxnType.INCOME)
    }
    val counts = remember(txns, slice, custom) {
        txns.filter { slice.contains(it.atMillis) }.groupingBy { it.categoryId }.eachCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = { HelpAction(
                        stringResource(R.string.cats_title),
                        stringResource(R.string.help_categories),
                    ) },
            )
        },
    ) { padding ->
        ScanRefresh(vm, Modifier.padding(padding)) {
        // Counted outside the list: it decides whether the toggle is worth a row at all.
        val hidden = remember(counts) {
            (Categories.forType(TxnType.EXPENSE) + Categories.forType(TxnType.INCOME))
                .count { (counts[it.id] ?: 0) == 0 }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "period") {
                PeriodBar(slice = slice, onChange = { slice = it }, txns = txns)
            }

            item(key = "expense-title") { SectionTitle(stringResource(R.string.cats_spending)) }
            val expenseCats = shown(Categories.forType(TxnType.EXPENSE), counts, showEmpty)
            items(expenseCats, key = { "e-" + it.id }) { cat ->
                val row = expenses.firstOrNull { it.categoryId == cat.id }
                CategoryRow(
                    category = cat,
                    amountMinor = row?.amountMinor ?: 0L,
                    fraction = row?.fraction ?: 0f,
                    count = counts[cat.id] ?: 0,
                    currency = currency,
                    income = false,
                    onClick = { onOpenCategory(cat.id, slice) },
                    onEdit = if (cat.custom) ({ editing = cat }) else null,
                )
            }

            item(key = "income-title") { SectionTitle(stringResource(R.string.cats_income)) }
            val incomeCats = shown(Categories.forType(TxnType.INCOME), counts, showEmpty)
            items(incomeCats, key = { "i-" + it.id }) { cat ->
                val row = incomes.firstOrNull { it.categoryId == cat.id }
                CategoryRow(
                    category = cat,
                    amountMinor = row?.amountMinor ?: 0L,
                    fraction = row?.fraction ?: 0f,
                    count = counts[cat.id] ?: 0,
                    currency = currency,
                    income = true,
                    onClick = { onOpenCategory(cat.id, slice) },
                    onEdit = if (cat.custom) ({ editing = cat }) else null,
                )
            }

            // The categories with nothing in them are still worth having - they are what
            // a record gets filed into next - but this screen is for reading a period,
            // and eight rows of "nothing in this period · OMR 0.000" is most of what was
            // on it. They are one tap away instead.
            if (hidden > 0 || showEmpty) {
                item(key = "show-empty") {
                    TextButton(
                        onClick = { showEmpty = !showEmpty },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            if (showEmpty) stringResource(R.string.cats_hide_empty)
                            else stringResource(R.string.cats_show_all, hidden)
                        )
                    }
                }
            }

            item(key = "transfers") {
                val moved = Stats.transferTotalIn(txns, slice.start, slice.endExclusive, currency)
                if (moved > 0) {
                    MoneyAwareText(
                        stringResource(
                            R.string.cats_transfers_note,
                            bidi(Money.format(moved, currency)),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            item(key = "add") {
                FilledTonalButton(
                    onClick = { editing = Category(id = "", name = "", color = Categories.PALETTE.random()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text(stringResource(R.string.cats_add)) }
            }

            // What the app has been taught, and the only place it can be untaught. A
            // rule re-files past records as well as future ones, which is too much to
            // do invisibly: it should be possible to read back every name that was
            // learned and take any of them away.
            item(key = "learned-title") {
                SectionTitle(stringResource(R.string.cats_learned_title))
            }
            item(key = "learned-intro") {
                Text(
                    stringResource(
                        if (rules.isEmpty() && askEachTime.isEmpty()) R.string.cats_learned_empty
                        else R.string.cats_learned_intro
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            item(key = "learned-add") {
                FilledTonalButton(
                    onClick = { addingKeyword = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) { Text(stringResource(R.string.cats_add_keyword)) }
            }
            val sortedRules = rules.sortedBy { it.pattern }
            items(sortedRules, key = { "rule-" + it.pattern + "-" + it.categoryId }) { rule ->
                LearnedRow(
                    name = rule.pattern,
                    detail = stringResource(
                        R.string.cats_filed_as,
                        Categories.byId(rule.categoryId).name,
                    ),
                    categoryId = rule.categoryId,
                    actionLabel = stringResource(R.string.cats_forget),
                    onAction = { vm.removeRule(rule.pattern) },
                )
            }
            if (askEachTime.isNotEmpty()) {
                item(key = "asked-title") {
                    Text(
                        stringResource(R.string.cats_asked_every_time),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(askEachTime.sorted(), key = { "ask-$it" }) { name ->
                    LearnedRow(
                        name = name,
                        detail = stringResource(R.string.cats_never_rule),
                        categoryId = null,
                        actionLabel = stringResource(R.string.action_remove),
                        onAction = { vm.setAskEachTime(name, false) },
                    )
                }
            }
        }
        }
    }

    if (addingKeyword) {
        KeywordRuleDialog(
            askEachTime = askEachTime,
            categoryUse = categoryUse,
            onSave = { pattern, categoryId ->
                vm.addRule(pattern, categoryId)
                addingKeyword = false
            },
            onDismiss = { addingKeyword = false },
        )
    }

    editing?.let { cat ->
        CategoryEditorDialog(
            category = cat,
            onSave = { name, income, color, icon ->
                if (cat.id.isBlank()) vm.addCategory(name, income, color, icon)
                else vm.updateCategory(cat.id, name, color, icon)
                editing = null
            },
            onDelete = if (cat.id.isNotBlank()) ({
                confirmDelete = cat
                editing = null
            }) else null,
            onDismiss = { editing = null },
        )
    }

    confirmDelete?.let { cat ->
        val pop = rememberDialogPop()
        AlertDialog(
            onDismissRequest = { pop.leave { confirmDelete = null } },
            modifier = Modifier.popIn(pop),
            title = { Text(stringResource(R.string.delete_title, cat.name)) },
            text = {
                Text(
                    stringResource(
                        R.string.cats_delete_body,
                        if (cat.income) Categories.byId(Categories.DEFAULT_INCOME).name
                        else Categories.byId(Categories.DEFAULT_EXPENSE).name,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pop.leave {
                        vm.deleteCategory(cat.id)
                        confirmDelete = null
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pop.leave { confirmDelete = null } }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    amountMinor: Long,
    fraction: Float,
    count: Int,
    currency: String,
    income: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    val used = amountMinor > 0
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(category.id, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (onEdit != null) {
                        TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                    }
                }
                Text(
                    if (count == 0) {
                        stringResource(R.string.cats_nothing_in_period)
                    } else {
                        stringResource(
                            if (income) R.string.cats_share_of_income
                            else R.string.cats_share_of_spending,
                            counted(R.plurals.count_records, count),
                            (fraction * 100).roundToInt(),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (used) {
                    FlatBar(
                        progress = { fraction.coerceIn(0f, 1f) },
                        color = Color(Categories.colorFor(category.id)),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(amountMinor, currency,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        !used -> MaterialTheme.colorScheme.onSurfaceVariant
                        income -> successColor()
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cats_open, category.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * One thing the app has learned: the name, what it does, and how to undo it. Used for
 * both halves of the list, because "a rule" and "a name to ask about" are the same kind
 * of thing to the reader - something remembered about a counterparty.
 */
@Composable
private fun LearnedRow(
    name: String,
    detail: String,
    categoryId: String?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (categoryId != null) CategoryBadge(categoryId, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Teaches a keyword by hand, rather than waiting to correct a record and leaving
 * "Always" on. It writes exactly the same [UserRule] that switch does, so there is one
 * mechanism and one list, not two.
 *
 * The rule is scoped to one side of the ledger on purpose: the same counterparty can
 * pay you and be paid, and a category chosen for one direction must not file the other.
 */
@Composable
private fun KeywordRuleDialog(
    askEachTime: Set<String>,
    categoryUse: Map<String, Int>,
    onSave: (pattern: String, categoryId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var categoryId by remember { mutableStateOf(Categories.DEFAULT_EXPENSE) }
    val order = rememberCategoryOrder(categoryUse)

    val type = if (income) TxnType.INCOME else TxnType.EXPENSE
    val pattern = UserRule.patternOf(text)
    // A name marked "ask me every time" would have its rule dropped the moment it was
    // written, so say so here instead of accepting the word and silently discarding it.
    val blocked = pattern.isNotBlank() && pattern in askEachTime
    // Matching mirrors Categorizer.contains: short ASCII words match whole only.
    val wholeWord = pattern.length <= 4 && pattern.all { it in 'a'..'z' }
    val pop = rememberDialogPop()

    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
        title = { Text(stringResource(R.string.cats_add_keyword)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.cats_keyword_word)) },
                    singleLine = true,
                    isError = blocked,
                )
                ConnectedChoice(
                    options = SIDES,
                    selected = type,
                    onSelect = {
                        income = it == TxnType.INCOME
                        categoryId = Categories.defaultFor(it)
                    },
                    label = { stringResource(typeLabel(it)) },
                    icon = ::typeIcon,
                    tone = { typeTone(it) },
                )
                CategoryChips(
                    type = type,
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                    order = order,
                )
                if (blocked) {
                    Text(
                        stringResource(R.string.cats_keyword_blocked, pattern),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (pattern.isNotBlank()) {
                    Text(
                        stringResource(
                            if (income) R.string.cats_keyword_effect_in
                            else R.string.cats_keyword_effect_out,
                            pattern,
                            Categories.byId(categoryId).name,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (wholeWord) {
                        Text(
                            stringResource(R.string.cats_keyword_whole, pattern),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = pattern.isNotBlank() && !blocked,
                onClick = {
                    val word = pattern
                    val category = categoryId
                    pop.leave { onSave(word, category) }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = { pop.leave(onDismiss) }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Create or rename a user category, and pick its colour from the shared palette. */
@Composable
private fun CategoryEditorDialog(
    category: Category,
    onSave: (name: String, income: Boolean, color: Int, icon: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val isNew = category.id.isBlank()
    var name by remember { mutableStateOf(category.name) }
    var income by remember { mutableStateOf(category.income) }
    var color by remember {
        mutableStateOf(if (category.color != 0) category.color else Categories.PALETTE.first())
    }
    var icon by remember { mutableStateOf(category.icon.ifBlank { CategoryVisuals.KEYS.first() }) }
    val pop = rememberDialogPop()

    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
        title = {
            Text(
                stringResource(if (isNew) R.string.cats_new_title else R.string.cats_edit_title)
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                )
                if (isNew) {
                    // Which side of the ledger a category lives on decides which pickers
                    // offer it, so it is fixed once records start using it.
                    ConnectedChoice(
                        options = SIDES,
                        selected = if (income) TxnType.INCOME else TxnType.EXPENSE,
                        onSelect = { income = it == TxnType.INCOME },
                        label = { stringResource(typeLabel(it)) },
                        icon = ::typeIcon,
                        tone = { typeTone(it) },
                    )
                }
                // Shown above the swatches because the icon is what the badge reads as
                // at a glance in a list; the colour only tells it apart from its
                // neighbours.
                Text(stringResource(R.string.label_icon), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CategoryVisuals.KEYS.forEach { key ->
                        val picked = key == icon
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (picked) Color(color).copy(alpha = 0.24f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                // The tint alone is not enough to say which one is
                                // picked: it is the colour being chosen next to it, and
                                // some of the palette sits close to the theme's own
                                // grey. The ring does not depend on that colour.
                                .then(
                                    if (picked) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape,
                                    ) else Modifier
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(CategoryVisuals.byKey(key)),
                                contentDescription = key,
                                tint = if (picked) Color(color)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                Text(stringResource(R.string.label_colour), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Categories.PALETTE.forEach { swatch ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .clickable { color = swatch },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (swatch == color) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.cats_selected),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                if (onDelete != null) DeleteButton(onClick = { pop.leave(onDelete) })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val chosen = name.trim()
                    val isIncome = income
                    val tint = color
                    val glyph = icon
                    pop.leave { onSave(chosen, isIncome, tint, glyph) }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = { pop.leave(onDismiss) }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The two sides a category or a keyword can be on, as the Add form names them. */
private val SIDES = listOf(TxnType.EXPENSE, TxnType.INCOME)

/**
 * The categories worth a row, most spent first, with the empty ones dropped unless
 * asked for. Ordering by what is in them rather than by the order they were declared
 * is the point: the one category carrying the period should not be the ninth row.
 */
private fun shown(
    cats: List<Category>,
    counts: Map<String, Int>,
    showEmpty: Boolean,
): List<Category> {
    val used = cats.filter { (counts[it.id] ?: 0) > 0 }
        .sortedByDescending { counts[it.id] ?: 0 }
    return if (showEmpty) used + cats.filterNot { it in used } else used
}
