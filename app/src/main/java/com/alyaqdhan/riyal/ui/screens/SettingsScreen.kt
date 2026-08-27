@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.alyaqdhan.riyal.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CURRENCIES
import com.alyaqdhan.riyal.ui.compose.CategoryIcon
import com.alyaqdhan.riyal.ui.compose.DropdownField
import com.alyaqdhan.riyal.ui.compose.plainText
import kotlinx.coroutines.launch

/**
 * Everything the scanner does is decided here: gate keywords, sender allowlist, scan
 * range, currency, rules, and hard controls over the data itself. Every change is
 * echoed into the verbose log.
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = vm.prefs
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val hasPerm by vm.hasSmsPermission.collectAsState()
    val knownSenders by vm.senders.collectAsState()
    val rules by vm.rules.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val customCategories by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()

    var expenseKw by remember { mutableStateOf(prefs.expenseKeywords) }
    var incomeKw by remember { mutableStateOf(prefs.incomeKeywords) }
    var newExpenseKw by remember { mutableStateOf("") }
    var newIncomeKw by remember { mutableStateOf("") }
    var rangeMonths by remember { mutableStateOf(prefs.scanRangeMonths) }
    var currency by remember { mutableStateOf(prefs.defaultCurrency) }
    var senderFilter by remember { mutableStateOf(prefs.senderFilterEnabled) }
    var allowlist by remember { mutableStateOf(prefs.senderAllowlist) }
    var newSender by remember { mutableStateOf("") }
    var bankOnly by remember { mutableStateOf(prefs.bankSendersOnly) }
    var scanOnLaunch by remember { mutableStateOf(prefs.scanOnLaunch) }
    var smartRules by remember { mutableStateOf(prefs.smartRules) }
    var budgetsEnabled by remember { mutableStateOf(prefs.budgetsEnabled) }
    var autoConfirmTransfers by remember { mutableStateOf(prefs.autoConfirmTransfers) }
    var confirmWipe by remember { mutableStateOf(false) }

    fun note(text: String) {
        Verbose.info("setting changed by you: $text")
        Verbose.flush()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── permission
            SettingsCard("Permission") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (hasPerm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Column {
                        Text(
                            if (hasPerm) "SMS reading is allowed" else "SMS reading is off",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "READ_SMS is the only permission this app declares. No internet, no background receivers, no notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }) { Text("Manage in system settings") }
            }

            // ── scanning behavior
            SettingsCard("Scanning") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Scan when the app opens", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "One quiet scan on launch. Off = only pull-to-refresh or the Scan button.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = scanOnLaunch,
                        onCheckedChange = {
                            scanOnLaunch = it
                            prefs.scanOnLaunch = it
                            note("scan on app open ${if (it) "enabled" else "disabled"}")
                        },
                    )
                }
            }

            // ── smart behavior
            SettingsCard("Smart") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Learn from my corrections", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "When you fix a category, the merchant is remembered automatically and applied to past and future messages. The picker still lets you opt out per edit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = smartRules,
                        onCheckedChange = {
                            smartRules = it
                            prefs.smartRules = it
                            note("smart category learning ${if (it) "enabled" else "disabled"}")
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Confirm transfers for me", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (autoConfirmTransfers) {
                                "A matching pair — same amount and currency, two of your own " +
                                    "accounts, minutes apart — is treated as one transfer straight " +
                                    "away, so it never counts as spending or income. Any of them " +
                                    "can be split back apart from its row."
                            } else {
                                "Off. Every matching pair waits in Review for your yes or no."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoConfirmTransfers,
                        onCheckedChange = {
                            autoConfirmTransfers = it
                            vm.autoConfirmTransfers = it
                            note("auto-confirm transfers ${if (it) "enabled" else "disabled"}")
                        },
                    )
                }
            }

            // ── money: the two pages that own accounts and categories, and the switch
            // that decides whether Home carries a budget section at all.
            SettingsCard("Money") {
                NavRow(
                    title = "Bank accounts",
                    subtitle = if (accounts.isEmpty()) {
                        "None yet — a scan sets them up from your bank's own messages."
                    } else {
                        accounts.joinToString(", ") { it.displayName }
                    },
                    onClick = onOpenAccounts,
                )
                NavRow(
                    title = "Categories",
                    subtitle = if (customCategories.isEmpty()) {
                        "Browse what you spend and earn, and add your own categories."
                    } else {
                        "${customCategories.size} of your own, plus the built-in ones"
                    },
                    onClick = onOpenCategories,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Budget", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (budgetsEnabled) {
                                "Home shows a budget section: cap what you spend per category " +
                                    "for any period, with progress bars." +
                                    if (budgets.isNotEmpty()) " ${budgets.size} plan(s) so far." else ""
                            } else {
                                "Off. Turn it on to plan spending per category on the Home screen."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = budgetsEnabled,
                        onCheckedChange = {
                            budgetsEnabled = it
                            vm.budgetsEnabled = it
                            note("budget planning ${if (it) "enabled" else "disabled"}")
                        },
                    )
                }
            }

            // ── scan range
            SettingsCard("Scan range") {
                Text(
                    "How far back a scan looks. Messages outside the range are not even queried from the inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val ranges = listOf(1 to "1 mo", 3 to "3 mo", 6 to "6 mo", 12 to "1 yr", 0 to "All")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ranges.forEachIndexed { index, (months, label) ->
                        SegmentedButton(
                            selected = rangeMonths == months,
                            onClick = {
                                rangeMonths = months
                                prefs.scanRangeMonths = months
                                note("scan range → $label")
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size),
                        ) { Text(label) }
                    }
                }
            }

            // ── keywords
            SettingsCard("Gate keywords") {
                Text(
                    "A message is processed only if it contains one of these words. Everything else is skipped, unread and unstored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Money out", style = MaterialTheme.typography.labelLarge)
                KeywordChips(expenseKw) { kw ->
                    expenseKw = expenseKw - kw
                    prefs.expenseKeywords = expenseKw
                    note("removed expense keyword \"$kw\"")
                }
                AddKeywordRow(
                    value = newExpenseKw,
                    onValueChange = { newExpenseKw = it },
                    onAdd = {
                        val kw = newExpenseKw.trim().lowercase()
                        if (kw.isNotEmpty()) {
                            expenseKw = expenseKw + kw
                            prefs.expenseKeywords = expenseKw
                            note("added expense keyword \"$kw\"")
                        }
                        newExpenseKw = ""
                    },
                )
                Text("Money in", style = MaterialTheme.typography.labelLarge)
                KeywordChips(incomeKw) { kw ->
                    incomeKw = incomeKw - kw
                    prefs.incomeKeywords = incomeKw
                    note("removed income keyword \"$kw\"")
                }
                AddKeywordRow(
                    value = newIncomeKw,
                    onValueChange = { newIncomeKw = it },
                    onAdd = {
                        val kw = newIncomeKw.trim().lowercase()
                        if (kw.isNotEmpty()) {
                            incomeKw = incomeKw + kw
                            prefs.incomeKeywords = incomeKw
                            note("added income keyword \"$kw\"")
                        }
                        newIncomeKw = ""
                    },
                )
                TextButton(onClick = {
                    prefs.resetKeywords()
                    expenseKw = prefs.expenseKeywords
                    incomeKw = prefs.incomeKeywords
                    note("keywords reset to defaults (withdraw/debited/purchase… + deposit/credited/salary… incl. Arabic)")
                }) { Text("Reset to defaults") }
            }

            // ── senders
            SettingsCard("Senders") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bank senders only", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Only sender names containing “bank” / “بنك” / “مصرف” are read. Banks that brand differently (NBO, Sohar Intl…), approve them below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bankOnly,
                        onCheckedChange = {
                            bankOnly = it
                            prefs.bankSendersOnly = it
                            note("bank-senders-only ${if (it) "enabled" else "disabled"}")
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only scan senders I approve", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Off = every sender is considered (bodies still keyword-gated).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = senderFilter,
                        onCheckedChange = {
                            senderFilter = it
                            prefs.senderFilterEnabled = it
                            note("sender allowlist ${if (it) "enabled" else "disabled"}")
                        },
                    )
                }
                if (senderFilter || bankOnly) {
                    if (allowlist.isEmpty()) {
                        Text(
                            if (senderFilter) "No approved senders yet, a scan will match nothing."
                            else "No extra approved senders, only bank-named senders are read.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (senderFilter) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        KeywordChips(allowlist) { sender ->
                            allowlist = allowlist - sender
                            prefs.senderAllowlist = allowlist
                            note("removed sender \"$sender\" from allowlist")
                        }
                    }
                    // Add a sender by name directly, for a bank whose ID hasn't shown up
                    // in the inbox yet (or a contact number you know is a bank).
                    Text(
                        "Add a sender the app hasn't seen yet:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AddKeywordRow(
                        value = newSender,
                        onValueChange = { newSender = it },
                        label = "Sender name",
                        onAdd = {
                            val s = newSender.trim()
                            if (s.isNotEmpty() && s !in allowlist) {
                                allowlist = allowlist + s
                                prefs.senderAllowlist = allowlist
                                note("approved sender \"$s\" (added by name)")
                            }
                            newSender = ""
                        },
                    )
                    val suggestions = remember(knownSenders, allowlist) {
                        (knownSenders - allowlist).sorted().take(12)
                    }
                    if (suggestions.isNotEmpty()) {
                        Text(
                            "Seen in your inbox, tap to approve:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            suggestions.forEach { sender ->
                                AssistChip(
                                    onClick = {
                                        allowlist = allowlist + sender
                                        prefs.senderAllowlist = allowlist
                                        note("approved sender \"$sender\"")
                                    },
                                    label = { Text(sender) },
                                )
                            }
                        }
                    }
                }
            }

            // ── rules
            SettingsCard("Your category rules") {
                if (rules.isEmpty()) {
                    Text(
                        "None yet. Create one from any transaction: pick a category and switch on “Always”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                rules.forEach { rule ->
                    val cat = Categories.byId(rule.categoryId)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryIcon(cat.id)
                        Text(
                            "\"${rule.pattern}\" → ${cat.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.removeRule(rule.pattern) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
                        }
                    }
                }
            }

            // ── currency
            SettingsCard("Default currency") {
                Text(
                    "Used when a message doesn't name a currency. OMR amounts keep 3 decimals (baisa).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DropdownField(
                    label = "Currency",
                    value = currency,
                    options = CURRENCIES,
                    display = { it },
                    onSelect = {
                        currency = it
                        prefs.defaultCurrency = it
                        note("default currency → $it")
                    },
                )
            }

            // ── data & privacy
            SettingsCard("Data & privacy") {
                Text(
                    "Everything lives in one JSON file inside this app's private storage. Backups are disabled. The manifest declares no INTERNET permission, verifiable with any APK inspector.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        scope.launch { clipboard.setClipEntry(plainText(Verbose.dump())) }
                        note("verbose log copied to clipboard")
                    }) { Text("Copy verbose log") }
                    TextButton(onClick = {
                        Verbose.clear()
                        Verbose.info("log cleared by you")
                        Verbose.flush()
                    }) { Text("Clear log") }
                }
                FilledTonalButton(onClick = { confirmWipe = true }) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Delete all app data")
                }
                Text(
                    "Riyal ${appVersion(context)} · made for Oman 🇴🇲 · OMR-first with Arabic SMS support",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Room for the floating toolbar hovering over the content.
            Spacer(Modifier.height(88.dp))
        }
    }


    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "All recorded transactions, rules, review items and settings will be erased. " +
                        "Your SMS inbox itself is untouched, this app never modifies messages.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.wipeAll()
                    confirmWipe = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun KeywordChips(items: Set<String>, onRemove: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            InputChip(
                selected = false,
                onClick = {},
                label = { Text(item) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove $item",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onRemove(item) },
                    )
                },
            )
        }
    }
}

@Composable
private fun AddKeywordRow(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    label: String = "Add keyword",
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onAdd, enabled = value.isNotBlank()) { Text("Add") }
    }
}

/**
 * Add or edit a custom category: name, a color from the palette, and (when adding)
 * whether it's income or expense. Direction is fixed once created, since moving a
 * category between income and expense would strand its transactions on the wrong side.
 */
/** A settings entry that leads somewhere rather than toggling something. */
@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
} catch (e: Exception) {
    "1.0"
}
