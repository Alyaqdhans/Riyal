@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.ui.compose.countOf
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CURRENCIES
import com.alyaqdhan.riyal.ui.compose.DropdownField
import com.alyaqdhan.riyal.ui.compose.EmptyState
import com.alyaqdhan.riyal.ui.compose.Face
import com.alyaqdhan.riyal.ui.compose.FaceStyle
import com.alyaqdhan.riyal.ui.compose.HelpAction
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.pressBounce
import com.alyaqdhan.riyal.ui.theme.successColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val asOfFmt = DateTimeFormatter.ofPattern("dd MMM uuuu, h:mm a")

/**
 * The bank account manager, and the first-run confirmation the app asks for once.
 *
 * Balances here are the app's only claim about how much money exists, and they were
 * read out of SMS - a good first guess, not gospel. So the page opens by saying exactly
 * that and asking the user to vouch for the numbers before anything relies on them.
 */
/** What the page is for, behind the (i) rather than above the work. */
private const val HELP =
    "The accounts Riyal found in your bank's own messages, and the balance each one " +
        "last quoted. A balance read out of a text is a good first guess and nothing " +
        "more, so open any account to set the real figure and the date it was true.\n\n" +
        "An account also carries the sender names that belong to it, which is how a " +
        "message gets routed to the right balance. Archiving one keeps its records, " +
        "because the money still moved."

@Composable
fun AccountsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val balances by vm.balances.collectAsState()
    val needsConfirming by vm.accountsNeedConfirming.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var confirmDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bank accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { HelpAction("Bank accounts", HELP) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (accounts.isEmpty()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EmptyState(
                        style = FaceStyle.SLEEPY,
                        title = "No accounts yet",
                        subtitle = "Scan your messages and Riyal will set accounts up from what your " +
                            "bank already told you, or add one here by hand.",
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        FilledTonalButton(
                            onClick = { editing = blankAccount(vm.prefs.defaultCurrency) },
                            modifier = Modifier.pressBounce(),
                        ) { Text("Add an account") }
                    }
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
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
                    FilledTonalButton(
                        onClick = { editing = blankAccount(vm.prefs.defaultCurrency) },
                        modifier = Modifier.fillMaxWidth().pressBounce(),
                    ) { Text("Add an account") }
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
        )
    }

    confirmDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${account.displayName}?") },
            text = {
                Text(
                    "Its transactions are kept, because the money still moved, but they'll no longer " +
                        "belong to any account, so they stop counting towards a balance."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAccount(account.id)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConfirmBanner(count: Int, anyMissingBalance: Boolean, onConfirm: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().popIn(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Face(mood = 0.4f, style = FaceStyle.CONFUSED, modifier = Modifier.size(48.dp))
                Column {
                    Text(
                        "Are these right?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    // One sentence, and only the one this state needs: the banner used
                    // to say the same thing three ways before the button that ends it.
                    Text(
                        if (anyMissingBalance) {
                            countOf(count, "account") + " read from your bank's texts. One quoted no " +
                                "balance and starts at zero. Tap it to set the real figure."
                        } else {
                            countOf(count, "account") + " read from the balances your bank's texts quote. " +
                                "Tap any that look wrong."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().pressBounce(),
            ) { Text("These are correct") }
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
            "Total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        byCurrency.forEach { (currency, group) ->
            val total = group.sumOf { balances[it.id] ?: it.openingBalanceMinor }
            Text(
                Money.format(total, currency),
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
    // Two lines: which account, and what is in it. Everything else the card used to
    // recite - the opening figure, the senders it reads, Edit and Delete - is in the
    // editor this row opens, which is where it can actually be changed.
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .popIn()
            .pressBounce(0.98f),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        Color(if (account.color != 0) account.color else Categories.colorFor("other"))
                    ),
            )
            Column(Modifier.weight(1f)) {
                Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                if (account.needsBalance) {
                    Text(
                        "No balance in any message, tap to set it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                Money.format(balance, account.currency),
                style = MaterialTheme.typography.titleMedium,
                color = if (balance < 0) MaterialTheme.colorScheme.error else successColor(),
            )
        }
    }
}

@Composable
private fun AccountEditorDialog(
    account: Account,
    isNew: Boolean,
    onSave: (Account) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(account.name) }
    var bankName by remember { mutableStateOf(account.bankName) }
    var last4 by remember { mutableStateOf(account.last4.orEmpty()) }
    var currency by remember { mutableStateOf(account.currency) }
    var senders by remember { mutableStateOf(account.senderIds.joinToString(", ")) }
    var archived by remember { mutableStateOf(account.archived) }
    var balance by remember {
        mutableStateOf(
            if (account.openingBalanceMinor == 0L && account.needsBalance) ""
            else Money.toMajor(account.openingBalanceMinor, account.currency).toPlainString()
        )
    }
    val parsedBalance = balance.trim().replace(",", "").toBigDecimalOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add account" else "Edit account") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nickname (optional)") },
                    placeholder = { Text(Account.defaultNameOf(bankName, last4.ifBlank { null })) },
                    supportingText = {
                        Text("Leave it empty and the account is named after its bank and last digits.")
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text(if (isNew) "Balance now" else "Opening balance") },
                    suffix = { Text(currency) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    if (isNew) {
                        "Everything Riyal records from now on moves this figure."
                    } else {
                        "The balance as of " +
                            asOfFmt.format(
                                Instant.ofEpochMilli(account.openingAtMillis).atZone(ZoneId.systemDefault())
                            ) + ". Records after that moment move it."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DropdownField(
                    label = "Currency",
                    value = currency,
                    options = CURRENCIES,
                    display = { it },
                    onSelect = { currency = it },
                )
                OutlinedTextField(
                    value = last4,
                    onValueChange = { last4 = it.filter(Char::isDigit).take(6) },
                    label = { Text("Account ends with (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = senders,
                    onValueChange = { senders = it },
                    label = { Text("SMS senders") },
                    placeholder = { Text("BankMuscat, NBO") },
                    supportingText = {
                        Text("Messages from these senders are filed under this account.")
                    },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Switch(checked = archived, onCheckedChange = { archived = it })
                    Text("Closed account (hide it from pickers)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() || bankName.isNotBlank() || last4.isNotBlank(),
                onClick = {
                    val minor = parsedBalance?.let { Money.toMinor(it, currency) } ?: 0L
                    onSave(
                        account.copy(
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
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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
