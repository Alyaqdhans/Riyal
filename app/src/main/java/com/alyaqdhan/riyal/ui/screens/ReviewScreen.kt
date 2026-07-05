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
import com.alyaqdhan.riyal.data.MsgTemplate
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.Face
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.ManualTxnDialog
import com.alyaqdhan.riyal.ui.compose.SummaryPill
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
            if (pending.isEmpty() && dismissed.isEmpty()) {
                EmptyState(
                    style = FaceStyle.NORMAL,
                    mood = 0.9f,
                    title = "All clear",
                    subtitle = "When a message matches your keywords but can't be read, it waits here for your decision, it is never guessed into your numbers.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            if (pending.isEmpty()) {
                                "All clear, nothing is waiting for you."
                            } else {
                                "These matched your keywords but couldn't be read automatically. Nothing was recorded for them, you decide."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            items(dismissed, key = { "d-${it.id}" }) { item ->
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
            onSave = { amountMinor, currency, direction, merchant, categoryId ->
                vm.resolveReview(item, amountMinor, currency, direction, merchant, categoryId, learnSimilar = learn)
                resolving = null
            },
            onDismiss = { resolving = null },
        )
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
