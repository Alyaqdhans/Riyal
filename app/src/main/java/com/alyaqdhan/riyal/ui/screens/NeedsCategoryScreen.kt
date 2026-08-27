@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryChips
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.popIn

/**
 * The backlog of records the scan could not place, as decisions rather than rows.
 *
 * Filing fifty records one at a time is fifty sheets opened, fifty categories picked,
 * fifty sheets dismissed - and next month the same shops come back and it starts
 * again. Here one counterparty is one row, one tap files every record under it, and
 * the answer is saved as a rule so the same shop is never asked about twice.
 *
 * Biggest money first, deliberately. A long backlog is mostly small payments that
 * barely move a total; the few largest merchants are where the distortion lives, so
 * the list puts them where they will be answered first and lets the tail wait.
 */
@Composable
fun NeedsCategoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val archived by vm.archivedIds.collectAsState()
    val custom by vm.categories.collectAsState()
    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }

    val groups = remember(txns, archived, currency, custom) {
        Stats.unfiledByMerchant(txns, currency, archived)
    }
    // Records that name nobody cannot be batched: there is nothing to key a rule on.
    // They are still waiting, so the screen says so rather than quietly losing them.
    val unnamed = remember(txns, archived, currency) {
        Stats.unfiled(txns, archived).count { it.merchant.isNullOrBlank() }
    }
    val records = remember(groups) { groups.sumOf { it.count } }

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
            if (groups.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        style = FaceStyle.NORMAL,
                        title = "Everything is filed",
                        subtitle = if (unnamed > 0) {
                            "$unnamed record(s) name no shop or person, so they can only be " +
                                "filed one at a time from Activity."
                        } else {
                            "Nothing is waiting for a category."
                        },
                    )
                }
                return@LazyColumn
            }

            item(key = "intro") {
                Text(
                    "$records record(s) from ${groups.size} place(s), biggest first. " +
                        "Filing one files all of its records, and remembers the answer for " +
                        "next month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            items(groups, key = { it.merchant.lowercase() + "|" + it.type }) { group ->
                MerchantCard(
                    group = group,
                    onFile = { vm.fileMerchant(group, it) },
                )
            }

            if (unnamed > 0) {
                item(key = "unnamed") {
                    Column {
                        SectionTitle("Can't be grouped")
                        Text(
                            "$unnamed record(s) name no shop or person - the bank didn't say " +
                                "who was paid. There is nothing to remember them by, so they " +
                                "stay one-at-a-time from Activity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One decision: who, how many, how much, and the categories to put them in. The chips
 * stay folded until asked for, so the list reads as a ranking of what is waiting rather
 * than a wall of every category repeated per row.
 */
@Composable
private fun MerchantCard(
    group: Stats.MerchantGroup,
    onFile: (String) -> Unit,
) {
    var open by rememberSaveable(group.merchant, group.type) { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().popIn()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.merchant, style = MaterialTheme.typography.titleSmall)
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
            }

            if (!open) {
                TextButton(onClick = { open = true }) { Text("Pick a category") }
            }
            AnimatedVisibility(
                visible = open,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryChips(
                        type = group.type,
                        // Nothing is preselected: the category it currently carries is
                        // the fallback, and showing it as a choice already made is how
                        // a backlog gets "confirmed" without being read.
                        selectedId = "",
                        onSelect = onFile,
                    )
                    Text(
                        "Files all ${group.count}, and anything from ${group.merchant} later.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
