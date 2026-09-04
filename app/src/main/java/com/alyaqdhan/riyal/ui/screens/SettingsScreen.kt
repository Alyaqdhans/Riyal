@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.alyaqdhan.riyal.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.ui.compose.countOf
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CURRENCIES
import com.alyaqdhan.riyal.ui.compose.ToolbarSpacer
import com.alyaqdhan.riyal.ui.compose.plainText
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.pressBounce
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val settingsDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu")

/**
 * Everything the scanner does is decided here, but the reason to open Settings is
 * usually to *check* something rather than change it - so the screen opens with what
 * the app has actually done, and only then offers the switches.
 *
 * Each row is one line: a name, its current value, and the control. The paragraph that
 * used to sit under every row is behind the (i) instead, because a screen where every
 * setting explains itself at rest has to be read rather than scanned. The few
 * explanations still on show are the ones describing *this* state rather than what a
 * control would do - a fresh-start floor, an allowlist that would match nothing.
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = vm.prefs
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val hasPerm by vm.hasSmsPermission.collectAsState()
    val knownSenders by vm.senders.collectAsState()
    val rules by vm.rules.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val txns by vm.txns.collectAsState()
    val lastSummary by vm.lastSummary.collectAsState()

    var expenseKw by remember { mutableStateOf(prefs.expenseKeywords) }
    var incomeKw by remember { mutableStateOf(prefs.incomeKeywords) }
    var newExpenseKw by remember { mutableStateOf("") }
    var newIncomeKw by remember { mutableStateOf("") }
    var rangeMonths by remember { mutableStateOf(prefs.scanRangeMonths) }
    var freshStart by remember { mutableStateOf(prefs.scanSinceMillis) }
    var currency by remember { mutableStateOf(prefs.defaultCurrency) }
    var senderFilter by remember { mutableStateOf(prefs.senderFilterEnabled) }
    var allowlist by remember { mutableStateOf(prefs.senderAllowlist) }
    var newSender by remember { mutableStateOf("") }
    var bankOnly by remember { mutableStateOf(prefs.bankSendersOnly) }
    var scanOnLaunch by remember { mutableStateOf(prefs.scanOnLaunch) }
    var smartRules by remember { mutableStateOf(prefs.smartRules) }
    var budgetsEnabled by remember { mutableStateOf(prefs.budgetsEnabled) }
    var helpOnPage by remember { mutableStateOf(prefs.showHelpText) }
    var autoConfirmTransfers by remember { mutableStateOf(prefs.autoConfirmTransfers) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(
                lastScanAt = prefs.lastScanAt,
                records = txns.size,
                accounts = accounts.size,
                messagesRead = lastSummary?.scanned,
            )

            SettingsCard("Your money") {
                ValueLine(
                    title = "Default currency",
                    value = currency,
                    detail = "Used when a message doesn't name a currency. OMR amounts keep " +
                        "3 decimals (baisa).",
                    onClick = { pickCurrency = true },
                )
                NavLine(
                    title = "Bank accounts",
                    value = if (accounts.isEmpty()) "none yet" else "${accounts.size}",
                    detail = "The accounts read out of your bank's own messages, their balances " +
                        "and which sender belongs to which.",
                    onClick = onOpenAccounts,
                )
                NavLine(
                    title = "Categories",
                    value = if (rules.isEmpty()) null else "${rules.size} learned",
                    detail = "Every category and what it cost, where your own are made - and " +
                        "the names Riyal has learned: the ones it files without asking, and " +
                        "the ones you asked to be asked about every time.",
                    onClick = onOpenCategories,
                )
                SwitchLine(
                    title = "Budget",
                    value = if (budgetsEnabled && budgets.isNotEmpty()) countOf(budgets.size, "plan") else null,
                    checked = budgetsEnabled,
                    onCheckedChange = {
                        budgetsEnabled = it
                        vm.budgetsEnabled = it
                        note("budget planning ${if (it) "enabled" else "disabled"}")
                    },
                    detail = "Home gains a budget section for the month it is showing: a cap per " +
                        "category, a bar for each, and a marker for whether the money is going " +
                        "faster than the calendar. A plan can cover any period, set from its editor.",
                )
            }

            SettingsCard("Scanning") {
                ActionLine(
                    title = "SMS permission",
                    value = if (hasPerm) "allowed" else "off",
                    valueIsWarning = !hasPerm,
                    detail = "READ_SMS is the only permission this app declares. No internet, " +
                        "no background receivers, no notifications.",
                    actionLabel = "Manage",
                    onAction = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                )
                SwitchLine(
                    title = "Scan when the app opens",
                    checked = scanOnLaunch,
                    onCheckedChange = {
                        scanOnLaunch = it
                        prefs.scanOnLaunch = it
                        note("scan on app open ${if (it) "enabled" else "disabled"}")
                    },
                    detail = "Riyal reads the inbox once when it starts. With this off, a scan " +
                        "only happens when you pull down to refresh.",
                )

                ExpandLine(
                    title = "How far back",
                    value = rangeLabel(rangeMonths),
                    detail = "How far back a scan looks. Messages outside the range are not " +
                        "even queried from the inbox.",
                ) {
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
                                modifier = Modifier.weight(1f),
                                icon = {},
                            ) { Text(label, softWrap = false, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
                // The fresh-start floor overrides the range above, so it stays on screen
                // rather than behind a tap: it is the reason "everything" can still show
                // nothing older than the day it was chosen.
                if (freshStart > 0L) {
                    StateNote(
                        "Nothing before ${settingsDayFmt.format(
                            Instant.ofEpochMilli(freshStart).atZone(ZoneId.systemDefault())
                        )} is read, whichever range is picked.",
                        action = "Read older messages too",
                        onAction = {
                            prefs.scanSinceMillis = 0L
                            freshStart = 0L
                            note("fresh start lifted, older messages can be read again")
                        },
                    )
                }

                ExpandLine(
                    title = "Gate keywords",
                    value = "${expenseKw.size} out · ${incomeKw.size} in",
                    detail = "A message is processed only if it contains one of these words. " +
                        "Everything else is skipped, unread and unstored.",
                ) {
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

                ExpandLine(
                    title = "Who is read",
                    value = senderSummary(bankOnly, senderFilter, allowlist.size),
                    detail = "Two filters over the sender name, on top of the gate keywords. " +
                        "Banks that don't brand themselves as banks (NBO, Sohar Intl, " +
                        "Meethaq) are approved by name in the list instead.",
                ) {
                    SwitchLine(
                        title = "Bank senders only",
                        checked = bankOnly,
                        onCheckedChange = {
                            bankOnly = it
                            prefs.bankSendersOnly = it
                            note("bank-senders-only ${if (it) "enabled" else "disabled"}")
                        },
                        detail = "Only senders whose name contains “bank”, “بنك” or “مصرف” are " +
                            "read. Anything else has to be approved by name below.",
                    )
                    SwitchLine(
                        title = "Only senders I approve",
                        checked = senderFilter,
                        onCheckedChange = {
                            senderFilter = it
                            prefs.senderFilterEnabled = it
                            note("sender allowlist ${if (it) "enabled" else "disabled"}")
                        },
                        detail = "With this off, every sender is considered and the message body " +
                            "still has to contain one of the gate keywords to be read at all.",
                    )
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
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                ActionLine(
                    title = "Scan now",
                    value = null,
                    detail = "Reads the inbox once, exactly as pulling down to refresh does. " +
                        "Riyal has no background receiver: it reads only when you ask it to.",
                    actionLabel = "Scan",
                    onAction = { vm.startScan() },
                )
            }

            SettingsCard("Automation") {
                SwitchLine(
                    title = "Learn from my corrections",
                    checked = smartRules,
                    onCheckedChange = {
                        smartRules = it
                        prefs.smartRules = it
                        note("smart category learning ${if (it) "enabled" else "disabled"}")
                    },
                    detail = "When you fix a category on a transaction with a merchant, that " +
                        "merchant is remembered and applied to past and future messages. The " +
                        "category picker still lets you opt out for a single edit, and a name " +
                        "marked “ask every time” is never remembered at all.",
                )
                SwitchLine(
                    title = "Confirm transfers for me",
                    checked = autoConfirmTransfers,
                    onCheckedChange = {
                        autoConfirmTransfers = it
                        vm.autoConfirmTransfers = it
                        note("auto-confirm transfers ${if (it) "enabled" else "disabled"}")
                    },
                    detail = "A matching pair is the same amount and currency, moving between " +
                        "two of your own accounts, minutes apart. With this on it becomes one " +
                        "transfer straight away and stops counting as spending or income; any " +
                        "of them can be split back apart from its row in Activity. With it off, " +
                        "every pair waits in Review for your yes or no.",
                )
            }

            SettingsCard("Your data") {
                ActionLine(
                    title = "Export transactions",
                    // The count belongs to the moment you ask for the file, not to a row
                    // you are only reading past. It is said in the confirmation instead.
                    value = null,
                    detail = "Writes every record to a CSV file you choose: date, type, amount, " +
                        "accounts, merchant, category and the message it was read from.",
                    actionLabel = "Export",
                    onAction = { confirmExport = true },
                )
                ExpandLine(
                    title = "Verbose log",
                    value = "every step, in plain words",
                    detail = "Everything the scanner did and why, written as it happens. It " +
                        "never leaves the phone unless you copy it out yourself.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            scope.launch { clipboard.setClipEntry(plainText(Verbose.dump())) }
                            note("verbose log copied to clipboard")
                        }) { Text("Copy") }
                        TextButton(onClick = {
                            Verbose.clear()
                            Verbose.info("log cleared by you")
                            Verbose.flush()
                        }) { Text("Clear") }
                    }
                }
                ValueLine(
                    title = "Where it lives",
                    value = "this phone only",
                    detail = "Everything lives in one JSON file inside this app's private " +
                        "storage, and backups are disabled. The app does reach the network, " +
                        "for one thing: it asks GitHub whether a newer release exists, and " +
                        "downloads that APK if you ask it to. Nothing goes the other way. No " +
                        "record, message, account or figure is ever put in a request, and " +
                        "there is no analytics and no backend.",
                )
            }

            SettingsCard("About") {
                SwitchLine(
                    title = "Explain screens",
                    checked = helpOnPage,
                    onCheckedChange = {
                        helpOnPage = it
                        vm.helpOnPage = it
                        note("on-page help ${if (it) "enabled" else "disabled"}")
                    },
                    detail = "Screens keep their explanation behind the (i) beside the " +
                        "title, so the page opens on the work rather than on a paragraph. " +
                        "Turn this on to have it written out on the page as well.",
                )
                // One row, both states. Normally it is the version you are on; when
                // GitHub is offering a later one it becomes the way to get it, with the
                // release notes behind the (i) like every other explanation here.
                val update by vm.update.collectAsState()
                val version = appVersion(context)
                ActionLine(
                    title = "Riyal",
                    value = update?.let { "${it.tag} available" } ?: version,
                    valueIsWarning = update != null,
                    detail = update?.let { release ->
                        val notes = release.notes.ifBlank { "No notes were published with it." }
                        "You have $version. ${release.tag} is out.\n\n$notes\n\n" +
                            "Downloading puts the APK in your Downloads folder and opens it " +
                            "there. Riyal cannot install it for you - you tap the file " +
                            "yourself. If Android refuses the install, it is because that " +
                            "build is signed with a different key than this one: uninstall " +
                            "Riyal first, which clears its stored records. They rebuild from " +
                            "your inbox on the next scan, but hand-filed categories do not."
                    } ?: "Made for Oman 🇴🇲 · OMR-first, with Arabic SMS support. " +
                        "Checks GitHub once a day for a newer release.",
                    actionLabel = if (update != null) "Download" else "Check now",
                    onAction = {
                        val release = update
                        if (release == null) {
                            note("checking GitHub for a newer release")
                            vm.checkForUpdate(version, force = true)
                        } else if (!release.hasApk) {
                            note("${release.tag} has no APK attached to it")
                        } else if (vm.downloadUpdate()) {
                            note("downloading ${release.tag} to your Downloads folder")
                        }
                    },
                )
            }

            // Kept away from the switches on purpose: this is the one control on the
            // screen that cannot be undone by tapping it again.
            DangerCard(onClick = { confirmWipe = true })

            ToolbarSpacer()
        }
    }

    if (pickCurrency) {
        PickerDialog(
            title = "Default currency",
            options = CURRENCIES,
            selected = currency,
            onPick = {
                currency = it
                prefs.defaultCurrency = it
                note("default currency → $it")
                pickCurrency = false
            },
            onDismiss = { pickCurrency = false },
        )
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

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("Export " + countOf(txns.size, "record") + "?") },
            text = {
                Text(
                    if (txns.isEmpty()) {
                        "There is nothing recorded yet, so the file would be empty. Scan your " +
                            "messages first."
                    } else {
                        "Every record Riyal holds goes into one CSV file, and you choose where " +
                            "it is written. Nothing leaves the phone on its own."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = txns.isNotEmpty(),
                    onClick = {
                        confirmExport = false
                        onExport()
                    },
                ) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExport = false }) { Text("Cancel") }
            },
        )
    }
}

// ─────────────────────────── the state you came to check ───────────────────────────

/**
 * What the app has actually done, before anything it can be told to do. Settings is
 * where you go to find out whether the thing is working, and a screen of switches
 * answers every question except that one.
 */
@Composable
private fun StatusCard(
    lastScanAt: Long,
    records: Int,
    accounts: Int,
    messagesRead: Int?,
) {
    Card(Modifier.fillMaxWidth().popIn()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (lastScanAt <= 0L) "No scan yet" else "Last scan ${relativeTime(lastScanAt)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    countOf(records, "record"),
                    countOf(accounts, "account"),
                    messagesRead?.let { countOf(it, "message") + " read" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "2 hours ago", or the date once that stops being a useful way to say it. */
private fun relativeTime(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> countOf(minutes.toInt(), "minute") + " ago"
        minutes < 60 * 24 -> countOf((minutes / 60).toInt(), "hour") + " ago"
        minutes < 60 * 24 * 7 -> countOf((minutes / (60 * 24)).toInt(), "day") + " ago"
        else -> "on " + settingsDayFmt.format(
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        )
    }
}

private fun rangeLabel(months: Int): String = when (months) {
    0 -> "everything"
    1 -> "1 month"
    12 -> "1 year"
    else -> "$months months"
}

private fun senderSummary(bankOnly: Boolean, allowlistOn: Boolean, approved: Int): String = when {
    allowlistOn && approved == 0 -> "nobody"
    allowlistOn -> "$approved approved"
    bankOnly && approved > 0 -> "banks + $approved"
    bankOnly -> "banks only"
    else -> "any sender"
}

// ─────────────────────────── one line per row ───────────────────────────

/**
 * The shape every row shares: a name, what it is currently set to, and the control
 * that changes it - on one line, so the screen can be read down the left edge for a
 * name and down the right edge for a value.
 *
 * [detail] is the paragraph that used to sit under the row at rest. It is one tap away
 * instead, because an explanation is read once and a setting is looked up many times.
 */
@Composable
private fun SettingLine(
    title: String,
    value: String? = null,
    detail: String? = null,
    valueIsWarning: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    var showDetail by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // One height for every row, whatever its control: a switch is taller than a
            // line of text, and the ragged rhythm that produced is most of what made
            // the screen read as a wall rather than a list.
            .heightIn(min = 52.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (detail != null) {
            Icon(
                Icons.Filled.Info,
                contentDescription = "About $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDetail = true },
            )
        }
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (valueIsWarning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }

    if (showDetail && detail != null) {
        AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text(title) },
            text = { Text(detail) },
            confirmButton = { TextButton(onClick = { showDetail = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun SwitchLine(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String,
    value: String? = null,
) {
    SettingLine(title = title, value = value, detail = detail) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A row whose value is chosen elsewhere - in a dialog, or nowhere at all. */
@Composable
private fun ValueLine(
    title: String,
    value: String,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    SettingLine(title = title, value = value, detail = detail, onClick = onClick) {
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A row that leads to another page. */
@Composable
private fun NavLine(
    title: String,
    value: String? = null,
    detail: String? = null,
    onClick: () -> Unit,
) {
    SettingLine(title = title, value = value, detail = detail, onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A row whose control does something once, rather than holding a value. */
@Composable
private fun ActionLine(
    title: String,
    value: String?,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    valueIsWarning: Boolean = false,
) {
    SettingLine(title = title, value = value, detail = detail, valueIsWarning = valueIsWarning) {
        TextButton(onClick = onAction, modifier = Modifier.pressBounce()) { Text(actionLabel) }
    }
}

/**
 * A row that opens into its own controls, for the settings that genuinely need more
 * than a switch - keywords, senders, the range. The screen stays a list of names and
 * values until one of them is actually being changed.
 */
@Composable
private fun ExpandLine(
    title: String,
    value: String,
    detail: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        SettingLine(
            title = title,
            value = value,
            detail = detail,
            onClick = { open = !open },
        ) {
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/**
 * A line that has to be on screen because it describes the state the app is in, not
 * what a control would do - the one kind of explanation worth the vertical space.
 */
@Composable
private fun StateNote(text: String, action: String, onAction: () -> Unit) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

/**
 * The one irreversible control, in its own container at the end of the screen. Sitting
 * in a card of switches, it read as one more of them.
 */
@Composable
private fun DangerCard(onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().popIn(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Delete all app data", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Records, rules and settings. Your inbox is untouched.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** One value out of a short list, without a dropdown anchored to a row. */
@Composable
private fun PickerDialog(
    title: String,
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(option) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (option == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().popIn()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
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

private fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
} catch (e: Exception) {
    "1.0"
}
