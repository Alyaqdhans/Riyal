@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryPickerSheet
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.JaggyFace
import com.alyaqdhan.riyal.ui.compose.ScanSheetHost
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.pressBounce
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val monthFmt = DateTimeFormatter.ofPattern("MMMM uuuu")
private val lastScanFmt = DateTimeFormatter.ofPattern("dd MMM, h:mm a")

@Composable
fun HomeScreen(vm: MainViewModel, onRequestPermission: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val hasPerm by vm.hasSmsPermission.collectAsState()
    val scan by vm.scanState.collectAsState()
    val lastSummary by vm.lastSummary.collectAsState()

    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    val month = remember { YearMonth.now() }
    val totals = remember(txns, currency) { Stats.totalsFor(txns, month, currency) }
    var picker by remember { mutableStateOf<Txn?>(null) }

    val scope = rememberCoroutineScope()
    val faceRotation = remember { Animatable(0f) }

    Scaffold(topBar = { TopAppBar(title = { Text("Riyal") }) }) { padding ->
        // Pull to refresh = scan, quietly (no sheet) — the wavy indicator shows the work.
        val ptrState = rememberPullToRefreshState()
        val refreshing = scan is MainViewModel.ScanState.Running
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.startScan(showSheet = false) },
            state = ptrState,
            modifier = Modifier.padding(padding),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = ptrState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── mood card: the face reacts to this month's spending health
            Card(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .popIn(),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JaggyFace(
                        mood = Stats.mood(totals),
                        modifier = Modifier
                            .size(96.dp)
                            .graphicsLayer { rotationZ = faceRotation.value }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                scope.launch {
                                    faceRotation.snapTo(-12f)
                                    faceRotation.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioHighBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                    )
                                }
                            },
                    )
                    Column {
                        Text(
                            month.format(monthFmt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val net = totals.received - totals.spent
                        Text(
                            (if (net < 0) "− " else "") + Money.format(kotlin.math.abs(net), currency),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            Stats.moodLabel(totals),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── in / out stat cards
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Spent",
                    value = Money.format(totals.spent, currency),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .weight(1f)
                        .popIn(60),
                )
                StatCard(
                    label = "Received",
                    value = Money.format(totals.received, currency),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .popIn(120),
                )
            }
            if (totals.otherCurrencyCount > 0) {
                Text(
                    "+${totals.otherCurrencyCount} transaction(s) this month in other currencies — see Activity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── scan card: the only thing in the app that reads SMS, and only on tap
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .popIn(160),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (scan is MainViewModel.ScanState.Running) {
                            LoadingIndicator(Modifier.size(40.dp))
                        } else {
                            Icon(
                                if (hasPerm) Icons.Filled.Refresh else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column {
                            Text(
                                when {
                                    scan is MainViewModel.ScanState.Running -> "Scanning…"
                                    hasPerm -> "Read new messages"
                                    else -> "SMS access is off"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                when {
                                    scan is MainViewModel.ScanState.Running -> "working — tap below for the live log"
                                    lastSummary != null -> lastSummary!!.let {
                                        "last scan ${lastScanFmt.format(Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()))} · ${it.parsed} recorded · ${it.skipped} skipped"
                                    }
                                    hasPerm -> "no scan yet — nothing has been read"
                                    else -> "the app cannot read anything until you allow it"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hasPerm) {
                        Button(
                            onClick = { vm.startScan() },
                            // Expressive shape morph: round → squarish while pressed
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressBounce(),
                        ) {
                            Text(if (scan is MainViewModel.ScanState.Running) "Show live log" else "Scan messages")
                        }
                    } else {
                        Text(
                            "Reading is on-demand only, keyword-gated, and stays on this phone. You can revoke the permission at any time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressBounce(),
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Allow SMS reading")
                        }
                    }
                }
            }

            // ── recent transactions
            SectionTitle("Recent activity")
            val recent = txns.take(6)
            if (recent.isEmpty()) {
                EmptyState(
                    style = FaceStyle.SLEEPY,
                    title = "Nothing recorded yet",
                    subtitle = if (hasPerm) "Tap “Scan messages” and Riyal will narrate everything it does."
                    else "Allow SMS reading, then scan whenever you choose.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEachIndexed { index, txn ->
                        TxnRow(txn, onClick = { picker = txn }, modifier = Modifier.popIn(index * 40))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        }
    }

    ScanSheetHost(vm)
    picker?.let { txn ->
        CategoryPickerSheet(
            txn = txn,
            onApply = { categoryId, rulePattern ->
                vm.setCategory(txn, categoryId, rulePattern)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = container, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = content)
            Text(value, style = MaterialTheme.typography.titleLarge, color = content)
        }
    }
}
