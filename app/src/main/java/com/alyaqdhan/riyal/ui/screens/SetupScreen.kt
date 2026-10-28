@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.MessageKind
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.MainViewModel.SetupStep
import com.alyaqdhan.riyal.ui.compose.CategoryChips
import com.alyaqdhan.riyal.ui.compose.CategoryIcon
import com.alyaqdhan.riyal.ui.compose.FlatBar
import com.alyaqdhan.riyal.ui.compose.ScanBar
import com.alyaqdhan.riyal.ui.compose.FlatWheel
import com.alyaqdhan.riyal.ui.compose.MoneySign
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.iconFor
import com.alyaqdhan.riyal.ui.compose.labelFor
import com.alyaqdhan.riyal.ui.compose.rememberCategoryOrder
import com.alyaqdhan.riyal.ui.compose.unmasked
import com.alyaqdhan.riyal.ui.theme.successColor
import java.time.Instant
import java.time.ZoneId

/**
 * Teaching Riyal how the user's banks write, in three steps: read the inbox, check the
 * banks and accounts it found, then confirm or correct a few messages. Each answer is
 * about every message written the same way, and the questions stop as soon as the
 * answers say the reading can be trusted - so a user whose banks it reads well is done
 * in a handful of taps, and one whose banks it misreads is asked until it reads them
 * right.
 */
@Composable
fun SetupScreen(vm: MainViewModel, onFinished: () -> Unit) {
    val step by vm.setupStep.collectAsState()
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started || !vm.setupActive) {
            started = true
            vm.startSetup()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                actions = {
                    val index = when (step) {
                        SetupStep.READING, SetupStep.ACCOUNTS -> 1
                        SetupStep.QUESTIONS -> 2
                        SetupStep.DONE -> 3
                    }
                    Text(
                        stringResource(R.string.setup_step_of, index, 3),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (step) {
                SetupStep.READING -> ReadingStep(vm)
                SetupStep.ACCOUNTS -> AccountsStep(vm)
                SetupStep.QUESTIONS -> QuestionsStep(vm)
                SetupStep.DONE -> DoneStep(vm, onFinished)
            }
        }
    }
}

@Composable
private fun ReadingStep(vm: MainViewModel) {
    val scan by vm.scanState.collectAsState()
    val running = scan as? MainViewModel.ScanState.Running
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlatWheel(Modifier.size(56.dp))
        Text(stringResource(R.string.setup_reading_title), style = MaterialTheme.typography.titleLarge)
        Text(
            if (running != null && running.total > 0) {
                stringResource(
                    R.string.scan_progress,
                    running.processed,
                    running.total,
                    stringResource(running.noun),
                )
            } else {
                stringResource(R.string.scan_querying)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScanBar(
            progress = if (running != null && running.total > 0) ({ running.processed / running.total.toFloat() }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.setup_reading_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * "Are these your banks and accounts?" Each bank is a group, each account a row that
 * opens the full editor. A sender that is not a bank at all can be dropped, and one the
 * first read missed can be added.
 */
@Composable
private fun AccountsStep(vm: MainViewModel) {
    val accounts by vm.accounts.collectAsState()
    val balances by vm.balances.collectAsState()
    val senders by vm.senders.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var dropping by remember { mutableStateOf<String?>(null) }
    val live = remember(accounts) { accounts.filter { !it.archived } }
    val byBank = remember(live) {
        live.groupBy { it.senderIds.firstOrNull()?.trim().orEmpty().ifEmpty { it.bankName } }
    }
    val missing = remember(senders, accounts) {
        val held = accounts.flatMap { it.senderIds }.mapTo(HashSet()) { it.trim().lowercase() }
        senders.filter { s ->
            s.trim().lowercase() !in held && s.any(Char::isLetter)
        }.sorted().take(16)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "intro") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.setup_accounts_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (live.isEmpty()) {
                            stringResource(R.string.setup_accounts_none)
                        } else {
                            stringResource(
                                R.string.setup_accounts_found,
                                counted(R.plurals.count_accounts, live.size),
                                counted(R.plurals.count_banks, byBank.size),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            byBank.forEach { (sender, group) ->
                item(key = "bank-$sender") {
                    ListItem(
                        supportingContent = { Text(stringResource(R.string.setup_bank_sender, sender)) },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_sym_account_balance), contentDescription = null)
                        },
                        trailingContent = {
                            TextButton(onClick = { dropping = sender }) {
                                Text(stringResource(R.string.setup_not_my_bank))
                            }
                        },
                    ) {
                            Text(
                                Account.bankLabel(group.first().bankName).ifEmpty { sender },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                }
                items(group, key = { it.id }) { account ->
                    ListItem(
                        onClick = { editing = account },
                        supportingContent = {
                            Text(
                                if (account.needsBalance) stringResource(R.string.acct_needs_balance)
                                else stringResource(labelFor(account.kind))
                            )
                        },
                        leadingContent = {
                            Icon(
                                painterResource(iconFor(account.kind)),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        },
                        trailingContent = {
                            val balance = balances[account.id] ?: account.openingBalanceMinor
                            MoneyText(
                                balance,
                                account.currency,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                    ) { Text(account.shortName) }
                }
                item(key = "div-$sender") { HorizontalDivider(Modifier.padding(horizontal = 16.dp)) }
            }
            if (missing.isNotEmpty()) {
                item(key = "missing") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.setup_missing_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.setup_missing_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            missing.forEach { sender ->
                                AssistChip(
                                    onClick = {
                                        vm.includeSender(sender)
                                        vm.rereadForSetup()
                                    },
                                    label = { Text(sender) },
                                )
                            }
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = { vm.confirmSetupAccounts() }) {
                    Text(stringResource(if (live.isEmpty()) R.string.setup_continue else R.string.setup_accounts_right))
                }
            }
        }
    }

    editing?.let { account ->
        AccountEditorDialog(
            account = account,
            isNew = false,
            onSave = {
                vm.saveAccount(it)
                editing = null
            },
            onDelete = {
                vm.deleteAccount(account.id)
                editing = null
            },
            onDismiss = { editing = null },
            mergeTargets = mergeTargetsFor(account, accounts),
            onMerge = { target ->
                vm.mergeAccount(account, target)
                editing = null
            },
        )
    }

    dropping?.let { sender ->
        AlertDialog(
            onDismissRequest = { dropping = null },
            title = { Text(stringResource(R.string.setup_not_my_bank_title, sender)) },
            text = { Text(stringResource(R.string.setup_not_my_bank_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.excludeSender(sender)
                    dropping = null
                }) { Text(stringResource(R.string.setup_not_my_bank)) }
            },
            dismissButton = {
                TextButton(onClick = { dropping = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * One message at a time: what the bank wrote, what Riyal made of it, yes or no. "No"
 * opens the answer, category first - a bank's message is far more often read the right
 * way round and filed under the wrong category than the other way about. Every answer
 * stays in a history the user can step back through, by the arrow or the back gesture,
 * to change one.
 */
@Composable
private fun QuestionsStep(vm: MainViewModel) {
    val question by vm.setupQuestion.collectAsState()
    val previous by vm.setupAnswer.collectAsState()
    val canStepBack by vm.canStepBack.collectAsState()
    val progress by vm.setupProgress.collectAsState()
    val left by vm.questionsLeft.collectAsState()
    val asked by vm.questionsAsked.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val categoryUse by vm.categoryUse.collectAsState()
    val q = question ?: return
    var correcting by remember(q.key, asked) { mutableStateOf(false) }
    BackHandler(enabled = canStepBack) { vm.stepBackSetup() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FlatBar(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.stepBackSetup() }, enabled = canStepBack) {
                    Icon(
                        painterResource(R.drawable.ic_sym_arrow_back),
                        contentDescription = stringResource(R.string.setup_previous),
                    )
                }
                Text(
                    stringResource(R.string.setup_question_count, asked + 1, left.coerceAtLeast(1)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.setup_question_title), style = MaterialTheme.typography.headlineSmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Account.bankLabel(q.sender).ifEmpty { q.sender },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Dates.pattern("d MMM uuuu").format(Instant.ofEpochMilli(q.atMillis).atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(q.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.setup_read_as),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val tint = when (q.guess) {
                            MessageKind.OUT -> MaterialTheme.colorScheme.error
                            MessageKind.IN -> successColor()
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(painterResource(iconFor(q.guess)), contentDescription = null, tint = tint)
                        Text(
                            stringResource(labelFor(q.guess)),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (q.amountMinor != null && q.currency != null) {
                            MoneyText(
                                q.amountMinor,
                                q.currency,
                                sign = when (q.guess) {
                                    MessageKind.OUT -> MoneySign.OUT
                                    MessageKind.IN -> MoneySign.IN
                                    else -> MoneySign.NONE
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = tint,
                            )
                        }
                    }
                    val txn = q.txn
                    if (txn != null && q.guess.direction != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CategoryIcon(txn.categoryId, size = 22.dp)
                            Text(Categories.byId(txn.categoryId).name, style = MaterialTheme.typography.bodyLarge)
                        }
                        val details = listOfNotNull(
                            txn.merchant?.let(::unmasked),
                            accounts.firstOrNull { it.id == txn.accountId }?.displayName,
                        )
                        if (details.isNotEmpty()) {
                            Text(
                                details.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (q.similar > 0) {
                        Text(
                            counted(R.plurals.setup_similar, q.similar),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            // Stepped back to: what was answered, so the user can see what they are changing.
            previous?.let { answer ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_sym_history),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        if (answer.confirmed) {
                            stringResource(R.string.setup_you_said_yes)
                        } else {
                            stringResource(
                                R.string.setup_you_said,
                                listOfNotNull(
                                    stringResource(labelFor(answer.kind)),
                                    answer.categoryId?.let { Categories.byId(it).name },
                                ).joinToString(" · "),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (correcting) {
                Correction(
                    question = q,
                    previous = previous?.takeUnless { it.confirmed },
                    categoryUse = categoryUse,
                    onSave = { kind, category -> vm.answerSetup(right = false, kind = kind, categoryId = category) },
                    onCancel = { correcting = false },
                )
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = { vm.finishSetup() }) { Text(stringResource(R.string.setup_finish_later)) }
                Spacer(Modifier.weight(1f))
                if (!correcting) {
                    OutlinedButton(onClick = { correcting = true }) { Text(stringResource(R.string.setup_no)) }
                    Button(onClick = { vm.answerSetup(right = true) }) { Text(stringResource(R.string.setup_yes)) }
                }
            }
        }
    }
}

/**
 * What the message really was. The reading stays as it was unless the user changes it:
 * a "no" is usually about the category, so the categories come first, and the other
 * kinds are a tap away for the time the money went the other way, or never moved.
 * A reading with no category of its own - a transfer, not a transaction - can only be
 * wrong in its kind, so that opens on the kinds.
 */
@Composable
private fun Correction(
    question: MainViewModel.SetupQuestion,
    previous: MainViewModel.SetupAnswer?,
    categoryUse: Map<String, Int>,
    onSave: (MessageKind, String?) -> Unit,
    onCancel: () -> Unit,
) {
    var kind by remember(question.key) { mutableStateOf(previous?.kind ?: question.guess) }
    var choosingKind by remember(question.key) {
        mutableStateOf(question.guess.direction == null || (previous != null && previous.kind != question.guess))
    }
    val type = when (kind) {
        MessageKind.OUT -> TxnType.EXPENSE
        MessageKind.IN -> TxnType.INCOME
        else -> null
    }
    var category by remember(question.key, kind) {
        val fits = { id: String -> type != null && Categories.byId(id).income == (type == TxnType.INCOME) }
        mutableStateOf(
            previous?.categoryId?.takeIf(fits)
                ?: question.txn?.categoryId?.takeIf(fits)
                ?: type?.let { Categories.defaultFor(it) }
        )
    }
    val order = rememberCategoryOrder(categoryUse)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (type != null) {
                Text(stringResource(R.string.setup_which_category), style = MaterialTheme.typography.titleMedium)
                CategoryChips(
                    type = type,
                    selectedId = category ?: Categories.defaultFor(type),
                    onSelect = { category = it },
                    order = order,
                )
            }
            if (choosingKind) {
                Text(stringResource(R.string.setup_what_was_it), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MessageKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(stringResource(labelFor(option))) },
                            leadingIcon = {
                                Icon(painterResource(iconFor(option)), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            } else {
                TextButton(onClick = { choosingKind = true }) {
                    Text(stringResource(R.string.setup_kind_wrong, stringResource(labelFor(question.guess))))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(kind, category.takeIf { type != null }) }) {
                    Text(stringResource(R.string.setup_save_answer))
                }
            }
        }
    }
}

@Composable
private fun DoneStep(vm: MainViewModel, onFinished: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val txns by vm.txns.collectAsState()
    val taught by vm.taughtCount.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_sym_check_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(stringResource(R.string.setup_done_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            listOf(
                counted(R.plurals.count_accounts, accounts.count { !it.archived }),
                counted(R.plurals.count_records, txns.size),
                counted(R.plurals.setup_kinds_taught, taught),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.setup_done_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_start))
        }
    }
}
