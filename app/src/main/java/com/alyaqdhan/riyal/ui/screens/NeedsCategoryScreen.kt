@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.alyaqdhan.riyal.ui.compose.typeIcon
import com.alyaqdhan.riyal.ui.compose.typeLabel
import com.alyaqdhan.riyal.ui.compose.typeTone
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
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.rememberDialogPop
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
import com.alyaqdhan.riyal.ui.compose.SiftBar
import com.alyaqdhan.riyal.ui.compose.localDateOf
import com.alyaqdhan.riyal.ui.compose.rememberSifting
import com.alyaqdhan.riyal.ui.compose.rememberCategoryOrder
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val recordFmt: DateTimeFormatter get() = Dates.pattern("d MMM uu")

/** A name's card, the same across rescans: its name and the side it is on. */
private fun keyOf(group: Stats.MerchantGroup) = group.merchant.lowercase() + "|" + group.type
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
    // Every record still waiting, for the bar that fills as names are filed: one answer
    // can file dozens of records, and the bar jumps by all of them.
    val sifting = rememberSifting(
        remember(groups, unnamed) { groups.flatMapTo(HashSet()) { it.txnIds } + unnamed.map { it.id } }
    )
    // Only the ones still here. A deferred id whose record has since been re-parsed
    // away would otherwise inflate the count on the empty state.
    val waiting = remember(txns, deferred) { txns.count { it.id in deferred } }

    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    // One card open at a time: opening a name folds the last one, so its categories are
    // never pushed down by another card's.
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }

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
        Column {
        // Above the list, so what is left stays in sight while the cards go.
        SiftBar(sifting)
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
                        icon = R.drawable.ic_sym_category,
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

            itemsIndexed(groups, key = { _, group -> keyOf(group) }) { index, group ->
                val key = keyOf(group)
                MerchantCard(
                    group = group,
                    marked = UserRule.patternOf(group.merchant) in askEachTime,
                    order = order,
                    open = openKey == key,
                    // Opens where it is: the list never scrolls out from under a tap.
                    onToggle = { openKey = if (openKey == key) null else key },
                    onFile = { categoryId, ids ->
                        // Saving the whole name takes its card away; the next one opens
                        // in its place, so a backlog is worked through without hunting.
                        if (ids.size == group.count) openKey = groups.getOrNull(index + 1)?.let(::keyOf)
                        vm.fileMerchant(group, categoryId, ids)
                    },
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
                    TextButton(onClick = { confirmLeave = true }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.needs_leave_button))
                    }
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
    val pop = rememberDialogPop()
    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
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
            TextButton(onClick = { pop.leave(onConfirm) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.needs_leave_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { pop.leave(onDismiss) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * One decision: who, how many, how much, and the categories to put them in.
 *
 * Folded, it is a row of the ranking. Opened (one card at a time, in place - see the
 * screen), the categories come straight under its name: a tap picks one, and Save at the
 * end of the card files the records, so nothing vanishes on a stray tap. The records themselves are behind "Choose records":
 * all of them are filed unless some are unticked, which is the answer nearly every
 * time, and a long list of them in the way of the categories was the screen's problem.
 * Filing part of a name saves no rule - see [MainViewModel.fileMerchant].
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
    open: Boolean,
    onToggle: () -> Unit,
    onFile: (String, Set<String>) -> Unit,
    onAskEachTime: (Boolean) -> Unit,
) {
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
    var choosing by rememberSaveable(cardKey) { mutableStateOf(false) }
    var everyRecord by rememberSaveable(cardKey) { mutableStateOf(false) }
    // The category picked for this name, waiting for Save.
    var picked by rememberSaveable(cardKey) { mutableStateOf<String?>(null) }
    val chosen = remember(group.txnIds, excluded) { group.txnIds.filter { it !in excluded } }
    val motion = MaterialTheme.motionScheme
    val turn by animateFloatAsState(if (open) 180f else 0f, motion.defaultSpatialSpec())
    val (toneBg, toneFg) = typeTone(group.type)

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(toneBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(typeIcon(group.type)),
                    contentDescription = null,
                    tint = toneFg,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    group.merchant,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (marked) {
                        stringResource(R.string.needs_always_asked)
                    } else {
                        stringResource(
                            R.string.needs_group_line,
                            counted(R.plurals.count_records, group.count),
                            stringResource(typeLabel(group.type)),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(
                group.amountMinor,
                group.currency,
                sign = MoneySign.NONE,
                style = MaterialTheme.typography.titleMedium,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (open) R.string.needs_hide_records else R.string.needs_show_records
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(turn),
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
        ) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.needs_file_as),
                    style = MaterialTheme.typography.labelLarge,
                )
                CategoryChips(
                    type = group.type,
                    order = order,
                    // Nothing is preselected: the category it currently carries is
                    // the fallback, and showing it as a choice already made is how
                    // a backlog gets "confirmed" without being read.
                    selectedId = picked ?: "",
                    onSelect = { picked = it },
                )
                Text(
                    when {
                        chosen.isEmpty() -> stringResource(R.string.needs_nothing_ticked)
                        chosen.size < group.count -> stringResource(
                            R.string.needs_files_some, chosen.size, group.count
                        )
                        marked -> stringResource(R.string.needs_files_all_asked, group.count)
                        else -> stringResource(R.string.needs_files_all_future, group.count)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chosen.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                HorizontalDivider()
                val plain = ListItemDefaults.colors(containerColor = Color.Transparent)
                val chooseTurn by animateFloatAsState(if (choosing) 180f else 0f, motion.defaultSpatialSpec())
                ListItem(
                    onClick = { choosing = !choosing },
                    colors = plain,
                    contentPadding = PaddingValues(0.dp),
                    supportingContent = {
                        Text(stringResource(R.string.needs_chosen_of, chosen.size, group.count))
                    },
                    trailingContent = {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.rotate(chooseTurn),
                        )
                    },
                ) { Text(stringResource(R.string.needs_choose_records)) }
                AnimatedVisibility(
                    visible = choosing,
                    enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val listed = if (everyRecord) group.txns else group.txns.take(RECORDS_AT_FIRST)
                        for (txn in listed) {
                            RecordRow(
                                txn = txn,
                                checked = txn.id !in excluded,
                                showing = txn.id in showing,
                                onToggle = {
                                    excluded = if (txn.id in excluded) excluded - txn.id else excluded + txn.id
                                },
                                onShow = {
                                    showing = if (txn.id in showing) showing - txn.id else showing + txn.id
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!everyRecord && group.txns.size > RECORDS_AT_FIRST) {
                                TextButton(onClick = { everyRecord = true }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.needs_show_all, group.count))
                                }
                            }
                            // Only worth a control once there is a mixed state to get out of.
                            if (excluded.isNotEmpty()) {
                                TextButton(onClick = { excluded = emptyList() }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(R.string.needs_select_all, group.count))
                                }
                            }
                        }
                    }
                }
                // Marking someone while working the backlog, rather than having to file
                // them first and undo it afterwards: a switch row, like every other
                // setting that stays on.
                ListItem(
                    checked = marked,
                    onCheckedChange = onAskEachTime,
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = Color.Transparent,
                        selectedContentColor = plain.contentColor,
                        selectedSupportingContentColor = plain.supportingContentColor,
                        selectedTrailingContentColor = plain.trailingContentColor,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    trailingContent = { Switch(checked = marked, onCheckedChange = null) },
                ) { Text(stringResource(R.string.needs_always_ask_name)) }
                // A category tapped is only picked; nothing is filed until this.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { picked?.let { onFile(it, chosen.toSet()) } },
                        enabled = picked != null && chosen.isNotEmpty(),
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

/** Records listed when a card's records are first opened; the rest are one tap away. */
private const val RECORDS_AT_FIRST = 5

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

        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = showing,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
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
