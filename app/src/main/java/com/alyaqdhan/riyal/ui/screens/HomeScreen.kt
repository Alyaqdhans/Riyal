@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.font.FontWeight
import com.alyaqdhan.riyal.ui.compose.ScrollThumb
import com.alyaqdhan.riyal.ui.compose.title
import androidx.compose.foundation.layout.size
import com.alyaqdhan.riyal.ui.theme.onSuccessContainer
import com.alyaqdhan.riyal.ui.theme.successContainer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.Stats
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.BudgetCard
import com.alyaqdhan.riyal.ui.compose.CategoryBadge
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.FlatBar
import com.alyaqdhan.riyal.ui.compose.MoneySign
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.compose.PeriodBar
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import com.alyaqdhan.riyal.ui.compose.TxnEditSheet
import com.alyaqdhan.riyal.ui.compose.TxnRow
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.dayLabel
import com.alyaqdhan.riyal.ui.compose.iconFor
import com.alyaqdhan.riyal.ui.compose.localDateOf
import com.alyaqdhan.riyal.ui.compose.toolbarSpace
import com.alyaqdhan.riyal.ui.theme.successColor
import com.alyaqdhan.riyal.ui.compose.categoryColor

/**
 * The dashboard, laid out the way people already read one in every finance app they
 * use: the period, then what came in, went out and is left over, then anything waiting
 * on them, where the money sits, where it went, and the latest records.
 *
 * Every section is one card with one job, and the things that need the user are one
 * list rather than a stack of differently coloured banners competing for attention.
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onRequestPermission: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenNeedsCategory: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenAnalysis: () -> Unit,
) {
    val txns by vm.txns.collectAsState()
    val hasPerm by vm.hasSmsPermission.collectAsState()
    val reviews by vm.reviews.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categoryUse by vm.categoryUse.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val budgetsOn by vm.budgetsOn.collectAsState()
    val needsCategory by vm.needsCategoryCount.collectAsState()
    val needsAccountCheck by vm.accountsNeedConfirming.collectAsState()
    val setupDone by vm.setupDone.collectAsState()
    val pendingTransfers by vm.pendingTransfers.collectAsState()
    val askEachTime by vm.askEachTime.collectAsState()
    val archivedIds by vm.archivedIds.collectAsState()
    val slice by vm.homeSlice.collectAsState()

    val currency = remember(txns) { Stats.primaryCurrency(txns, vm.prefs.defaultCurrency) }
    val totals = remember(txns, currency, slice) {
        Stats.totalsIn(txns, slice.start, slice.endExclusive, currency)
    }
    val topSpending = remember(txns, currency, slice) {
        Stats.breakdownIn(txns, slice.start, slice.endExclusive, currency).take(4)
    }
    val pending = remember(reviews) { reviews.filter { it.state == ReviewItem.STATE_PENDING } }
    // The newest records of the period chosen above, so the dates switch the list too.
    val recent = remember(txns, archivedIds, slice) {
        txns.asSequence().filter { it.id !in archivedIds && slice.contains(it.atMillis) }.take(8).toList()
            .groupBy { localDateOf(it.atMillis) }
    }
    var picker by remember { mutableStateOf<Txn?>(null) }

    val attention = buildList {
        if (hasPerm && !setupDone) {
            add(
                Attention(
                    R.drawable.ic_sym_school,
                    stringResource(R.string.home_setup_title),
                    stringResource(R.string.home_setup_subtitle),
                    onOpenSetup,
                )
            )
        }
        if (pending.isNotEmpty() || pendingTransfers.isNotEmpty()) {
            val parts = buildList {
                if (pendingTransfers.isNotEmpty()) add(counted(R.plurals.home_possible_transfers, pendingTransfers.size))
                if (pending.isNotEmpty()) add(counted(R.plurals.home_unreadable_messages, pending.size))
            }
            add(
                Attention(
                    R.drawable.ic_sym_inbox,
                    stringResource(R.string.review_title),
                    parts.joinToString(" · "),
                    onOpenReview,
                )
        // Like the row above: the page it opens, and how much is waiting there.
            )
        }
        if (needsCategory > 0) {
            add(
                Attention(
                    R.drawable.ic_sym_category,
                    stringResource(R.string.needs_title),
                    counted(R.plurals.count_records, needsCategory),
                    onOpenNeedsCategory,
                )
            )
        }
        // The setup checks the accounts too, so this is only for someone who skipped it.
        if (needsAccountCheck && setupDone) {
            add(
                Attention(
                    R.drawable.ic_sym_account_balance,
                    stringResource(R.string.home_check_accounts_title),
                    stringResource(R.string.home_check_accounts_subtitle, counted(R.plurals.count_accounts, accounts.size)),
                    onOpenAccounts,
                )
            )
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_home)) }) },
    ) { padding ->
        ScanRefresh(vm, Modifier.padding(padding)) {
            val list = rememberLazyListState()
            ScrollThumb(list, bottom = toolbarSpace) {
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(bottom = toolbarSpace),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "period") {
                    PeriodBar(
                        slice = slice,
                        onChange = { vm.setHomeSlice(it) },
                        txns = txns,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                item(key = "summary") { SummaryCard(totals, currency) }
                if (budgetsOn) {
                    item(key = "budget") {
                        BudgetCard(
                            slice = slice,
                            plans = budgets,
                            txns = txns,
                            currency = currency,
                            onSave = { plan, from -> vm.saveBudget(plan, from) },
                            onDelete = { vm.deleteBudget(it) },
                            categoryUse = categoryUse,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                if (!hasPerm) {
                    item(key = "permission") { PermissionCard(onRequestPermission) }
                }
                if (attention.isNotEmpty()) {
                    item(key = "attention") { AttentionCard(attention) }
                }
                if (topSpending.isNotEmpty()) {
                    item(key = "top") { TopSpendingCard(topSpending, currency, onOpenAnalysis) }
                }
                item(key = "recent-header") {
                    SectionHeader(
                        stringResource(R.string.home_recent_transactions),
                        action = stringResource(R.string.home_see_all).takeIf { recent.isNotEmpty() },
                        onAction = onOpenTransactions,
                    )
                }
                if (recent.isEmpty()) {
                    item(key = "empty") {
                        if (txns.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.home_empty_title),
                                subtitle = stringResource(
                                    if (hasPerm) R.string.home_empty_with_permission
                                    else R.string.home_empty_without_permission
                                ),
                                icon = R.drawable.ic_nav_records,
                            )
                        } else {
                            // Records exist, just none in these dates.
                            EmptyState(
                                title = stringResource(R.string.home_period_empty, slice.title()),
                                subtitle = stringResource(R.string.home_period_empty_hint),
                                icon = R.drawable.ic_sym_calendar_month,
                            )
                        }
                    }
                } else {
                    recent.forEach { (date, dayTxns) ->
                        item(key = "day-$date", contentType = "day") {
                            Text(
                                dayLabel(date),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        items(dayTxns, key = { it.id }, contentType = { "record" }) { txn ->
                            TxnRow(txn, onClick = { picker = txn }, accounts = accounts, showDate = false)
                        }
                    }
                }
            }
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

/** One thing waiting on the user. */
private data class Attention(
    @param:DrawableRes val icon: Int,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/** A section's title, with its one way further in on the other side. */
@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(action) }
    }
}

/**
 * What the period came to: what is left, large, with what went out and what came in as
 * two pills under it. Net is the one figure people open a money app to read, so it is
 * the one shown big; the two that make it up stay a glance away.
 */
@Composable
private fun SummaryCard(totals: Stats.MonthTotals, currency: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.home_net),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(
                totals.net,
                currency,
                sign = MoneySign.NEGATIVE,
                roll = true,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = if (totals.net < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 20.sp,
                    maxFontSize = MaterialTheme.typography.displaySmall.fontSize,
                ),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalPill(
                    R.drawable.ic_sym_arrow_up,
                    stringResource(R.string.label_expense),
                    totals.spent,
                    currency,
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    Modifier.weight(1f),
                )
                TotalPill(
                    R.drawable.ic_sym_arrow_down,
                    stringResource(R.string.label_income),
                    totals.received,
                    currency,
                    successContainer(),
                    onSuccessContainer(),
                    Modifier.weight(1f),
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
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** One side of the net: an arrow, its name and its total, in a pill of its own colour. */
@Composable
private fun TotalPill(
    @DrawableRes icon: Int,
    label: String,
    minor: Long,
    currency: String,
    container: Color,
    content: Color,
    modifier: Modifier,
) {
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            MoneyText(
                minor,
                currency,
                sign = MoneySign.NONE,
                roll = true,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 9.sp,
                    maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                ),
            )
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_sym_lock), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.home_sms_off_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.home_sms_off_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.home_sms_allow))
            }
        }
    }
}

/** Everything waiting on the user, as one list with one row each. */
@Composable
private fun AttentionCard(items: List<Attention>) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.home_attention),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        )
        items.forEach { a ->
            ListItem(
                onClick = a.onClick,
                leadingContent = { Icon(painterResource(a.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                supportingContent = { OneLine(a.subtitle) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) { OneLine(a.title) }
        }
    }
}

/** Where the money went: the biggest categories of the period, each with its share. */
@Composable
/**
 * An attention row's line, kept to one: a little smaller rather than broken, when a large
 * count or two reasons at once make it long.
 */
@Composable
private fun OneLine(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = LocalTextStyle.current.fontSize),
    )
}

private fun TopSpendingCard(slices: List<Stats.Slice>, currency: String, onOpenAnalysis: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.home_top_spending), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenAnalysis, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.home_see_all)) }
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            slices.forEach { s ->
                val category = Categories.byId(s.categoryId)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category.id, size = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            MoneyText(s.amountMinor, currency, sign = MoneySign.NONE, style = MaterialTheme.typography.bodyMedium)
                        }
                        FlatBar(
                            progress = { s.fraction },
                            color = categoryColor(category.id),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
