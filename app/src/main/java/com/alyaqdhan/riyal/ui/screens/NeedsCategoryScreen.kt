@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.data.UserRule
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryChips
import com.alyaqdhan.riyal.ui.compose.CategoryOrder
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.localDateOf
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.rememberCategoryOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val recordFmt = DateTimeFormatter.ofPattern("d MMM uu")
private val recordClockFmt = DateTimeFormatter.ofPattern("h:mm a")

/**
 * The backlog of records the scan could not place, as decisions rather than rows.
 *
 * Filing fifty records one at a time is fifty sheets opened, fifty categories picked,
 * fifty sheets dismissed - and next month the same shops come back and it starts
 * again. Here one counterparty is one row, one tap files every record under it, and
 * the answer is saved as a rule so the same shop is never asked about twice.
 *
 * One tap is the fast path, not the only one. A name's records are listed inside its
 * card and each can be taken out of the answer, because "everything from this shop is
 * groceries" is usually true and occasionally not - and a batch you cannot correct is
 * one people stop trusting after it is wrong once.
 *
 * Biggest money first, deliberately. A long backlog is mostly small payments that
 * barely move a total; the few largest merchants are where the distortion lives, so
 * the list puts them where they will be answered first and lets the tail wait.
 */
@Composable
fun NeedsCategoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val archived by vm.archivedIds.collectAsState()
    val deferred by vm.deferredIds.collectAsState()
    val custom by vm.categories.collectAsState()
    val askEachTime by vm.askEachTime.collectAsState()
    // Frozen for the whole screen, not per card: working a backlog files record after
    // record, and every one of those changes the counts. The chips have to sit still
    // while the list they belong to is being emptied.
    val order = rememberCategoryOrder(vm.categoryUse.collectAsState().value)
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }

    val groups = remember(txns, archived, deferred, currency, custom) {
        Stats.unfiledByMerchant(txns, currency, archived, deferred)
    }
    // Records that name nobody cannot be batched: there is nothing to key a rule on.
    // They are still waiting, so the screen says so rather than quietly losing them.
    val unnamed = remember(txns, archived, deferred) {
        Stats.unfiled(txns, archived, deferred).filter { it.merchant.isNullOrBlank() }
    }
    val records = remember(groups) { groups.sumOf { it.count } }
    // Only the ones still here. A deferred id whose record has since been re-parsed
    // away would otherwise inflate the count on the empty state.
    val waiting = remember(txns, deferred) { txns.count { it.id in deferred } }

    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    if (confirmLeave) {
        LeaveAsOtherDialog(
            records = records + unnamed.size,
            onDismiss = { confirmLeave = false },
            onConfirm = {
                confirmLeave = false
                vm.deferAll(groups.flatMap { it.txnIds } + unnamed.map { it.id })
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Needs a category") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (groups.isEmpty() && unnamed.isEmpty()) {
                item(key = "empty") {
                    // "Everything is filed" over "nothing was decided about them" is
                    // the exact confusion the dialog was written to prevent, so the
                    // title has to know the difference too.
                    EmptyState(
                        style = if (waiting > 0) FaceStyle.SLEEPY else FaceStyle.NORMAL,
                        title = if (waiting > 0) "Left for next time" else "Everything is filed",
                        subtitle = when {
                            waiting > 0 ->
                                "$waiting record(s) are under Other for now. Nothing was " +
                                    "decided about them, so the next scan asks again."
                            else -> "Nothing is waiting for a category."
                        },
                        mood = if (waiting > 0) 0f else 0.2f,
                    )
                }
                return@LazyColumn
            }

            if (groups.isNotEmpty()) {
                item(key = "intro") {
                    Text(
                        "$records record(s) from ${groups.size} place(s), biggest first. " +
                            "Filing one files all of its records, and remembers the answer for " +
                            "next month - or open it and pick only the records you mean.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            items(groups, key = { it.merchant.lowercase() + "|" + it.type }) { group ->
                MerchantCard(
                    group = group,
                    marked = UserRule.patternOf(group.merchant) in askEachTime,
                    order = order,
                    onFile = { categoryId, ids -> vm.fileMerchant(group, categoryId, ids) },
                    onAskEachTime = { vm.setAskEachTime(group.merchant, it) },
                )
            }

            if (unnamed.isNotEmpty()) {
                item(key = "unnamed") {
                    Column {
                        SectionTitle("Can't be grouped")
                        Text(
                            "${unnamed.size} record(s) name no shop or person - the bank didn't " +
                                "say who was paid. There is nothing to remember them by, so they " +
                                "stay one-at-a-time from Activity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Last, and worded as what it is. It sits at the end of the backlog rather
            // than the top because it is the thing to reach for once the names worth
            // answering have been answered.
            item(key = "leave") {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    TextButton(onClick = { confirmLeave = true }) {
                        Text("Leave the rest under Other")
                    }
                }
            }
        }
    }
}

/**
 * The notice the request asks for, before anything happens rather than after. Filing
 * a backlog under Other is a reasonable thing to want and a bad thing to do silently:
 * the records stay uncategorised, every total keeps whatever shape it had, and the
 * only real change is that the list is quiet until the next scan. All three are said
 * here so that "leave as Other" is never mistaken for "sorted".
 */
@Composable
private fun LeaveAsOtherDialog(
    records: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave the rest under Other?") },
        text = {
            Text(
                "All $records record(s) still waiting stay exactly where they are, under " +
                    "Other. Nothing is answered and no rule is remembered.\n\n" +
                    "They come back the next time Riyal scans, so this clears the list " +
                    "without deciding anything."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Leave under Other") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * One decision: who, how many, how much, and the categories to put them in. The chips
 * stay folded until asked for, so the list reads as a ranking of what is waiting rather
 * than a wall of every category repeated per row.
 *
 * Opening it also lists the records themselves, each with a tick. They all start
 * ticked, because "all of them" is the answer nearly every time and the fast path must
 * stay one tap; untick the odd one out and the answer covers the rest. Filing part of
 * a name saves no rule - see [MainViewModel.fileMerchant].
 *
 * A [marked] name is still one decision - four records from one person still need a
 * category, and one tap is still better than four. What it loses is the memory: the
 * answer covers these records and no future ones, which the card says out loud so the
 * saving is never mistaken for one it isn't.
 */
@Composable
private fun MerchantCard(
    group: Stats.MerchantGroup,
    marked: Boolean,
    order: CategoryOrder,
    onFile: (String, Set<String>) -> Unit,
    onAskEachTime: (Boolean) -> Unit,
) {
    var open by rememberSaveable(group.merchant, group.type) { mutableStateOf(false) }
    // Keyed on the ids, so a card whose records changed under it starts from "all of
    // them" again rather than holding a selection of records that are no longer there.
    var excluded by remember(group.txnIds) { mutableStateOf(emptySet<String>()) }
    val chosen = remember(group.txnIds, excluded) { group.txnIds.filter { it !in excluded } }

    Card(Modifier.fillMaxWidth().popIn()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        group.merchant,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${group.count} record(s) · " +
                            if (group.type == TxnType.INCOME) "money in" else "money out",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Money.formatAmount(group.amountMinor, group.currency),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        group.currency,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Hide the records" else "Show the records",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (marked) {
                Text(
                    "Asked every time - no rule is saved for this name.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = open,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    for (txn in group.txns) {
                        RecordRow(
                            txn = txn,
                            checked = txn.id !in excluded,
                            onToggle = {
                                excluded = if (txn.id in excluded) {
                                    excluded - txn.id
                                } else {
                                    excluded + txn.id
                                }
                            },
                        )
                    }
                    // Only worth a control once there is a mixed state to get out of.
                    if (excluded.isNotEmpty()) {
                        TextButton(onClick = { excluded = emptySet() }) {
                            Text("Select all ${group.count}")
                        }
                    }
                    HorizontalDivider()

                    CategoryChips(
                        type = group.type,
                        order = order,
                        // One picker per merchant card, several on screen at once: a
                        // filter field on each would cost more room than it saves.
                        allowSearch = false,
                        // Nothing is preselected: the category it currently carries is
                        // the fallback, and showing it as a choice already made is how
                        // a backlog gets "confirmed" without being read.
                        selectedId = "",
                        onSelect = { if (chosen.isNotEmpty()) onFile(it, chosen.toSet()) },
                    )
                    Text(
                        when {
                            chosen.isEmpty() ->
                                "Nothing is ticked, so there is nothing to file."
                            chosen.size < group.count ->
                                "Files the ${chosen.size} you ticked. The other " +
                                    "${group.count - chosen.size} stay here, and no rule is " +
                                    "saved - part of a name is not an answer about the name."
                            marked ->
                                "Files all ${group.count}. You'll be asked again next time."
                            else ->
                                "Files all ${group.count}, and anything from " +
                                    "${group.merchant} later."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!open) {
                    TextButton(onClick = { open = true }) { Text("Pick a category") }
                }
                // Marking someone while working the backlog, rather than having to file
                // them first and undo it afterwards.
                TextButton(onClick = { onAskEachTime(!marked) }) {
                    Text(if (marked) "Remember this name" else "Always ask for this name")
                }
            }
        }
    }
}

/** One record inside a merchant's card: enough to recognise it, and a tick to leave it out. */
@Composable
private fun RecordRow(txn: Txn, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                recordFmt.format(localDateOf(txn.atMillis)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                recordClockFmt.format(
                    Instant.ofEpochMilli(txn.atMillis).atZone(ZoneId.systemDefault())
                ) + " · " + txn.sender,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            Money.formatAmount(txn.amountMinor, txn.currency),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
