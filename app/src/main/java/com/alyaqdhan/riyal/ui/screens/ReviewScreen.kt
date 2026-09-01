@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.MsgTemplate
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.TransferProposal
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.Face
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.ManualTxnDialog
import com.alyaqdhan.riyal.ui.compose.SummaryPill
import com.alyaqdhan.riyal.ui.theme.successColor
import com.alyaqdhan.riyal.ui.compose.pressBounce
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val reviewDateFmt = DateTimeFormatter.ofPattern("dd MMM uuuu, h:mm a")

/**
 * Inner page (opened from the Home "Needs review" section): messages that matched the
 * keywords but could not be read automatically. Nothing was recorded for them, the
 * user decides what each one was, or dismisses it. With "Remember" checked the choice
 * teaches the app: dismissing hides similar messages too (restorable below), recording
 * marks that kind of message as wanted.
 */
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
        "$autoConfirmed matching pair(s) were confirmed as transfers for you, so they " +
            "count as neither spending nor income. Open one in Activity to split it back " +
            "apart, or turn off \"Confirm transfers for me\" in Settings to be asked each time."
    } else {
        null
    }
    val pending = remember(reviews) { reviews.filter { it.state == ReviewItem.STATE_PENDING } }
    val dismissed = remember(reviews) { reviews.filter { it.state == ReviewItem.STATE_DISMISSED } }
    var resolving by remember { mutableStateOf<Pair<ReviewItem, Boolean>?>(null) }
    var showDismissed by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Needs review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (pending.isEmpty() && dismissed.isEmpty() && transfers.isEmpty()) {
                EmptyState(
                    style = FaceStyle.NORMAL,
                    mood = 0.9f,
                    title = "All clear",
                    subtitle = "When a message matches your keywords but can't be read, it waits here for your decision, it is never guessed into your numbers." +
                        (autoNote?.let { "\n\n$it" } ?: ""),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (transfers.isNotEmpty()) {
                        item(key = "transfer-header") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Transfers to confirm (${transfers.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "Two messages that look like one movement of your own money. " +
                                        "Confirm and it stops counting as both spending and income; " +
                                        "reject and both stay exactly as they are.",
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
                                    scope.launch { snackbar.showSnackbar("Merged into one transfer") }
                                },
                                onReject = {
                                    vm.rejectTransfer(proposal)
                                    scope.launch { snackbar.showSnackbar("Kept as separate records") }
                                },
                                modifier = Modifier.animateItem(),
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
                            when {
                                pending.isEmpty() && transfers.isEmpty() -> "All clear, nothing is waiting for you."
                                pending.isEmpty() -> "Nothing unreadable — just the transfers above."
                                else -> "These matched your keywords but couldn't be read automatically. Nothing was recorded for them, you decide."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = if (transfers.isEmpty()) 0.dp else 12.dp),
                        )
                    }
                    items(pending, key = { it.id }) { item ->
                        ReviewCard(
                            item = item,
                            rememberDefault = vm.prefs.smartRules,
                            onResolve = { learn -> resolving = item to learn },
                            onDismiss = { alsoSimilar ->
                                val similar = if (alsoSimilar) {
                                    val t = MsgTemplate.of(item.sender, item.body)
                                    pending.count { it.id != item.id && MsgTemplate.of(it.sender, it.body) == t }
                                } else 0
                                vm.dismissReview(item, alsoSimilar)
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        message = when {
                                            similar > 0 -> "Dismissed, along with $similar similar"
                                            alsoSimilar -> "Dismissed, future ones will be too"
                                            else -> "Message dismissed"
                                        },
                                        actionLabel = "Undo",
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        vm.restoreReview(item)
                                    }
                                }
                            },
                            modifier = Modifier.animateItem(),
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
                                Text("Dismissed (${dismissed.size})", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { showDismissed = !showDismissed }) {
                                    Text(if (showDismissed) "Hide" else "Show")
                                }
                            }
                        }
                        if (showDismissed) {
                            item(key = "dismissed-hint") {
                                Text(
                                    "Everything you dismissed stays here, nothing is deleted. Restoring one also brings back the similar ones hidden with it, and that kind of message will reach Review again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(dismissed, key = { "d-" + it.id }) { item ->
                                DismissedCard(
                                    item = item,
                                    onRestore = {
                                        vm.restoreReview(item)
                                        scope.launch { snackbar.showSnackbar("Restored to review") }
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    resolving?.let { (item, learn) ->
        ManualTxnDialog(
            title = "What was this?",
            atMillis = item.atMillis,
            defaultCurrency = vm.prefs.defaultCurrency,
            accounts = accounts,
            categoryUse = categoryUse,
            onSave = { amountMinor, currency, type, merchant, categoryId, from, to ->
                vm.resolveReview(
                    item, amountMinor, currency, type, merchant, categoryId,
                    fromAccountId = from, toAccountId = to, learnSimilar = learn,
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
    fun name(id: String?): String =
        accounts.firstOrNull { it.id == id }?.displayName ?: "an unassigned account"

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
                Text("⇄", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f)) {
                    Text(
                        Money.format(proposal.amountMinor, proposal.currency),
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
                Text(
                    "− ${Money.format(proposal.amountMinor, proposal.currency)} left ${name(proposal.fromAccountId)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "+ ${Money.format(proposal.amountMinor, proposal.currency)} arrived in ${name(proposal.toAccountId)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = successColor(),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onReject) { Text("No, keep both") }
                Button(
                    onClick = onAccept,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.pressBounce(),
                ) { Text("Yes, it's a transfer") }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    item: ReviewItem,
    rememberDefault: Boolean,
    onResolve: (learnSimilar: Boolean) -> Unit,
    onDismiss: (alsoSimilar: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Face(mood = -0.2f, style = FaceStyle.CONFUSED, modifier = Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.sender, style = MaterialTheme.typography.titleSmall)
                    Text(
                        reviewDateFmt.format(Instant.ofEpochMilli(item.atMillis).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SummaryPill(
                item.reason,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
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
                    "Remember for similar messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = { onDismiss(rememberChoice) }) { Text("Dismiss") }
                FilledTonalButton(
                    onClick = { onResolve(rememberChoice) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.pressBounce(),
                ) {
                    Text("Add manually")
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
            TextButton(onClick = onRestore) { Text("Restore") }
        }
    }
}
