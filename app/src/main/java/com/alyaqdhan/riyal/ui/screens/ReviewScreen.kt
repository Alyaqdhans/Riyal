@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MoneyAwareText(
                    stringResource(
                        R.string.review_left_account,
                        bidi("− " + Money.format(proposal.amountMinor, proposal.currency)),
                        name(proposal.fromAccountId),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                MoneyAwareText(
                    stringResource(
                        R.string.review_arrived_account,
                        bidi("+ " + Money.format(proposal.amountMinor, proposal.currency)),
                        name(proposal.toAccountId),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = successColor(),
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

@Composable
private fun ReviewCard(
    item: ReviewItem,
    rememberDefault: Boolean,
    onResolve: (learnSimilar: Boolean) -> Unit,
    onTeach: (MessageKind) -> Unit,
    onDismiss: (alsoSimilar: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_sym_mail_read),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    if (item.directionOnly) {
                        MoneyText(item.amountMinor!!, item.currency ?: "",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(
                                R.string.row_account_time,
                                item.sender,
                                reviewDateFmt.format(
                                    Instant.ofEpochMilli(item.atMillis)
                                        .atZone(ZoneId.systemDefault())
                                ),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(item.sender, style = MaterialTheme.typography.titleSmall)
                        Text(
                            reviewDateFmt.format(Instant.ofEpochMilli(item.atMillis).atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SummaryPill(
                reasonText(item.reason),
                if (item.directionOnly) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                if (item.directionOnly) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
            )
            var expanded by remember { mutableStateOf(false) }
            Text(
                item.body,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded },
            )
            var rememberChoice by remember { mutableStateOf(rememberDefault) }
            if (!item.directionOnly) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { rememberChoice = !rememberChoice },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Text(
                        stringResource(R.string.review_remember_similar),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // One question, and the answer is about every message written like this
            // one: it teaches the kind, and the inbox is read again with it, so the
            // others like it are answered in the same moment.
            if (item.directionOnly) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onTeach(MessageKind.OUT) },
                        modifier = Modifier.weight(1f),
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.label_expense)) }
                    FilledTonalButton(
                        onClick = { onTeach(MessageKind.IN) },
                        modifier = Modifier.weight(1f),
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.label_income)) }
                }
                TextButton(onClick = { onTeach(MessageKind.NONE) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.setup_kind_none))
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = { onDismiss(rememberChoice) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_dismiss))
                }
                if (!item.directionOnly) {
                    FilledTonalButton(
                        onClick = { onResolve(rememberChoice) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.review_add_manually))
                    }
                }
            }
        }
    }
}

@Composable
private fun DismissedCard(
    item: ReviewItem,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.sender, style = MaterialTheme.typography.titleSmall)
                Text(
                    reviewDateFmt.format(Instant.ofEpochMilli(item.atMillis).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRestore, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_restore)) }
        }
    }
}
