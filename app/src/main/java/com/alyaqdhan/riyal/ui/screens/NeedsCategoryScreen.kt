@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.data.UserRule
import com.alyaqdhan.riyal.ui.compose.MoneySign
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryChips
import com.alyaqdhan.riyal.ui.compose.CategoryOrder
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.bidi
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.HelpAction
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.localDateOf
import com.alyaqdhan.riyal.ui.compose.rememberCategoryOrder
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val recordFmt: DateTimeFormatter get() = Dates.pattern("d MMM uu")
private val recordClockFmt: DateTimeFormatter get() = Dates.pattern("h:mm a")

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
                title = { Text(stringResource(R.string.needs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = { HelpAction(
                        stringResource(R.string.needs_title),
                        stringResource(R.string.help_needs_category),
                    ) },
            )
        },
    ) { padding ->
        ScanRefresh(vm, Modifier.padding(padding)) {
        LazyColumn(
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
                        title = stringResource(
                            if (waiting > 0) R.string.needs_left_title
                            else R.string.needs_all_filed_title
                        ),
                        subtitle = if (waiting > 0) {
                            stringResource(
                                R.string.needs_left_subtitle,
                                counted(R.plurals.count_records, waiting),
                            )
                        } else {
                            stringResource(R.string.needs_nothing_waiting)
                        },
                        mood = if (waiting > 0) 0f else 0.2f,
                    )
                }
                return@LazyColumn
            }

            if (groups.isNotEmpty()) {
                item(key = "intro") {
                    // What is left to do, and nothing else. The explanation is behind
                    // the (i) and only there.
                    Text(
                        stringResource(
                            R.string.needs_intro,
                            counted(R.plurals.count_records, records),
                            counted(R.plurals.count_places, groups.size),
                        ),
                        style = MaterialTheme.typography.labelLarge,
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
                        SectionTitle(stringResource(R.string.needs_cant_group))
                        Text(
                            stringResource(
                                R.string.needs_unnamed,
                                counted(R.plurals.count_records, unnamed.size),
                            ),
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
                        Text(stringResource(R.string.needs_leave_button))
                    }
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
        title = { Text(stringResource(R.string.needs_leave_title)) },
        text = {
            Text(
                stringResource(
                    R.string.needs_leave_body,
                    counted(R.plurals.count_records, records),
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.needs_leave_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
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

    Card(Modifier.fillMaxWidth()) {
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
                        stringResource(
                            R.string.needs_group_line,
                            counted(R.plurals.count_records, group.count),
                            stringResource(
                                if (group.type == TxnType.INCOME) R.string.label_money_in
                                else R.string.label_money_out
                            ),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(
                        group.amountMinor,
                        group.currency,
                        sign = MoneySign.NONE,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (open) R.string.needs_hide_records else R.string.needs_show_records
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (marked) {
                Text(
                    stringResource(R.string.needs_always_asked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (open) {
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
                            Text(stringResource(R.string.needs_select_all, group.count))
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
                            chosen.isEmpty() -> stringResource(R.string.needs_nothing_ticked)
                            chosen.size < group.count -> stringResource(
                                R.string.needs_files_some, chosen.size, group.count
                            )
                            marked -> stringResource(
                                R.string.needs_files_all_asked, group.count
                            )
                            else -> stringResource(
                                R.string.needs_files_all_future, group.count
                            )
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
                        Text(
                            stringResource(
                                if (marked) R.string.needs_remember_name
                                else R.string.needs_always_ask_name
                            )
                        )
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
                    stringResource(
                        R.string.row_account_time,
                        recordClockFmt.format(
                            Instant.ofEpochMilli(txn.atMillis).atZone(ZoneId.systemDefault())
                        ),
                        txn.sender,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MoneyText(
                txn.amountMinor,
                txn.currency,
                sign = MoneySign.NONE,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                Icons.Outlined.MailOutline,
                contentDescription = stringResource(
                    if (showing) R.string.needs_hide_message else R.string.needs_read_message
                ),
                tint = if (showing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }

        if (showing) {
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
