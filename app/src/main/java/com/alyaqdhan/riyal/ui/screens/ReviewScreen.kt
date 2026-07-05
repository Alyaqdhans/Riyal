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
import androidx.compose.material3.Card
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
 * user decides what each one was, or dismisses it.
 */
@Composable
fun ReviewScreen(vm: MainViewModel, onBack: () -> Unit) {
    val reviews by vm.reviews.collectAsState()
    val pending = remember(reviews) { reviews.filter { it.state == ReviewItem.STATE_PENDING } }
    var resolving by remember { mutableStateOf<ReviewItem?>(null) }
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
            if (pending.isEmpty()) {
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
                            "These matched your keywords but couldn't be read automatically. Nothing was recorded for them, you decide.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(pending, key = { it.id }) { item ->
                        ReviewCard(
                            item = item,
                            onResolve = { resolving = item },
                            onDismiss = {
                                vm.dismissReview(item)
                                scope.launch {
                                    val result = snackbar.showSnackbar(
                                        message = "Message dismissed",
                                        actionLabel = "Undo",
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        vm.undoDismissReview(item)
                                    }
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    resolving?.let { item ->
        ManualTxnDialog(
            title = "What was this?",
            atMillis = item.atMillis,
            defaultCurrency = vm.prefs.defaultCurrency,
            onSave = { amountMinor, currency, direction, merchant, categoryId ->
                vm.resolveReview(item, amountMinor, currency, direction, merchant, categoryId)
                resolving = null
            },
            onDismiss = { resolving = null },
        )
    }
}

@Composable
private fun ReviewCard(
    item: ReviewItem,
    onResolve: () -> Unit,
    onDismiss: () -> Unit,
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                FilledTonalButton(onClick = onResolve, modifier = Modifier.pressBounce()) {
                    Text("Add manually")
                }
            }
        }
    }
}
