@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MailOutline
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
import androidx.compose.ui.text.style.TextDirection
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
import com.alyaqdhan.riyal.ui.compose.countOf
import com.alyaqdhan.riyal.ui.compose.HelpAction
import com.alyaqdhan.riyal.ui.compose.HelpNote
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.localDateOf
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.rememberCategoryOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything the screen used to say on the page. It is one explanation, read once, and
 * it sat above a list that is the reason anyone opens this screen.
 */
private const val HELP =
    "One row is one place you paid, biggest first. Filing a row files every record " +
        "under it and remembers the answer, so the same place is never asked about " +
        "twice.\n\n" +
        "Open a row to see its records and read the message each came from. Untick any " +
        "and only the ticked ones are filed, with no rule saved: part of a name is not " +
        "an answer about the name.\n\n" +
        "\"Leave the rest under Other\" clears the list without answering it. Those " +
        "records keep the category they fell back to, and the next scan asks again."

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
    val helpOnPage by vm.helpShown.collectAsState()
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
                actions = { HelpAction("Needs a category", HELP) },
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
                        subtitle = if (waiting > 0) {
                            countOf(waiting, "record") + " are under Other. The next scan asks again."
                        } else {
                            "Nothing is waiting for a category."
                        },
                        mood = if (waiting > 0) 0f else 0.2f,
                    )
                }
                return@LazyColumn
            }

            if (groups.isNotEmpty()) {
                item(key = "intro") {
                    Column {
                        // What is left to do, and nothing else. The paragraph that used
                        // to be here is behind the (i), or on the page for someone who
                        // asked for that in Settings.
                        Text(
                            countOf(records, "record") + " · " + countOf(groups.size, "place"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        HelpNote(HELP, visible = helpOnPage)
                    }
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
                            countOf(unnamed.size, "record") + " name nobody. File them from Activity.",
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
                "All $records stay under Other. Nothing is answered, no rule is saved, " +
                    "and the next scan asks about them again."
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
    //
    // Saveable, not merely remembered: a rotation or a theme change rebuilds the
    // activity, and silently restoring every tick under someone who had just unticked
    // three of ten records turns their next tap into filing all ten. Held as a list
    // because that is what survives a Bundle.
    val cardKey = remember(group.txnIds) { group.txnIds.joinToString(",") }
    var excluded by rememberSaveable(cardKey) { mutableStateOf(listOf<String>()) }
    // Which messages are open. Several at once, because deciding between two records
    // usually means reading both.
    var showing by rememberSaveable(cardKey) { mutableStateOf(listOf<String>()) }
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
                        countOf(group.count, "record") + " · " +
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
                    "Always asked · no rule saved",
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
                            showing = txn.id in showing,
                            onToggle = {
                                excluded = if (txn.id in excluded) {
                                    excluded - txn.id
                                } else {
                                    excluded + txn.id
                                }
                            },
                            onShow = {
                                showing = if (txn.id in showing) {
                                    showing - txn.id
                                } else {
                                    showing + txn.id
                                }
                            },
                        )
                    }
                    // Only worth a control once there is a mixed state to get out of.
                    if (excluded.isNotEmpty()) {
                        TextButton(onClick = { excluded = emptyList() }) {
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
                    // One short line, and only the part that is not already obvious
                    // from the ticks above it. The reasoning moved into the help.
                    Text(
                        when {
                            chosen.isEmpty() -> "Nothing ticked"
                            chosen.size < group.count ->
                                "Files ${chosen.size} of ${group.count} · no rule saved"
                            marked -> "Files all ${group.count} · asked again next time"
                            else -> "Files all ${group.count} · and future ones"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Marking someone while working the backlog, rather than having to
                    // file them first and undo it afterwards. Inside the card, because
                    // it is the rarer of the two things to do with a name and it was
                    // costing a line of text on every row at rest.
                    TextButton(
                        onClick = { onAskEachTime(!marked) },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(if (marked) "Remember this name" else "Always ask for this name")
                    }
                }
            }


        }
    }
}

/**
 * One record inside a merchant's card: enough to recognise it, a tick to leave it out,
 * and the message it was read from.
 *
 * The message is the whole point of the row. "ALSUTUE ALAMTE TRAD BAH" is not a shop
 * anyone recognises, and a category picked without reading what the bank actually said
 * is a guess - so the text is one tap away, on the row itself, rather than somewhere
 * else in the app that loses the place in the backlog to get to.
 *
 * The tick and the message are separate targets on purpose. Tapping a row to read it
 * must never quietly change what is about to be filed, so selection stays on the
 * checkbox alone and everything else in the row opens the text.
 */
@Composable
private fun RecordRow(
    txn: Txn,
    checked: Boolean,
    showing: Boolean,
    onToggle: () -> Unit,
    onShow: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onShow),
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
            Icon(
                Icons.Outlined.MailOutline,
                contentDescription = if (showing) "Hide the message" else "Read the message",
                tint = if (showing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(
            visible = showing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            // Quoted, not restated: the bank's own words, in a block of their own, with
            // no attempt to tidy the mixed Arabic and English the message arrived in.
            //
            // Outlined rather than only tinted. Inside a card the container colours sit
            // a shade apart at most, which in the dark theme left the message looking
            // like loose text rather than something quoted - the border reads as an
            // edge in both themes, whatever the fill underneath happens to be.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            ) {
                SelectionContainer {
                    Text(
                        txn.body,
                        // The app's layout is left-to-right, so an Arabic message was
                        // being aligned to the left edge - every line ending where an
                        // Arabic reader starts. Content direction lets each message
                        // take the side its own first letter asks for, which is what
                        // makes a bilingual inbox readable in one place.
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = TextDirection.Content,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
        }
    }
}
