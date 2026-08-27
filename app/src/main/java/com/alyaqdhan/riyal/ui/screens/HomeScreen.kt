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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.BudgetSection
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.Face
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.ScanSheetHost
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.compose.ToolbarSpacer
import com.alyaqdhan.riyal.ui.compose.TxnEditSheet
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.pressBounce
import com.alyaqdhan.riyal.ui.theme.successColor
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val monthFmt = DateTimeFormatter.ofPattern("MMMM uuuu")

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onRequestPermission: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenAccounts: () -> Unit,
) {
    val txns by vm.txns.collectAsState()
    val hasPerm by vm.hasSmsPermission.collectAsState()
    val scan by vm.scanState.collectAsState()
    val reviews by vm.reviews.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val budgetsOn by vm.budgetsOn.collectAsState()
    val needsAccountCheck by vm.accountsNeedConfirming.collectAsState()
    val pendingTransfers by vm.pendingTransfers.collectAsState()

    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    // The dashboard is per-month: chevrons walk back through any month the inbox covers.
    var monthOffset by remember { mutableIntStateOf(0) }
    val month = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }
    val totals = remember(txns, currency, month) { Stats.totalsFor(txns, month, currency) }
    val pending = remember(reviews) { reviews.filter { it.state == ReviewItem.STATE_PENDING } }
    var picker by remember { mutableStateOf<Txn?>(null) }
    // The budget follows the month selector above it: one period control per screen.
    val budgetSlice = remember(month) { TimeSlice.ofMonth(month) }

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

            // ── the one hero: the face reacts to the month, Net is the number, and
            // spent/received sit under it as a single line rather than two more cards.
            Card(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .popIn(),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val moodLabel = Stats.moodLabel(totals)
                    Face(
                        mood = Stats.mood(totals),
                        modifier = Modifier
                            .size(88.dp)
                            .graphicsLayer { rotationZ = faceRotation.value }
                            // The sentence that used to say this is gone from the screen,
                            // so the face carries it for anyone reading by screen reader.
                            .semantics { contentDescription = moodLabel }
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Net",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val net = totals.net
                        Text(
                            (if (net < 0) "\u2212 " else "") + Money.format(kotlin.math.abs(net), currency),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (net < 0) MaterialTheme.colorScheme.error else successColor(),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 18.sp,
                                maxFontSize = MaterialTheme.typography.headlineMedium.fontSize,
                            ),
                        )
                        // Both figures, one line, no labels repeated: colour says which
                        // is which, and the currency was named by the number above.
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                Money.formatAmount(totals.spent, currency) + " out",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "\u00b7",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                Money.formatAmount(totals.received, currency) + " in",
                                style = MaterialTheme.typography.bodySmall,
                                color = successColor(),
                            )
                        }
                        if (totals.otherCurrencyCount > 0) {
                            Text(
                                "+${totals.otherCurrencyCount} in other currencies",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── accounts: balances read from SMS are a first guess until the user says
            // otherwise, so this asks once and then gets out of the way for good.
            if (needsAccountCheck) {
                ActionCard(
                    face = FaceStyle.CONFUSED,
                    mood = 0.3f,
                    title = "Check your accounts",
                    subtitle = "${accounts.size} account(s) were set up from your messages. " +
                        "Confirm the balances are right.",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenAccounts,
                    modifier = Modifier.popIn(140),
                )
            }

            // ── budget: only present once switched on in Settings
            if (budgetsOn) {
                BudgetSection(
                    slice = budgetSlice,
                    plans = budgets,
                    txns = txns,
                    currency = currency,
                    onCreate = { label, start, end -> vm.addBudget(label, start, end) },
                    onCopy = { source, label, start, end -> vm.copyBudget(source, label, start, end) },
                    onSetLine = { planId, categoryId, minor -> vm.setBudgetLine(planId, categoryId, minor) },
                    onSetPeriod = { planId, label, start, end -> vm.setBudgetPeriod(planId, label, start, end) },
                    onDelete = { vm.deleteBudget(it) },
                    modifier = Modifier.popIn(160),
                )
            }

            // ── permission: the only reason scanning could be unavailable
            if (!hasPerm) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .popIn(180),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            "Reading happens only when you ask, and never leaves this phone.",
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

            // ── needs review: unreadable messages and transfer pairs both wait here
            if (pending.isNotEmpty() || pendingTransfers.isNotEmpty()) {
                val parts = buildList {
                    if (pendingTransfers.isNotEmpty()) add("${pendingTransfers.size} possible transfer(s)")
                    if (pending.isNotEmpty()) add("${pending.size} unreadable message(s)")
                }
                ActionCard(
                    face = FaceStyle.CONFUSED,
                    mood = -0.2f,
                    title = "Needs review",
                    subtitle = parts.joinToString(" · ") + ", tap to decide",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenReview,
                    modifier = Modifier.popIn(200),
                )
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
                        TxnRow(
                            txn,
                            onClick = { picker = txn },
                            modifier = Modifier.popIn(index * 40),
                            accounts = accounts,
                        )
                    }
                }
            }
            ToolbarSpacer()
        }
        }
    }

    ScanSheetHost(vm)
    picker?.let { txn ->
        TxnEditSheet(
            txn = txn,
            accounts = accounts,
            onApply = { categoryId, rulePattern ->
                vm.setCategory(txn, categoryId, rulePattern)
                picker = null
            },
            onDismiss = { picker = null },
            rememberByDefault = vm.prefs.smartRules,
            onSetAccount = {
                vm.setTxnAccount(txn, it)
                picker = null
            },
            onMarkTransfer = { from, to ->
                vm.markAsTransfer(txn, from, to)
                picker = null
            },
            onSplitTransfer = {
                vm.splitTransfer(txn)
                picker = null
            },
        )
    }
}

/** A tappable prompt card: mascot, one line of why, and a chevron into the page. */
@Composable
private fun ActionCard(
    face: FaceStyle,
    mood: Float,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .pressBounce(0.97f),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Face(mood = mood, style = face, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = content)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = content)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = content,
            )
        }
    }
}
