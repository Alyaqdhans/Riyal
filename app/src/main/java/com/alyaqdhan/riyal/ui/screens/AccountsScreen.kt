@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.alyaqdhan.riyal.ui.screens

import com.alyaqdhan.riyal.ui.compose.ActionButton
import com.alyaqdhan.riyal.ui.compose.ConfirmDeleteButton
import com.alyaqdhan.riyal.ui.compose.DeleteButton
import com.alyaqdhan.riyal.ui.compose.MenuChoice
import com.alyaqdhan.riyal.ui.compose.ChoiceMenu
import androidx.compose.foundation.layout.Arrangement
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.rememberDialogPop
import com.alyaqdhan.riyal.ui.compose.toolbarSpace
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.ui.res.painterResource
import com.alyaqdhan.riyal.data.AccountKind
import com.alyaqdhan.riyal.data.Banks
import com.alyaqdhan.riyal.ui.compose.iconFor
import com.alyaqdhan.riyal.ui.compose.labelFor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Numerals
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.ui.compose.MoneyText
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CurrencyField
import com.alyaqdhan.riyal.ui.compose.CurrencySign
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.bidi
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.Face
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.HelpAction
import com.alyaqdhan.riyal.ui.compose.ScanRefresh
import com.alyaqdhan.riyal.ui.theme.successColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import com.alyaqdhan.riyal.ui.theme.legible

private val asOfFmt: DateTimeFormatter get() = Dates.pattern("dd MMM uuuu, h:mm a")

/**
 * The bank account manager, and the first-run confirmation the app asks for once.
 *
 * Balances here are the app's only claim about how much money exists, and they were
 * read out of SMS - a good first guess, not gospel. So the page opens by saying exactly
 * that and asking the user to vouch for the numbers before anything relies on them.
 */
@Composable
fun AccountsScreen(vm: MainViewModel, onBack: (() -> Unit)?) {
    val accounts by vm.accounts.collectAsState()
    val balances by vm.balances.collectAsState()
    val needsConfirming by vm.accountsNeedConfirming.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var confirmDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.acct_title)) },
                // A tab has no way back; reached from somewhere, it does.
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = { HelpAction(
                        stringResource(R.string.acct_title),
                        stringResource(R.string.help_accounts),
                    ) },
            )
        },
    ) { padding ->
        ScanRefresh(vm, Modifier.padding(padding)) {
        Column {
            if (accounts.isEmpty()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EmptyState(
                        title = stringResource(R.string.acct_empty_title),
                        subtitle = stringResource(R.string.acct_empty_subtitle),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        ActionButton(
                            text = stringResource(R.string.acct_add),
                            icon = R.drawable.ic_sym_add,
                            onClick = { editing = blankAccount(vm.prefs.defaultCurrency) },
                        )
                    }
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = toolbarSpace + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (needsConfirming) {
                    item(key = "confirm") {
                        ConfirmBanner(
                            count = accounts.size,
                            anyMissingBalance = accounts.any { it.needsBalance },
                            onConfirm = { vm.confirmAccounts() },
                        )
                    }
                }
                item(key = "total") {
                    TotalRow(accounts, balances)
                }
                items(accounts, key = { it.id }) { account ->
                    AccountCard(
                        account = account,
                        balance = balances[account.id] ?: account.openingBalanceMinor,
                        onEdit = { editing = account },
                    )
                }
                item(key = "add") {
                    ActionButton(
                        text = stringResource(R.string.acct_add),
                        icon = R.drawable.ic_sym_add,
                        onClick = { editing = blankAccount(vm.prefs.defaultCurrency) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        }
    }

    editing?.let { account ->
        AccountEditorDialog(
            account = account,
            isNew = accounts.none { it.id == account.id },
            onSave = {
                vm.saveAccount(it)
                editing = null
            },
            onDelete = {
                confirmDelete = account
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

    confirmDelete?.let { account ->
        val pop = rememberDialogPop()
        AlertDialog(
            onDismissRequest = { pop.leave { confirmDelete = null } },
            modifier = Modifier.popIn(pop),
            title = {
                Text(stringResource(R.string.delete_title, account.displayName))
            },
            text = { Text(stringResource(R.string.acct_delete_body)) },
            confirmButton = {
                ConfirmDeleteButton(stringResource(R.string.action_delete)) {
                    pop.leave {
                        vm.deleteAccount(account.id)
                        confirmDelete = null
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pop.leave { confirmDelete = null } }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ConfirmBanner(count: Int, anyMissingBalance: Boolean, onConfirm: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_sym_account_balance), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                Column {
                    Text(
                        stringResource(R.string.acct_confirm_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    // One sentence, and only the one this state needs: the banner used
                    // to say the same thing three ways before the button that ends it.
                    Text(
                        stringResource(
                            if (anyMissingBalance) R.string.acct_confirm_missing
                            else R.string.acct_confirm_ok,
                            counted(R.plurals.count_accounts, count),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.acct_confirm_button)) }
        }
    }
}

@Composable
private fun TotalRow(accounts: List<Account>, balances: Map<String, Long>) {
    // Only same-currency accounts can be added up; mixing them would invent an exchange
    // rate the app has no business guessing.
    val live = accounts.filter { !it.archived }
    val byCurrency = live.groupBy { it.currency }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.acct_total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        byCurrency.forEach { (currency, group) ->
            val total = group.sumOf { balances[it.id] ?: it.openingBalanceMinor }
            MoneyText(total, currency,
                roll = true,
                style = MaterialTheme.typography.headlineSmall,
                color = if (total < 0) MaterialTheme.colorScheme.error else successColor(),
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    balance: Long,
    onEdit: () -> Unit,
) {
    // Two lines: which account, and what is in it. Everything else - the opening figure,
    // the senders it reads, Edit and Delete - is in the editor this row opens, which is
    // where it can actually be changed.
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            onClick = onEdit,
            leadingContent = {
                Icon(
                    painterResource(iconFor(account.kind)),
                    contentDescription = null,
                    tint = legible(Color(if (account.color != 0) account.color else Categories.colorFor("other"))),
                )
            },
            supportingContent = {
                Text(
                    if (account.needsBalance) stringResource(R.string.acct_needs_balance)
                    else stringResource(labelFor(account.kind)),
                    color = if (account.needsBalance) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                MoneyText(
                    balance,
                    account.currency,
                    roll = true,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (balance < 0) MaterialTheme.colorScheme.error else successColor(),
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) { Text(account.displayName, style = MaterialTheme.typography.titleMedium) }
    }
}

/**
 * Everything about one account, in one dialog: what to call it, which bank and what
 * sort of account it is, what is in it, which digits and senders route messages to it.
 * The setup's accounts step opens it too.
 */
@Composable
internal fun AccountEditorDialog(
    account: Account,
    isNew: Boolean,
    onSave: (Account) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** The bank's other accounts, any of which this one may turn out to be. */
    mergeTargets: List<Account> = emptyList(),
    onMerge: (Account) -> Unit = {},
) {
    var merging by remember { mutableStateOf<Account?>(null) }
    var pickingMerge by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(account.name) }
    var bankName by remember { mutableStateOf(account.bankName) }
    var last4 by remember { mutableStateOf(account.last4.orEmpty()) }
    var currency by remember { mutableStateOf(account.currency) }
    var senders by remember { mutableStateOf(account.senderIds.joinToString(", ")) }
    var archived by remember { mutableStateOf(account.archived) }
    var kind by remember { mutableStateOf(account.kind) }
    var balance by remember {
        mutableStateOf(
            if (account.openingBalanceMinor == 0L && account.needsBalance) ""
            else Money.toMajor(account.openingBalanceMinor, account.currency).toPlainString()
        )
    }
    val parsedBalance = Numerals.decimal(balance)
    val pop = rememberDialogPop()

    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.acct_add_title else R.string.acct_edit_title
                )
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.acct_nickname)) },
                    placeholder = { Text(Account.defaultNameOf(bankName, last4.ifBlank { null })) },
                    supportingText = {
                        Text(stringResource(R.string.acct_nickname_help))
                    },
                    singleLine = true,
                )
                BankField(value = bankName, onValueChange = { bankName = it })
                Text(stringResource(R.string.acct_kind), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(stringResource(labelFor(option))) },
                            leadingIcon = {
                                Icon(
                                    painterResource(iconFor(option)),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = {
                        Text(
                            stringResource(
                                if (isNew) R.string.acct_balance_now
                                else R.string.acct_opening_balance
                            )
                        )
                    },
                    suffix = { CurrencySign(currency) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    if (isNew) {
                        stringResource(R.string.acct_balance_new_help)
                    } else {
                        stringResource(
                            R.string.acct_balance_asof,
                            asOfFmt.format(
                                Instant.ofEpochMilli(account.openingAtMillis)
                                    .atZone(ZoneId.systemDefault())
                            ),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CurrencyField(
                    label = stringResource(R.string.label_currency),
                    code = currency,
                    onPick = { currency = it },
                )
                OutlinedTextField(
                    value = last4,
                    onValueChange = { last4 = Numerals.latin(it).filter { c -> c in '0'..'9' }.take(6) },
                    label = { Text(stringResource(R.string.acct_last4)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                // A bank can quote other digits where the account usually goes; when one
                // of those became an account, this is how it is put back.
                if (!isNew && mergeTargets.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { pickingMerge = true }, shapes = ButtonDefaults.shapes()) {
                            Icon(
                                painterResource(R.drawable.ic_sym_merge),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.acct_same_as),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        ChoiceMenu(expanded = pickingMerge, onDismiss = { pickingMerge = false }) {
                            mergeTargets.forEachIndexed { index, target ->
                                MenuChoice(
                                    text = target.displayName,
                                    selected = false,
                                    index = index,
                                    count = mergeTargets.size,
                                    icon = iconFor(target.kind),
                                ) {
                                    pickingMerge = false
                                    merging = target
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = senders,
                    onValueChange = { senders = it },
                    label = { Text(stringResource(R.string.acct_senders)) },
                    placeholder = { Text(stringResource(R.string.acct_senders_placeholder)) },
                    supportingText = { Text(stringResource(R.string.acct_senders_help)) },
                )
                // The whole row is the switch, and stays plain when on.
                val plain = ListItemDefaults.colors(containerColor = Color.Transparent)
                ListItem(
                    checked = archived,
                    onCheckedChange = { archived = it },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = Color.Transparent,
                        selectedContentColor = plain.contentColor,
                        selectedSupportingContentColor = plain.supportingContentColor,
                        selectedTrailingContentColor = plain.trailingContentColor,
                    ),
                    supportingContent = { Text(stringResource(R.string.acct_archived_detail)) },
                    trailingContent = { Switch(checked = archived, onCheckedChange = null) },
                ) { Text(stringResource(R.string.acct_archived)) }
                if (!isNew) DeleteButton(onClick = { pop.leave(onDelete) })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() || bankName.isNotBlank() || last4.isNotBlank(),
                onClick = {
                    val minor = parsedBalance?.let { Money.toMinor(it, currency) } ?: 0L
                    val edited = account.copy(
                        name = name.trim(),
                        bankName = bankName.trim(),
                        last4 = last4.trim().ifBlank { null },
                        currency = currency,
                        openingBalanceMinor = minor,
                        // A hand-entered figure is true as of now, so the opening
                        // moment moves with it and older records stop double-counting.
                        openingAtMillis = if (minor != account.openingBalanceMinor) {
                            System.currentTimeMillis()
                        } else {
                            account.openingAtMillis
                        },
                        senderIds = senders.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                        archived = archived,
                        needsBalance = false,
                        kind = kind,
                    )
                    pop.leave { onSave(edited) }
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = { pop.leave(onDismiss) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    merging?.let { target ->
        val confirmPop = rememberDialogPop()
        AlertDialog(
            onDismissRequest = { confirmPop.leave { merging = null } },
            modifier = Modifier.popIn(confirmPop),
            title = { Text(stringResource(R.string.acct_merge_title, target.displayName)) },
            text = { Text(stringResource(R.string.acct_merge_body, account.displayName, target.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    // The question goes, then the editor it was asked over.
                    confirmPop.leave {
                        merging = null
                        pop.leave { onMerge(target) }
                    }
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.acct_merge)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPop.leave { merging = null } }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The accounts [account] could be the same as: the others its bank texts about. */
internal fun mergeTargetsFor(account: Account, accounts: List<Account>): List<Account> {
    val senders = account.senderIds.mapTo(HashSet()) { it.trim().lowercase() }
    return accounts.filter { other ->
        other.id != account.id && !other.archived &&
            (other.senderIds.any { it.trim().lowercase() in senders } ||
                other.bankName.isNotBlank() && other.bankName == account.bankName)
    }
}

private fun blankAccount(currency: String) = Account(
    id = Account.ID_PREFIX + UUID.randomUUID().toString().take(8),
    name = "",
    bankName = "",
    last4 = null,
    currency = currency,
    openingBalanceMinor = 0L,
    openingAtMillis = System.currentTimeMillis(),
    color = Categories.PALETTE.random(),
    needsBalance = true,
)

/**
 * The bank, typed or picked: the stock editable dropdown, suggesting the banks the app
 * knows by name as the user types, and taking any other name as it is.
 */
@Composable
private fun BankField(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(value) {
        Banks.KNOWN.filter { value.isBlank() || it.contains(value.trim(), ignoreCase = true) }
    }
    ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.label_bank)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_sym_account_balance), contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEachIndexed { index, bank ->
                MenuChoice(
                    text = Account.bankLabel(bank),
                    selected = bank == value,
                    index = index,
                    count = suggestions.size,
                    icon = R.drawable.ic_sym_account_balance,
                ) {
                    onValueChange(bank)
                    expanded = false
                }
            }
        }
    }
}
