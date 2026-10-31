@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.MessageKind
import com.alyaqdhan.riyal.data.SenderLists
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.ActionButton
import com.alyaqdhan.riyal.ui.compose.CategoryBadge
import com.alyaqdhan.riyal.ui.compose.ChoiceMenu
import com.alyaqdhan.riyal.ui.compose.ConfirmDeleteButton
import com.alyaqdhan.riyal.ui.compose.MenuChoice
import com.alyaqdhan.riyal.ui.compose.ScrollThumb
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.SwipeAction
import com.alyaqdhan.riyal.ui.compose.SwipeRow
import com.alyaqdhan.riyal.ui.compose.SwipeTour
import com.alyaqdhan.riyal.ui.compose.SwipeWay
import com.alyaqdhan.riyal.ui.compose.rememberSwipeTour
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.relativeTime
import com.alyaqdhan.riyal.ui.compose.rememberDialogPop
import kotlinx.coroutines.launch

/** The three lists, in the order their tabs sit. */
enum class ListsTab(@param:StringRes val label: Int) {
    KEYWORDS(R.string.lists_tab_keywords),
    SENDERS(R.string.lists_tab_senders),
    IGNORED(R.string.lists_tab_ignored),
}

/**
 * Settings › Lists: what Riyal reads by and what it leaves out, a tab each, and every
 * entry one that can be taken back.
 *
 *  - Keywords: names filed under a category without asking, names always asked about,
 *    and the kinds of message the user taught.
 *  - Senders: everyone who has texted, and whether each is read.
 *  - Ignored: the senders never read, and the kinds of message dismissed for good.
 */
@Composable
fun ListsScreen(vm: MainViewModel, onBack: () -> Unit) {
    // The lists lie side by side: a tab slides over to its list, and a finger can drag
    // from one list to the next. The tab follows where the pager is headed, so a tap
    // lights it at once, as the slide starts.
    val pager = rememberPagerState { ListsTab.entries.size }
    val scope = rememberCoroutineScope()
    val show: (ListsTab) -> Unit = { t -> scope.launch { pager.animateScrollToPage(t.ordinal) } }
    // Every entry's Forget or Restore waits behind a swipe, so the first time the page is
    // seen, the first entry of the list showing slides open by itself to say so.
    val tour = rememberSwipeTour(
        seen = vm.prefs.listsTourSeen,
        steps = listOf(SwipeTour.Step(SwipeWay.LEFT, R.string.lists_tour_title, R.string.lists_tour_text)),
        onDone = { vm.prefs.listsTourSeen = true },
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_lists)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = pager.targetPage) {
                ListsTab.entries.forEach { t ->
                    Tab(
                        selected = pager.targetPage == t.ordinal,
                        onClick = { show(t) },
                        text = { Text(stringResource(t.label)) },
                    )
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                // Only the list the pager has settled on plays it: a neighbour drawn while
                // dragging would open a row half off the screen.
                val pageTour = tour.takeIf { pager.settledPage == page && !pager.isScrollInProgress }
                when (ListsTab.entries[page]) {
                    ListsTab.KEYWORDS -> KeywordsList(vm, pageTour)
                    ListsTab.SENDERS -> SendersList(vm)
                    ListsTab.IGNORED -> IgnoredList(vm, pageTour, onFindSender = { show(ListsTab.SENDERS) })
                }
            }
        }
    }
}

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

/**
 * Names filed under a category without asking - learned from a correction left on
 * "Always", or added here - and names always asked about; then the kinds of message
 * the user answered, which every message written the same way follows.
 */
@Composable
private fun KeywordsList(vm: MainViewModel, tour: SwipeTour?) {
    val rules by vm.rules.collectAsState()
    val askEachTime by vm.askEachTime.collectAsState()
    val categoryUse by vm.categoryUse.collectAsState()
    val labels by vm.labels.collectAsState()
    val templates by vm.templates.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val samples = remember(templates) { templates.associateBy { it.key } }
    val taught = remember(labels) { labels.entries.sortedByDescending { it.value.atMillis } }
    val sortedRules = remember(rules) { rules.sortedBy { it.pattern } }
    val asked = remember(askEachTime) { askEachTime.sorted() }
    // The tour plays on the first entry, whichever list it heads.
    val touring = sortedRules.firstOrNull()?.let { "rule-" + it.pattern + "-" + it.categoryId }
        ?: asked.firstOrNull()?.let { "ask-$it" }
        ?: taught.firstOrNull()?.let { "taught-" + it.key }

    val list = rememberLazyListState()
    ScrollThumb(list) {
    LazyColumn(state = list, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "learned-title") { SectionTitle(stringResource(R.string.cats_learned_title)) }
        item(key = "learned-intro") {
            Intro(
                stringResource(
                    if (rules.isEmpty() && askEachTime.isEmpty()) R.string.cats_learned_empty
                    else R.string.cats_learned_intro
                )
            )
        }
        item(key = "learned-add") {
            ActionButton(
                text = stringResource(R.string.cats_add_keyword),
                icon = R.drawable.ic_sym_add,
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                tonal = true,
            )
        }
        items(sortedRules, key = { "rule-" + it.pattern + "-" + it.categoryId }) { rule ->
            ListRow(
                title = rule.pattern,
                detail = stringResource(R.string.cats_filed_as, Categories.byId(rule.categoryId).name),
                leading = { CategoryBadge(rule.categoryId, size = 32.dp) },
                action = forget { vm.removeRule(rule.pattern) },
                tour = tour.takeIf { touring == "rule-" + rule.pattern + "-" + rule.categoryId },
            )
        }
        if (asked.isNotEmpty()) {
            item(key = "asked-title") {
                Text(
                    stringResource(R.string.cats_asked_every_time),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            items(asked, key = { "ask-$it" }) { name ->
                ListRow(
                    title = name,
                    detail = stringResource(R.string.cats_never_rule),
                    action = SwipeAction(
                        label = stringResource(R.string.action_remove),
                        icon = R.drawable.ic_sym_delete,
                        danger = true,
                        onClick = { vm.setAskEachTime(name, false) },
                    ),
                    tour = tour.takeIf { touring == "ask-$name" },
                )
            }
        }

        item(key = "taught-title") {
            SectionTitle(stringResource(R.string.lists_taught_title), Modifier.padding(top = 12.dp))
        }
        item(key = "taught-intro") {
            Intro(stringResource(if (taught.isEmpty()) R.string.lists_taught_empty else R.string.lists_taught_intro))
        }
        items(taught, key = { "taught-" + it.key }) { (key, label) ->
            val sample = samples[key]
            val (container, content) = kindTone(label.kind)
            ListRow(
                // A message it was taught on reads better than the template itself; the
                // template is all there is when no message of the kind is in the inbox.
                title = sample?.sampleBody?.takeIf { it.isNotBlank() } ?: key.substringAfter('|'),
                titleLines = 3,
                detail = listOfNotNull(
                    sample?.sender ?: key.substringBefore('|'),
                    stringResource(kindLabel(label.kind)),
                    // The account it was taught to go to, for a sender that holds none.
                    label.accountId?.let { id -> accounts.firstOrNull { it.id == id } }?.let {
                        stringResource(
                            if (label.kind == MessageKind.OUT) R.string.lists_taught_from else R.string.lists_taught_into,
                            it.displayName,
                        )
                    },
                    sample?.count?.takeIf { it > 0 }?.let { counted(R.plurals.count_messages, it) },
                ).joinToString(" · "),
                leading = { Badge(kindIcon(label.kind), container, content) },
                action = forget { vm.forgetTaught(key) },
                tour = tour.takeIf { touring == "taught-$key" },
            )
        }
    }
    }

    if (adding) {
        KeywordRuleDialog(
            askEachTime = askEachTime,
            categoryUse = categoryUse,
            onSave = { pattern, categoryId ->
                vm.addRule(pattern, categoryId)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

/** What a sender can be set to, in the order the menu offers it. */
private enum class SenderChoice(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    READ(R.string.lists_choice_read, R.drawable.ic_sym_mail_read),
    LEAVE_OUT(R.string.lists_choice_leave_out, R.drawable.ic_sym_sms),
    IGNORE(R.string.lists_choice_ignore, R.drawable.ic_sym_block),
}

/** Search appears once the list is longer than a screen is likely to show. */
private const val SEARCH_FROM = 8

/**
 * Everyone who has texted, as the scans found them: read first, then left out, then
 * ignored. A tap opens what the sender can be - read, left out, ignored - with the one
 * it is now ticked; ignoring one that holds accounts asks first, because they go too.
 */
@Composable
private fun SendersList(vm: MainViewModel) {
    val senders by vm.senders.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val ignored by vm.ignoredSenders.collectAsState()
    val added by vm.addedSenders.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf<String?>(null) }
    val rows = remember(senders, accounts, ignored, added) { SenderLists.rows(senders, accounts, ignored, added) }
    val shown = remember(rows, query) {
        if (query.isBlank()) rows else rows.filter { it.sender.contains(query.trim(), ignoreCase = true) }
    }

    val list = rememberLazyListState()
    ScrollThumb(list) {
    LazyColumn(state = list, contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item(key = "intro") {
            Intro(
                stringResource(if (rows.isEmpty()) R.string.lists_senders_empty else R.string.lists_senders_intro),
                Modifier.padding(horizontal = 16.dp),
            )
        }
        if (rows.size > SEARCH_FROM) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.lists_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(shown, key = { "sender-" + it.sender.lowercase() }, contentType = { "sender" }) { row ->
            SenderRow(row) { choice ->
                when (choice) {
                    SenderChoice.READ ->
                        if (row.status == SenderLists.Status.IGNORED && row.bank) vm.restoreSender(row.sender)
                        else vm.readSender(row.sender)
                    SenderChoice.LEAVE_OUT -> vm.leaveOutSender(row.sender)
                    SenderChoice.IGNORE -> {
                        val key = row.sender.trim().lowercase()
                        val holds = accounts.any { a -> a.senderIds.any { it.trim().lowercase() == key } }
                        if (holds) confirming = row.sender else vm.ignoreSender(row.sender)
                    }
                }
            }
        }
    }
    }

    confirming?.let { sender ->
        val pop = rememberDialogPop()
        AlertDialog(
            onDismissRequest = { pop.leave { confirming = null } },
            modifier = Modifier.popIn(pop),
            title = { Text(stringResource(R.string.setup_not_my_bank_title, sender)) },
            text = { Text(stringResource(R.string.setup_not_my_bank_body)) },
            confirmButton = {
                ConfirmDeleteButton(stringResource(R.string.lists_choice_ignore)) {
                    pop.leave {
                        confirming = null
                        vm.ignoreSender(sender)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pop.leave { confirming = null } }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SenderRow(row: SenderLists.Row, onChoose: (SenderChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val (icon, status) = when (row.status) {
        SenderLists.Status.BANK -> R.drawable.ic_sym_account_balance to R.string.lists_status_bank
        SenderLists.Status.ADDED -> R.drawable.ic_sym_check_circle to R.string.lists_status_added
        SenderLists.Status.LEFT_OUT -> R.drawable.ic_sym_sms to R.string.lists_status_left_out
        SenderLists.Status.PHONE -> R.drawable.ic_sym_sms to R.string.lists_status_phone
        SenderLists.Status.IGNORED -> R.drawable.ic_sym_block to R.string.lists_status_ignored
    }
    val now = when (row.status) {
        SenderLists.Status.BANK, SenderLists.Status.ADDED -> SenderChoice.READ
        SenderLists.Status.LEFT_OUT, SenderLists.Status.PHONE -> SenderChoice.LEAVE_OUT
        SenderLists.Status.IGNORED -> SenderChoice.IGNORE
    }
    // A bank is read unless it is ignored; there is no leaving one out.
    val choices = if (row.bank) listOf(SenderChoice.READ, SenderChoice.IGNORE) else SenderChoice.entries
    Box {
        ListItem(
            onClick = { open = true },
            leadingContent = {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (row.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            supportingContent = { Text(stringResource(status)) },
            trailingContent = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) { Text(row.sender, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        ChoiceMenu(expanded = open, onDismiss = { open = false }) {
            choices.forEachIndexed { index, choice ->
                MenuChoice(
                    text = stringResource(choice.label),
                    selected = choice == now,
                    index = index,
                    count = choices.size,
                    icon = choice.icon,
                ) {
                    open = false
                    if (choice != now) onChoose(choice)
                }
            }
        }
    }
}

/**
 * The senders never read, and the kinds of message dismissed for good - each one a tap
 * from coming back.
 */
@Composable
private fun IgnoredList(vm: MainViewModel, tour: SwipeTour?, onFindSender: () -> Unit) {
    val ignored by vm.ignoredSenders.collectAsState()
    val muted by vm.muted.collectAsState()
    val senders = remember(ignored) { ignored.sortedBy { it.lowercase() } }
    val kinds = remember(muted) { muted.sortedByDescending { it.at } }
    val (container, content) = MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    val touring = senders.firstOrNull()?.let { "ignored-" + it.lowercase() }
        ?: kinds.firstOrNull()?.let { "kind-" + it.template }

    val list = rememberLazyListState()
    ScrollThumb(list) {
    LazyColumn(state = list, contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "senders-title") { SectionTitle(stringResource(R.string.lists_ignored_senders)) }
        item(key = "senders-intro") {
            Intro(
                stringResource(
                    if (senders.isEmpty()) R.string.lists_ignored_senders_empty
                    else R.string.lists_ignored_senders_intro
                )
            )
        }
        items(senders, key = { "ignored-" + it.lowercase() }) { sender ->
            ListRow(
                title = sender,
                detail = stringResource(R.string.lists_status_ignored),
                leading = { Badge(R.drawable.ic_sym_block, container, content) },
                action = restore { vm.restoreSender(sender) },
                tour = tour.takeIf { touring == "ignored-" + sender.lowercase() },
            )
        }
        item(key = "find") {
            TextButton(onClick = onFindSender, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.lists_find_sender))
            }
        }

        item(key = "kinds-title") {
            SectionTitle(stringResource(R.string.lists_dismissed_title), Modifier.padding(top = 12.dp))
        }
        item(key = "kinds-intro") {
            Intro(stringResource(if (kinds.isEmpty()) R.string.lists_dismissed_empty else R.string.lists_dismissed_intro))
        }
        items(kinds, key = { "kind-" + it.template }) { kind ->
            ListRow(
                title = kind.sample.ifBlank { kind.template.substringAfter('|') },
                titleLines = 3,
                detail = stringResource(R.string.lists_kind_detail, kind.sender, relativeTime(kind.at)),
                leading = { Badge(R.drawable.ic_sym_inbox, container, content) },
                action = restore { vm.undismissKind(kind) },
                tour = tour.takeIf { touring == "kind-" + kind.template },
            )
        }
    }
    }
}

@Composable
private fun Intro(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

/** A round icon in a tone, at a list entry's start. */
@Composable
private fun Badge(@DrawableRes icon: Int, container: Color, content: Color) {
    Box(Modifier.size(32.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
    }
}

/** Forgetting a learned entry: it takes something away, so it is drawn as removing does. */
@Composable
private fun forget(onClick: () -> Unit) = SwipeAction(
    label = stringResource(R.string.cats_forget),
    icon = R.drawable.ic_sym_delete,
    danger = true,
    onClick = onClick,
)

/** Restoring an ignored entry: it brings something back, so it is drawn as archiving is. */
@Composable
private fun restore(onClick: () -> Unit) = SwipeAction(
    label = stringResource(R.string.action_restore),
    icon = R.drawable.ic_sym_restore,
    danger = false,
    onClick = onClick,
)

private val ListRowShape = RoundedCornerShape(18.dp)

/**
 * One entry of a list: what it is, what it means, and - behind a swipe to the left, as a
 * record's Remove is - the one way to take it back. The swipe only uncovers the button;
 * tapping it is the confirmation. A tinted card rather than a bare row, because every
 * entry is the same kind of thing to the reader - something remembered.
 */
@Composable
private fun ListRow(
    title: String,
    detail: String,
    action: SwipeAction,
    leading: (@Composable () -> Unit)? = null,
    titleLines: Int = Int.MAX_VALUE,
    tour: SwipeTour? = null,
) {
    SwipeRow(dragLeft = action, shape = ListRowShape, tour = tour) {
        Surface(
            shape = ListRowShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
