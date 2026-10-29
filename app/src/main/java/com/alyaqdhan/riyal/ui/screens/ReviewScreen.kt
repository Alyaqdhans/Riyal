@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.ui.res.painterResource
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import com.alyaqdhan.riyal.ui.compose.typeIcon
import com.alyaqdhan.riyal.ui.compose.typeLabel
import com.alyaqdhan.riyal.ui.compose.typeTone
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.MessageKind
import com.alyaqdhan.riyal.data.MsgTemplate
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.reasonText
import com.alyaqdhan.riyal.data.TransferProposal
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.compose.MoneyAwareText
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.ConnectedChoice
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.bidi
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.Face
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.HelpAction
import com.alyaqdhan.riyal.ui.compose.ManualTxnDialog
import com.alyaqdhan.riyal.ui.compose.SummaryPill
import com.alyaqdhan.riyal.ui.theme.successColor
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val reviewDateFmt: DateTimeFormatter get() = Dates.pattern("dd MMM uuuu, h:mm a")

/**
 * Inner page (opened from the Home "Needs review" section): messages that matched the
 * keywords but could not be read automatically. Nothing was recorded for them, the
 * user decides what each one was, or dismisses it. With "Remember" checked the choice
 * teaches the app: dismissing hides similar messages too (restorable below), recording
 * marks that kind of message as wanted.
 */
/** The item the manual dialog is open for, and what is already decided about it. */
private data class Resolving(
    val item: ReviewItem,
    val learnSimilar: Boolean,
    /** Set when the user already answered "money out" or "money in" on the card. */
    val type: TxnType? = null,
)

@Composable
fun ReviewScreen(vm: MainViewModel, onBack: () -> Unit) {
    val reviews by vm.reviews.collectAsState()
    val transfers by vm.pendingTransfers.collectAsState()
    val allTransfers by vm.transfers.collectAsState()
    val autoConfirmOn by vm.autoConfirmOn.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categoryUse by vm.categoryUse.collectAsState()
    // Confirmed pairs never reach this queue, so the page says so rather than looking
    // as if the app found nothing.
    val autoConfirmed = remember(allTransfers) {
        allTransfers.count { it.state == TransferProposal.STATE_ACCEPTED }
    }
    val autoNote = if (autoConfirmOn && autoConfirmed > 0) {
        stringResource(
            R.string.review_auto_note,
            counted(R.plurals.count_matching_pairs, autoConfirmed),
        )
    } else {
        null
    }
    val pending = remember(reviews) { reviews.filter { it.state == ReviewItem.STATE_PENDING } }
    val dismissed = remember(reviews) { reviews.filter { it.state == ReviewItem.STATE_DISMISSED } }
    // What the manual dialog is finishing. A direction-only item arrives with its
    // amount and the answer already given, so the dialog opens on the category rather
    // than on an empty amount field.
    var resolving by remember { mutableStateOf<Resolving?>(null) }
    var showDismissed by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val merged = stringResource(R.string.review_merged)
    val keptSeparate = stringResource(R.string.review_kept_separate)
    val restored = stringResource(R.string.review_restored)
    val dismissedSimilar = stringResource(R.string.review_dismissed_plus_similar)
    val dismissedFuture = stringResource(R.string.review_dismissed_future)
    val dismissedOne = stringResource(R.string.review_dismissed_one)
    val undo = stringResource(R.string.action_undo)
    val taught = stringResource(R.string.review_taught)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = { HelpAction(
                        stringResource(R.string.review_title),
                        stringResource(R.string.help_review),
                    ) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ScanRefresh(vm, Modifier.padding(padding)) {
        Column {
            if (pending.isEmpty() && dismissed.isEmpty() && transfers.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EmptyState(
                        title = stringResource(R.string.review_all_clear_title),
                        subtitle = stringResource(R.string.review_all_clear_subtitle) +
                            (autoNote?.let { "\n\n$it" } ?: ""),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (transfers.isNotEmpty()) {
                        item(key = "transfer-header") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(
                                        R.string.review_transfers_header,
                                        transfers.size,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(R.string.review_transfers_intro),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(transfers, key = { "t-" + it.id }) { proposal ->
                            TransferCard(
                                proposal = proposal,
                                accounts = accounts,
                                modifier = Modifier.animateItem(),
                                onAccept = {
                                    vm.acceptTransfer(proposal)
                                    scope.launch { snackbar.showSnackbar(merged) }
                                },
                                onReject = {
                                    vm.rejectTransfer(proposal)
                                    scope.launch { snackbar.showSnackbar(keptSeparate) }
                                },
                            )
                        }
                    }
                    autoNote?.let { note ->
                        item(key = "auto-transfers") {
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = if (transfers.isEmpty()) 0.dp else 12.dp),
                            )
                        }
                    }
                    item(key = "intro") {
                        Text(
                            stringResource(
                                when {
                                    pending.isEmpty() && transfers.isEmpty() ->
                                        R.string.review_intro_clear
                                    pending.isEmpty() -> R.string.review_intro_only_transfers
                                    else -> R.string.review_intro
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = if (transfers.isEmpty()) 0.dp else 12.dp),
                        )
                    }
                    items(pending, key = { it.id }) { item ->
                        ReviewCard(
                            item = item,
                            modifier = Modifier.animateItem(),
                            rememberDefault = vm.prefs.smartRules,
                            onResolve = { learn -> resolving = Resolving(item, learn) },
                            onTeach = { kind ->
                                vm.teach(item, kind)
                                scope.launch { snackbar.showSnackbar(taught) }
                            },
                            onDismiss = { alsoSimilar ->
                                val similar = if (alsoSimilar) {
                                    val t = MsgTemplate.of(item.sender, item.body)
                                    pending.count { it.id != item.id && MsgTemplate.of(it.sender, it.body) == t }
                                } else 0
                                vm.dismissReview(item, alsoSimilar)
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        message = when {
                                            similar > 0 -> dismissedSimilar.format(similar)
                                            alsoSimilar -> dismissedFuture
                                            else -> dismissedOne
                                        },
                                        actionLabel = undo,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        vm.restoreReview(item)
                                    }
                                }
                            },
                        )
                    }
                    if (dismissed.isNotEmpty()) {
                        item(key = "dismissed-header") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(
                                        R.string.review_dismissed_header,
                                        dismissed.size,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                TextButton(onClick = { showDismissed = !showDismissed }, shapes = ButtonDefaults.shapes()) {
                                    Text(
                                        stringResource(
                                            if (showDismissed) R.string.action_hide
                                            else R.string.action_show
                                        )
                                    )
                                }
                            }
                        }
                        if (showDismissed) {
                            item(key = "dismissed-hint") {
                                Text(
                                    stringResource(R.string.review_dismissed_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(dismissed, key = { "d-" + it.id }) { item ->
                                DismissedCard(
                                    item = item,
                                    modifier = Modifier.animateItem(),
                                    onRestore = {
                                        vm.restoreReview(item)
                                        scope.launch { snackbar.showSnackbar(restored) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    resolving?.let { open ->
        val item = open.item
        ManualTxnDialog(
            title = stringResource(
                if (open.type != null) R.string.review_which_category
                else R.string.review_what_was_this
            ),
            atMillis = item.atMillis,
            defaultCurrency = vm.prefs.defaultCurrency,
            accounts = accounts,
            categoryUse = categoryUse,
            initialAmountMinor = item.amountMinor,
            initialCurrency = item.currency,
            initialType = open.type,
            onSave = { amountMinor, currency, type, merchant, categoryId, from, to ->
                vm.resolveReview(
                    item, amountMinor, currency, type, merchant, categoryId,
                    fromAccountId = from, toAccountId = to, learnSimilar = open.learnSimilar,
                )
                resolving = null
            },
            onDismiss = { resolving = null },
        )
    }
}

/**
 * One nominated transfer, shown as the two messages it came from so the user can see
 * exactly what would be merged. Deliberately a two-button decision with no default:
 * accepting wrongly quietly erases a real expense and a real income from every total,
 * so it is never something the app does on its own.
 */
@Composable
private fun TransferCard(
    proposal: TransferProposal,
    accounts: List<Account>,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unassigned = stringResource(R.string.review_unassigned_account)
    fun name(id: String?): String =
        accounts.firstOrNull { it.id == id }?.displayName ?: unassigned

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_sym_swap),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    MoneyText(proposal.amountMinor, proposal.currency,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        reviewDateFmt.format(
                            Instant.ofEpochMilli(proposal.atMillis).atZone(ZoneId.systemDefault())
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            // The two messages it would merge, each with the arrow and colour Home gives
            // money out and money in.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TransferLeg(
                    type = TxnType.EXPENSE,
                    text = stringResource(
                        R.string.review_left_account,
                        bidi("− " + Money.format(proposal.amountMinor, proposal.currency)),
                        name(proposal.fromAccountId),
                    ),
                )
                TransferLeg(
                    type = TxnType.INCOME,
                    text = stringResource(
                        R.string.review_arrived_account,
                        bidi("+ " + Money.format(proposal.amountMinor, proposal.currency)),
                        name(proposal.toAccountId),
                    ),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onReject, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.review_keep_both)) }
                Button(
                    onClick = onAccept,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.review_yes_transfer)) }
            }
        }
    }
}

/** One side of a proposed transfer: its arrow in Home's tone, and what happened. */
@Composable
private fun TransferLeg(type: TxnType, text: String) {
    val (bg, fg) = typeTone(type)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(typeIcon(type)), contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
        MoneyAwareText(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/**
 * One message the app could not read on its own: who sent it and when, why it stopped,
 * the bank's own words, and the answers. A message that only lacked its direction is
 * answered in one tap - Expense or Income, in Home's colours - and the answer is about
 * every message written like it. Anything else is added by hand or dismissed.
 */
/** What a message that only lacked its direction can be, in the order the card offers it. */
private val KINDS: List<MessageKind?> = listOf(MessageKind.OUT, MessageKind.IN, MessageKind.NONE)

private fun kindLabel(kind: MessageKind) = when (kind) {
    MessageKind.OUT -> typeLabel(TxnType.EXPENSE)
    MessageKind.IN -> typeLabel(TxnType.INCOME)
    else -> R.string.setup_kind_none
}

private fun kindIcon(kind: MessageKind) = when (kind) {
    MessageKind.OUT -> typeIcon(TxnType.EXPENSE)
    MessageKind.IN -> typeIcon(TxnType.INCOME)
    else -> R.drawable.ic_sym_block
}

/** Home's colours for money out and in; "not a transaction" in the neutral one. */
@Composable
private fun kindTone(kind: MessageKind) = when (kind) {
    MessageKind.OUT -> typeTone(TxnType.EXPENSE)
    MessageKind.IN -> typeTone(TxnType.INCOME)
    else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun ReviewCard(
    item: ReviewItem,
    rememberDefault: Boolean,
    onResolve: (learnSimilar: Boolean) -> Unit,
    onTeach: (MessageKind) -> Unit,
    onDismiss: (alsoSimilar: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_sym_mail_read),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.sender,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        reviewDateFmt.format(Instant.ofEpochMilli(item.atMillis).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.directionOnly) {
                    MoneyText(
                        item.amountMinor!!, item.currency ?: "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            SummaryPill(
                reasonText(item.reason),
                if (item.directionOnly) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                if (item.directionOnly) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
            )
            // The bank's words, quoted as the backlog quotes them: three lines, and the
            // rest slides open on a tap.
            var expanded by remember { mutableStateOf(false) }
            Surface(
                onClick = { expanded = !expanded },
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(motion.defaultSpatialSpec())
                        .padding(12.dp),
                )
            }
            var rememberChoice by remember { mutableStateOf(rememberDefault) }
            if (!item.directionOnly) {
                val plain = ListItemDefaults.colors(containerColor = Color.Transparent)
                ListItem(
                    checked = rememberChoice,
                    onCheckedChange = { rememberChoice = it },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = Color.Transparent,
                        selectedContentColor = plain.contentColor,
                        selectedSupportingContentColor = plain.supportingContentColor,
                        selectedTrailingContentColor = plain.trailingContentColor,
                    ),
                    contentPadding = PaddingValues(0.dp),
                    trailingContent = { Switch(checked = rememberChoice, onCheckedChange = null) },
                ) { Text(stringResource(R.string.review_remember_similar), style = MaterialTheme.typography.bodyMedium) }
            }
            // One question, and the answer is about every message written like this
            // one: it teaches the kind, and the inbox is read again with it, so the
            // others like it are answered in the same moment. Picked first, saved second,
            // so a stray tap never sends the card away.
            var kind by remember { mutableStateOf<MessageKind?>(null) }
            if (item.directionOnly) {
                ConnectedChoice(
                    options = KINDS,
                    selected = kind,
                    onSelect = { kind = it },
                    label = { stringResource(kindLabel(it!!)) },
                    icon = { kindIcon(it!!) },
                    tone = { kindTone(it!!) },
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onDismiss(rememberChoice) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_dismiss))
                }
                if (item.directionOnly) {
                    Button(
                        onClick = { kind?.let(onTeach) },
                        enabled = kind != null,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.action_save)) }
                }
                if (!item.directionOnly) {
                    Button(
                        onClick = { onResolve(rememberChoice) },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_sym_add),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.review_add_manually))
                    }
                }
            }
        }
    }
}

/** A dismissed message: a plain row, one tap from coming back. */
@Composable
private fun DismissedCard(
    item: ReviewItem,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        supportingContent = {
            Text(
                reviewDateFmt.format(Instant.ofEpochMilli(item.atMillis).atZone(ZoneId.systemDefault())) +
                    "\n" + item.body,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            TextButton(onClick = onRestore, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.action_restore))
            }
        },
    ) { Text(item.sender, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
