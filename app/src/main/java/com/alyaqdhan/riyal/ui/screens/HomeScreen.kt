@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.appVersion
import com.alyaqdhan.riyal.ui.compose.bidi
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.BudgetSection
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.Face
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.SectionTitle
import com.alyaqdhan.riyal.ui.compose.ToolbarSpacer
import com.alyaqdhan.riyal.ui.compose.TxnEditSheet
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import com.alyaqdhan.riyal.ui.theme.successColor


@Composable
fun HomeScreen(
    vm: MainViewModel,
    onRequestPermission: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenNeedsCategory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val txns by vm.txns.collectAsState()
    val updateState by vm.updateState.collectAsState()
    val appVersion = appVersion(LocalContext.current)
    val hasPerm by vm.hasSmsPermission.collectAsState()
    val reviews by vm.reviews.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categoryUse by vm.categoryUse.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val budgetsOn by vm.budgetsOn.collectAsState()
    val needsCategory by vm.needsCategoryCount.collectAsState()
    val needsAccountCheck by vm.accountsNeedConfirming.collectAsState()
    val pendingTransfers by vm.pendingTransfers.collectAsState()
    val askEachTime by vm.askEachTime.collectAsState()

    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    // The same period control as everywhere else, and the same kind of period. Home used
    // to have its own pair of chevrons over a month it never let you leave: no presets,
    // no calendar, and no way to ask about a week or a year the way Analysis can.
    val slice by vm.homeSlice.collectAsState()
    val totals = remember(txns, currency, slice) {
        Stats.totalsIn(txns, slice.start, slice.endExclusive, currency)
    }
    val pending = remember(reviews) { reviews.filter { it.state == ReviewItem.STATE_PENDING } }
    var picker by remember { mutableStateOf<Txn?>(null) }
    // The budget follows the selector above it: one period control per screen.
    val budgetSlice = slice

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_home)) }) }) { padding ->
        ScanRefresh(vm, Modifier.padding(padding)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── period selector: every stat below follows it
            PeriodBar(slice = slice, onChange = { vm.setHomeSlice(it) }, txns = txns)

            // ── the one hero: the face reacts to the month, Net is the number, and
            // spent/received sit under it as a single line rather than two more cards.
            Card(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val moodLabel = stringResource(
                        when (Stats.moodLabel(totals)) {
                            Stats.Mood.QUIET -> R.string.mood_quiet
                            Stats.Mood.NO_INCOME_YET -> R.string.mood_no_income_yet
                            Stats.Mood.WELL_UNDER -> R.string.mood_well_under
                            Stats.Mood.FINE -> R.string.mood_fine
                            Stats.Mood.CLOSE -> R.string.mood_close
                            Stats.Mood.OVER -> R.string.mood_over
                        }
                    )
                    Face(
                        mood = Stats.mood(totals),
                        modifier = Modifier
                            .size(88.dp)
                            // The sentence that used to say this is gone from the screen,
                            // so the face carries it for anyone reading by screen reader.
                            .semantics { contentDescription = moodLabel },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.home_net),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val net = totals.net
                        Text(
                            bidi(
                                (if (net < 0) "\u2212 " else "") +
                                    Money.format(kotlin.math.abs(net), currency)
                            ),
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
                                stringResource(R.string.home_out, Money.formatAmount(totals.spent, currency)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "\u00b7",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.home_in, Money.formatAmount(totals.received, currency)),
                                style = MaterialTheme.typography.bodySmall,
                                color = successColor(),
                            )
                        }
                        if (totals.otherCurrencyCount > 0) {
                            Text(
                                stringResource(
                                    R.string.home_other_currencies,
                                    counted(R.plurals.count_transactions, totals.otherCurrencyCount),
                                ),
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
                    title = stringResource(R.string.home_check_accounts_title),
                    // The title already says what to do, so the line under it only has to
                    // say what there is - three lines of prompt on a screen you are
                    // trying to read past is the prompt shouting.
                    subtitle = stringResource(
                        R.string.home_check_accounts_subtitle,
                        counted(R.plurals.count_accounts, accounts.size),
                    ),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenAccounts,
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
                )
            }

            // ── permission: the only reason scanning could be unavailable
            if (!hasPerm) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
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
                            Text(stringResource(R.string.home_sms_off_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            stringResource(R.string.home_sms_off_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier
                                .fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_sms_allow))
                        }
                    }
                }
            }

            // ── needs review: unreadable messages and transfer pairs both wait here
            if (pending.isNotEmpty() || pendingTransfers.isNotEmpty()) {
                val parts = buildList {
                    if (pendingTransfers.isNotEmpty()) add(counted(R.plurals.home_possible_transfers, pendingTransfers.size))
                    if (pending.isNotEmpty()) add(counted(R.plurals.home_unreadable_messages, pending.size))
                }
                ActionCard(
                    face = FaceStyle.CONFUSED,
                    mood = -0.2f,
                    title = stringResource(R.string.review_title),
                    subtitle = stringResource(R.string.home_needs_review_subtitle, parts.joinToString(" · ")),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenReview,
                )
            }

            // Sits under Needs review because it is the same kind of thing: something
            // waiting on the user. Nothing said "these have no category" before, so a
            // backlog could grow for months without ever being mentioned.
            if (needsCategory > 0) {
                ActionCard(
                    face = FaceStyle.CONFUSED,
                    mood = 0f,
                    title = counted(R.plurals.home_needs_category_title, needsCategory),
                    // How they are ordered is something the page itself shows on arrival.
                    subtitle = stringResource(R.string.home_needs_category_subtitle),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onOpenNeedsCategory,
                )
            }

            // ── a newer release exists. Last of the cards on purpose: the ones above are
            // things waiting on the user, and this is news. The check itself has run on
            // launch since it was written, but its answer only ever appeared in Settings,
            // so the way to learn a release was out was to go and look for one.
            (updateState as? MainViewModel.UpdateState.Available)?.let { available ->
                ActionCard(
                    face = FaceStyle.NORMAL,
                    mood = 0.8f,
                    title = stringResource(R.string.home_update_title, available.release.tag),
                    // Settings is where Download lives and where the notes are; saying
                    // so means the tap is not a surprise.
                    subtitle = stringResource(R.string.home_update_subtitle, appVersion),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenSettings,
                )
            }

            // ── recent transactions
            SectionTitle(stringResource(R.string.home_recent_transactions))
            val recent = txns.take(6)
            if (recent.isEmpty()) {
                EmptyState(
                    style = FaceStyle.SLEEPY,
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(
                        if (hasPerm) R.string.home_empty_with_permission
                        else R.string.home_empty_without_permission
                    ),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEachIndexed { index, txn ->
                        TxnRow(
                            txn,
                            onClick = { picker = txn },
                            accounts = accounts,
                        )
                    }
                }
            }
            ToolbarSpacer()
        }
        }
    }

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
            categoryUse = categoryUse,
            askEachTime = askEachTime,
            onAskEachTime = { vm.setAskEachTime(txn.merchant.orEmpty(), it) },
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
            .fillMaxWidth(),
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
