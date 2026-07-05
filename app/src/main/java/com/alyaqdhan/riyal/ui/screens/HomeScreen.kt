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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CategoryPickerSheet
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.Face
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.ScanSheetHost
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.pressBounce
import com.alyaqdhan.riyal.ui.theme.onSuccessContainer
import com.alyaqdhan.riyal.ui.theme.successColor
import com.alyaqdhan.riyal.ui.theme.successContainer
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val monthFmt = DateTimeFormatter.ofPattern("MMMM uuuu")

@Composable
fun HomeScreen(vm: MainViewModel, onRequestPermission: () -> Unit, onOpenReview: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val hasPerm by vm.hasSmsPermission.collectAsState()
    val scan by vm.scanState.collectAsState()
    val reviews by vm.reviews.collectAsState()

    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    // The dashboard is per-month: chevrons walk back through any month the inbox covers.
    var monthOffset by remember { mutableIntStateOf(0) }
    val month = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }
    val totals = remember(txns, currency, month) { Stats.totalsFor(txns, month, currency) }
    val pending = remember(reviews) { reviews.filter { it.state == ReviewItem.STATE_PENDING } }
    var picker by remember { mutableStateOf<Txn?>(null) }

    val scope = rememberCoroutineScope()
    val faceRotation = remember { Animatable(0f) }

    Scaffold(topBar = { TopAppBar(title = { Text("Riyal") }) }) { padding ->
        // Pull to refresh = scan (scanning also runs on launch; there is no button).
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
            // ── month selector: every stat below follows it
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthOffset-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    month.format(monthFmt),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { monthOffset++ }, enabled = monthOffset < 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }

            // ── mood card: the face reacts to the selected month's spending health
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
                    Face(
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
                            "Net",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val net = totals.received - totals.spent
                        Text(
                            (if (net < 0) "− " else "") + Money.format(kotlin.math.abs(net), currency),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (net < 0) MaterialTheme.colorScheme.error else successColor(),
                        )
                        Text(
                            Stats.moodLabel(totals),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── in / out stat cards: danger red out, success green in
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
                    container = successContainer(),
                    content = onSuccessContainer(),
                    modifier = Modifier
                        .weight(1f)
                        .popIn(120),
                )
            }
            if (totals.otherCurrencyCount > 0) {
                Text(
                    "+${totals.otherCurrencyCount} transaction(s) this month in other currencies, see Activity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── budget: wavy bar against the monthly budget from Settings
            val budget = vm.prefs.monthlyBudgetMinor
            if (budget > 0) {
                val used = totals.spent.toFloat() / budget.toFloat()
                val budgetColor = when {
                    used >= 1f -> MaterialTheme.colorScheme.error
                    used > 0.8f -> MaterialTheme.colorScheme.primary
                    else -> successColor()
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .popIn(140),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Budget", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${(used * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = budgetColor,
                            )
                        }
                        LinearWavyProgressIndicator(
                            progress = { used.coerceIn(0f, 1f) },
                            color = budgetColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            amplitude = { 1f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val left = budget - totals.spent
                        Text(
                            if (left >= 0) {
                                "${Money.format(left, currency)} left of ${Money.format(budget, currency)}"
                            } else {
                                "${Money.format(-left, currency)} over your ${Money.format(budget, currency)} budget"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── permission: the only reason scanning could be unavailable
            if (!hasPerm) {
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
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text("SMS access is off", style = MaterialTheme.typography.titleMedium)
                        }
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

            // ── needs review: one tappable row leading to the inner review page
            if (pending.isNotEmpty()) {
                Surface(
                    onClick = onOpenReview,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .popIn(200)
                        .pressBounce(0.97f),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Face(mood = -0.2f, style = FaceStyle.CONFUSED, modifier = Modifier.size(44.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Needs review",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "${pending.size} message(s) couldn't be read, tap to decide",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
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
                    subtitle = if (hasPerm) "Pull down to scan, Riyal will narrate everything it does."
                    else "Allow SMS reading, then scan whenever you choose.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEachIndexed { index, txn ->
                        TxnRow(txn, onClick = { picker = txn }, modifier = Modifier.popIn(index * 40))
                    }
                }
            }
            // Room for the floating toolbar hovering over the content.
            Spacer(Modifier.height(88.dp))
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
            rememberByDefault = vm.prefs.smartRules,
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

