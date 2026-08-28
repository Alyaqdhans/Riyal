@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Category
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryBadge
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.pressBounce
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
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    var slice by remember { mutableStateOf(TimeSlice.thisMonth()) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var confirmDelete by remember { mutableStateOf<Category?>(null) }

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
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "period") {
                PeriodBar(slice = slice, onChange = { slice = it }, txns = txns)
            }

            item(key = "expense-title") { SectionTitle("Spending") }
            val expenseCats = Categories.forType(TxnType.EXPENSE)
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

            item(key = "income-title") { SectionTitle("Income") }
            val incomeCats = Categories.forType(TxnType.INCOME)
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

            item(key = "transfers") {
                val moved = Stats.transferTotalIn(txns, slice.start, slice.endExclusive, currency)
                if (moved > 0) {
                    Text(
                        "${Money.format(moved, currency)} moved between your own accounts in this " +
                            "period. Transfers have no category — they're neither spending nor income.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            item(key = "add") {
                FilledTonalButton(
                    onClick = { editing = Category(id = "", name = "", color = Categories.PALETTE.random()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).pressBounce(),
                ) { Text("Add a category") }
            }

            // What the app has been taught, and the only place it can be untaught. A
            // rule re-files past records as well as future ones, which is too much to
            // do invisibly: it should be possible to read back every name that was
            // learned and take any of them away.
            item(key = "learned-title") { SectionTitle("What Riyal has learned") }
            item(key = "learned-intro") {
                Text(
                    if (rules.isEmpty() && askEachTime.isEmpty()) {
                        "Nothing yet. Filing a record with \"Always\" left on saves the name " +
                            "here, and every later message mentioning it is filed the same way " +
                            "without asking."
                    } else {
                        "Names filed without asking, and names always asked about. Forgetting " +
                            "a rule also re-answers the records it had filed."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            val sortedRules = rules.sortedBy { it.pattern }
            items(sortedRules, key = { "rule-" + it.pattern + "-" + it.categoryId }) { rule ->
                LearnedRow(
                    name = rule.pattern,
                    detail = "filed as ${Categories.byId(rule.categoryId).name}",
                    categoryId = rule.categoryId,
                    actionLabel = "Forget",
                    onAction = { vm.removeRule(rule.pattern) },
                )
            }
            if (askEachTime.isNotEmpty()) {
                item(key = "asked-title") {
                    Text(
                        "Asked every time",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(askEachTime.sorted(), key = { "ask-$it" }) { name ->
                    LearnedRow(
                        name = name,
                        detail = "never saved as a rule",
                        categoryId = null,
                        actionLabel = "Remove",
                        onAction = { vm.setAskEachTime(name, false) },
                    )
                }
            }
        }
    }

    editing?.let { cat ->
        CategoryEditorDialog(
            category = cat,
            onSave = { name, income, color ->
                if (cat.id.isBlank()) vm.addCategory(name, income, color)
                else vm.updateCategory(cat.id, name, color)
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
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${cat.name}?") },
            text = {
                Text(
                    "Records filed under it move to " +
                        (if (cat.income) Categories.byId(Categories.DEFAULT_INCOME).name
                        else Categories.byId(Categories.DEFAULT_EXPENSE).name) +
                        ". Nothing is lost."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(cat.id)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
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
        modifier = Modifier.fillMaxWidth().popIn().pressBounce(0.98f),
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
                        TextButton(onClick = onEdit) { Text("Edit") }
                    }
                }
                Text(
                    if (count == 0) "nothing in this period"
                    else "$count record(s) · ${(fraction * 100).roundToInt()}% of ${if (income) "income" else "spending"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (used) {
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        color = Color(Categories.colorFor(category.id)),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(amountMinor, currency),
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
                contentDescription = "Open ${category.name}",
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
        modifier = Modifier.fillMaxWidth().popIn(),
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

/** Create or rename a user category, and pick its colour from the shared palette. */
@Composable
private fun CategoryEditorDialog(
    category: Category,
    onSave: (name: String, income: Boolean, color: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val isNew = category.id.isBlank()
    var name by remember { mutableStateOf(category.name) }
    var income by remember { mutableStateOf(category.income) }
    var color by remember {
        mutableStateOf(if (category.color != 0) category.color else Categories.PALETTE.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New category" else "Edit category") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                if (isNew) {
                    // Which side of the ledger a category lives on decides which pickers
                    // offer it, so it is fixed once records start using it.
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !income,
                            onClick = { income = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Expense") }
                        SegmentedButton(
                            selected = income,
                            onClick = { income = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("Income") }
                    }
                }
                Text("Colour", style = MaterialTheme.typography.labelLarge)
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
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), income, color) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
