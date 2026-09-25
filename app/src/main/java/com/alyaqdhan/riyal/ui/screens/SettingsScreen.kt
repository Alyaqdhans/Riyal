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
import androidx.annotation.StringRes
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.ui.compose.ScanSheetHost
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Language
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CURRENCIES
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.ToolbarSpacer
import com.alyaqdhan.riyal.ui.compose.appVersion
import com.alyaqdhan.riyal.ui.compose.plainText
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.pressBounce
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val settingsDayFmt: DateTimeFormatter get() = Dates.pattern("d MMM uuuu")

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
    var autoConfirmTransfers by remember { mutableStateOf(prefs.autoConfirmTransfers) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf(false) }
    val language = Language.current()

    fun note(text: String) {
        Verbose.info(Verbose.str(R.string.log_setting_changed, text))
        Verbose.flush()
    }

    fun note(@StringRes id: Int, vararg args: Any?) = note(Verbose.str(id, *args))

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
    ) { padding ->
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

            SettingsCard(stringResource(R.string.set_card_money)) {
                ValueLine(
                    title = stringResource(R.string.set_default_currency),
                    value = currency,
                    detail = stringResource(R.string.set_help_currency),
                    onClick = { pickCurrency = true },
                )
                NavLine(
                    title = stringResource(R.string.acct_title),
                    value = if (accounts.isEmpty()) stringResource(R.string.an_none_yet)
                    else "${accounts.size}",
                    detail = stringResource(R.string.set_help_accounts),
                    onClick = onOpenAccounts,
                )
                NavLine(
                    title = stringResource(R.string.cats_title),
                    value = if (rules.isEmpty()) null
                    else counted(R.plurals.count_learned, rules.size),
                    detail = stringResource(R.string.set_help_categories),
                    onClick = onOpenCategories,
                )
                SwitchLine(
                    title = stringResource(R.string.budget_title),
                    value = if (budgetsEnabled && budgets.isNotEmpty()) {
                        counted(R.plurals.count_plans, budgets.size)
                    } else {
                        null
                    },
                    checked = budgetsEnabled,
                    onCheckedChange = {
                        budgetsEnabled = it
                        vm.budgetsEnabled = it
                        note(if (it) R.string.log_note_budget_on else R.string.log_note_budget_off)
                    },
                    detail = stringResource(R.string.set_help_budget),
                )
            }

            SettingsCard(stringResource(R.string.set_card_scanning)) {
                ActionLine(
                    title = stringResource(R.string.set_sms_permission),
                    value = stringResource(if (hasPerm) R.string.set_allowed else R.string.set_off),
                    valueIsWarning = !hasPerm,
                    detail = stringResource(R.string.set_help_permission),
                    actionLabel = stringResource(R.string.set_manage),
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
                    title = stringResource(R.string.set_scan_on_open),
                    checked = scanOnLaunch,
                    onCheckedChange = {
                        scanOnLaunch = it
                        prefs.scanOnLaunch = it
                        note(if (it) R.string.log_note_scan_open_on else R.string.log_note_scan_open_off)
                    },
                    detail = stringResource(R.string.set_help_scan_on_open),
                )

                ExpandLine(
                    title = stringResource(R.string.set_how_far_back),
                    value = rangeLabel(rangeMonths),
                    detail = stringResource(R.string.set_help_how_far_back),
                ) {
                    val ranges = listOf(
                        1 to stringResource(R.string.set_range_1mo),
                        3 to stringResource(R.string.set_range_3mo),
                        6 to stringResource(R.string.set_range_6mo),
                        12 to stringResource(R.string.set_range_1yr),
                        0 to stringResource(R.string.set_range_all),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ranges.forEachIndexed { index, (months, label) ->
                            SegmentedButton(
                                selected = rangeMonths == months,
                                onClick = {
                                    rangeMonths = months
                                    prefs.scanRangeMonths = months
                                    note(R.string.log_note_scan_range, label)
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
                        stringResource(
                            R.string.set_fresh_start,
                            settingsDayFmt.format(
                                Instant.ofEpochMilli(freshStart).atZone(ZoneId.systemDefault())
                            ),
                        ),
                        action = stringResource(R.string.set_read_older),
                        onAction = {
                            prefs.scanSinceMillis = 0L
                            freshStart = 0L
                            note(R.string.log_note_fresh_start_lifted)
                        },
                    )
                }

                ExpandLine(
                    title = stringResource(R.string.set_gate_keywords),
                    value = stringResource(
                        R.string.set_keyword_counts,
                        expenseKw.size,
                        incomeKw.size,
                    ),
                    detail = stringResource(R.string.set_help_keywords),
                ) {
                    Text(
                        stringResource(R.string.label_money_out),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    KeywordChips(expenseKw) { kw ->
                        expenseKw = expenseKw - kw
                        prefs.expenseKeywords = expenseKw
                        note(R.string.log_note_kw_removed_out, kw)
                    }
                    AddKeywordRow(
                        value = newExpenseKw,
                        onValueChange = { newExpenseKw = it },
                        onAdd = {
                            val kw = newExpenseKw.trim().lowercase()
                            if (kw.isNotEmpty()) {
                                expenseKw = expenseKw + kw
                                prefs.expenseKeywords = expenseKw
                                note(R.string.log_note_kw_added_out, kw)
                            }
                            newExpenseKw = ""
                        },
                    )
                    Text(
                        stringResource(R.string.label_money_in),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    KeywordChips(incomeKw) { kw ->
                        incomeKw = incomeKw - kw
                        prefs.incomeKeywords = incomeKw
                        note(R.string.log_note_kw_removed_in, kw)
                    }
                    AddKeywordRow(
                        value = newIncomeKw,
                        onValueChange = { newIncomeKw = it },
                        onAdd = {
                            val kw = newIncomeKw.trim().lowercase()
                            if (kw.isNotEmpty()) {
                                incomeKw = incomeKw + kw
                                prefs.incomeKeywords = incomeKw
                                note(R.string.log_note_kw_added_in, kw)
                            }
                            newIncomeKw = ""
                        },
                    )
                    TextButton(onClick = {
                        prefs.resetKeywords()
                        expenseKw = prefs.expenseKeywords
                        incomeKw = prefs.incomeKeywords
                        note(R.string.log_note_kw_reset)
                    }) { Text(stringResource(R.string.set_reset_defaults)) }
                }

                ExpandLine(
                    title = stringResource(R.string.set_who_is_read),
                    value = senderSummary(bankOnly, senderFilter, allowlist.size),
                    detail = stringResource(R.string.set_help_who_is_read),
                ) {
                    SwitchLine(
                        title = stringResource(R.string.set_bank_only),
                        checked = bankOnly,
                        onCheckedChange = {
                            bankOnly = it
                            prefs.bankSendersOnly = it
                            note(if (it) R.string.log_note_bank_only_on else R.string.log_note_bank_only_off)
                        },
                        detail = stringResource(R.string.set_help_bank_only),
                    )
                    SwitchLine(
                        title = stringResource(R.string.set_only_approved),
                        checked = senderFilter,
                        onCheckedChange = {
                            senderFilter = it
                            prefs.senderFilterEnabled = it
                            note(if (it) R.string.log_note_allowlist_on else R.string.log_note_allowlist_off)
                        },
                        detail = stringResource(R.string.set_help_only_approved),
                    )
                    if (senderFilter || bankOnly) {
                        if (allowlist.isEmpty()) {
                            Text(
                                stringResource(
                                    if (senderFilter) R.string.set_no_approved
                                    else R.string.set_no_extra_approved
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (senderFilter) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            KeywordChips(allowlist) { sender ->
                                allowlist = allowlist - sender
                                prefs.senderAllowlist = allowlist
                                note(R.string.log_note_sender_removed, sender)
                            }
                        }
                        // Add a sender by name directly, for a bank whose ID hasn't shown up
                        // in the inbox yet (or a contact number you know is a bank).
                        AddKeywordRow(
                            value = newSender,
                            onValueChange = { newSender = it },
                            label = stringResource(R.string.set_sender_name),
                            onAdd = {
                                val s = newSender.trim()
                                if (s.isNotEmpty() && s !in allowlist) {
                                    allowlist = allowlist + s
                                    prefs.senderAllowlist = allowlist
                                    note(R.string.log_note_sender_added_by_name, s)
                                }
                                newSender = ""
                            },
                        )
                        val suggestions = remember(knownSenders, allowlist) {
                            (knownSenders - allowlist).sorted().take(12)
                        }
                        if (suggestions.isNotEmpty()) {
                            Text(
                                stringResource(R.string.set_seen_in_inbox),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                suggestions.forEach { sender ->
                                    AssistChip(
                                        onClick = {
                                            allowlist = allowlist + sender
                                            prefs.senderAllowlist = allowlist
                                            note(R.string.log_note_sender_approved, sender)
                                        },
                                        label = { Text(sender) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Progress belongs on the screen that has the button. This row used to
                // start a scan whose sheet only Home and Activity rendered, so the work
                // you asked for here appeared over a screen you were not looking at.
                val scan by vm.scanState.collectAsState()
                val running = scan as? MainViewModel.ScanState.Running
                ActionLine(
                    title = stringResource(R.string.set_scan_now),
                    value = running?.let { p ->
                        if (p.total > 0) {
                            stringResource(R.string.set_scan_progress, p.processed, p.total)
                        } else {
                            stringResource(R.string.set_scan_starting)
                        }
                    },
                    detail = stringResource(R.string.set_help_scan_now),
                    actionLabel = stringResource(
                        if (running != null) R.string.set_scanning else R.string.set_scan
                    ),
                    enabled = running == null,
                    onAction = { vm.startScan() },
                )
                ActionLine(
                    title = stringResource(R.string.set_rescan_all),
                    value = null,
                    detail = stringResource(R.string.set_help_rescan),
                    actionLabel = stringResource(R.string.set_rescan),
                    enabled = running == null,
                    onAction = { vm.startScan(full = true) },
                )
                if (running != null) {
                    LinearProgressIndicator(
                        progress = {
                            if (running.total > 0) running.processed.toFloat() / running.total else 0f
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }

            SettingsCard(stringResource(R.string.set_card_automation)) {
                SwitchLine(
                    title = stringResource(R.string.set_learn_corrections),
                    checked = smartRules,
                    onCheckedChange = {
                        smartRules = it
                        prefs.smartRules = it
                        note(if (it) R.string.log_note_smart_on else R.string.log_note_smart_off)
                    },
                    detail = stringResource(R.string.set_help_learn),
                )
                SwitchLine(
                    title = stringResource(R.string.set_confirm_transfers),
                    checked = autoConfirmTransfers,
                    onCheckedChange = {
                        autoConfirmTransfers = it
                        vm.autoConfirmTransfers = it
                        note(if (it) R.string.log_note_autoconfirm_on else R.string.log_note_autoconfirm_off)
                    },
                    detail = stringResource(R.string.set_help_confirm_transfers),
                )
            }

            SettingsCard(stringResource(R.string.set_card_data)) {
                ActionLine(
                    title = stringResource(R.string.set_export_txns),
                    // The count belongs to the moment you ask for the file, not to a row
                    // you are only reading past. It is said in the confirmation instead.
                    value = null,
                    detail = stringResource(R.string.set_help_export),
                    actionLabel = stringResource(R.string.action_export),
                    onAction = { confirmExport = true },
                )
                ExpandLine(
                    title = stringResource(R.string.set_verbose_log),
                    value = stringResource(R.string.set_verbose_value),
                    detail = stringResource(R.string.set_help_verbose),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            scope.launch { clipboard.setClipEntry(plainText(Verbose.dump())) }
                            note(R.string.log_note_log_copied)
                        }) { Text(stringResource(R.string.action_copy)) }
                        TextButton(onClick = {
                            Verbose.clear()
                            Verbose.info(R.string.log_note_log_cleared)
                            Verbose.flush()
                        }) { Text(stringResource(R.string.action_clear)) }
                    }
                }
                ValueLine(
                    title = stringResource(R.string.set_where_it_lives),
                    value = stringResource(R.string.set_this_phone_only),
                    detail = stringResource(R.string.set_help_where_it_lives),
                )
            }

            SettingsCard(stringResource(R.string.settings_card_app)) {
                ValueLine(
                    title = stringResource(R.string.settings_language),
                    value = when (language) {
                        Language.Choice.ARABIC -> stringResource(R.string.settings_language_arabic)
                        Language.Choice.ENGLISH -> stringResource(R.string.settings_language_english)
                        Language.Choice.SYSTEM -> stringResource(R.string.settings_language_system)
                    },
                    detail = stringResource(R.string.settings_language_help),
                    onClick = { pickLanguage = true },
                )
            }

            SettingsCard(stringResource(R.string.set_card_about)) {
                // One row, every state. Normally it is the version you are on; when
                // GitHub is offering a later one it becomes the way to get it. The
                // release notes sit behind the (i) whichever way the check came out,
                // because "what changed" is worth reading on the version you already
                // have and not only on one you are about to install.
                val updateState by vm.updateState.collectAsState()
                val version = appVersion(context)
                val known = when (val u = updateState) {
                    is MainViewModel.UpdateState.Available -> u.release
                    is MainViewModel.UpdateState.UpToDate -> u.release
                    else -> null
                }
                val offered = (updateState as? MainViewModel.UpdateState.Available)?.release
                val checking = updateState is MainViewModel.UpdateState.Checking
                val notes = known?.let { plainNotes(it.notes) }?.takeIf { it.isNotBlank() }
                // Only a check the user tapped gets an answer said out loud. The daily
                // one in the background must stay silent.
                var asked by remember { mutableStateOf(false) }
                ActionLine(
                    title = stringResource(R.string.app_name),
                    value = when {
                        checking -> stringResource(R.string.set_checking)
                        offered != null ->
                            stringResource(R.string.set_update_available, offered.tag)
                        updateState is MainViewModel.UpdateState.UpToDate ->
                            stringResource(R.string.set_latest, version)
                        updateState is MainViewModel.UpdateState.Unreachable ->
                            stringResource(R.string.set_could_not_check, version)
                        else -> version
                    },
                    valueIsWarning = offered != null,
                    detail = when {
                        offered != null -> stringResource(
                            R.string.set_about_update_header, version, offered.tag
                        ) + stringResource(R.string.set_about_download)
                        updateState is MainViewModel.UpdateState.UpToDate ->
                            stringResource(R.string.set_about_uptodate, version)
                        updateState is MainViewModel.UpdateState.Unreachable ->
                            stringResource(R.string.set_about_unreachable)
                        else -> stringResource(R.string.set_about_default)
                    } + if (notes != null) {
                        stringResource(R.string.set_about_whats_in, known!!.tag) + notes
                    } else {
                        ""
                    },
                    actionLabel = when {
                        checking -> stringResource(R.string.set_checking_action)
                        offered != null -> stringResource(R.string.set_download)
                        else -> stringResource(R.string.set_check_now)
                    },
                    enabled = !checking,
                    onAction = {
                        if (offered == null) {
                            asked = true
                            note(R.string.log_note_checking)
                            vm.checkForUpdate(version, force = true)
                        } else if (!offered.hasApk) {
                            note(R.string.log_note_no_apk, offered.tag)
                        } else if (vm.downloadUpdate()) {
                            note(R.string.log_note_downloading, offered.tag)
                        }
                    },
                )
                // A check the user asked for has someone waiting on it, so it says how
                // it came out instead of leaving the row to be re-read.
                LaunchedEffect(updateState) {
                    when (val u = updateState) {
                        is MainViewModel.UpdateState.UpToDate ->
                            if (asked) { asked = false; note(R.string.log_note_latest, version) }
                        is MainViewModel.UpdateState.Available ->
                            if (asked) { asked = false; note(R.string.log_note_available, u.release.tag) }
                        is MainViewModel.UpdateState.Unreachable ->
                            if (asked) { asked = false; note(R.string.log_note_unreachable) }
                        else -> Unit
                    }
                }
            }

            // Kept away from the switches on purpose: this is the one control on the
            // screen that cannot be undone by tapping it again.
            DangerCard(onClick = { confirmWipe = true })

            ToolbarSpacer()
        }
    }

    // The scan sheet is hosted here, on the screen whose button starts a scan.
    ScanSheetHost(vm)

    if (pickCurrency) {
        PickerDialog(
            title = stringResource(R.string.set_default_currency),
            options = CURRENCIES,
            selected = currency,
            onPick = {
                currency = it
                prefs.defaultCurrency = it
                note(R.string.log_note_currency, it)
                pickCurrency = false
            },
            onDismiss = { pickCurrency = false },
        )
    }

    if (pickLanguage) {
        val labels = mapOf(
            Language.Choice.SYSTEM to stringResource(R.string.settings_language_system),
            Language.Choice.ENGLISH to stringResource(R.string.settings_language_english),
            Language.Choice.ARABIC to stringResource(R.string.settings_language_arabic),
        )
        PickerDialog(
            title = stringResource(R.string.settings_language),
            options = labels.values.toList(),
            selected = labels.getValue(language),
            onPick = { picked ->
                pickLanguage = false
                val choice = labels.entries.first { it.value == picked }.key
                if (choice != language) {
                    note(R.string.log_note_language, picked)
                    Language.set(choice)
                }
            },
            onDismiss = { pickLanguage = false },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(stringResource(R.string.set_wipe_title)) },
            text = { Text(stringResource(R.string.set_wipe_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.wipeAll()
                    confirmWipe = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = {
                Text(
                    stringResource(
                        R.string.set_export_title,
                        counted(R.plurals.count_records, txns.size),
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (txns.isEmpty()) R.string.set_export_empty
                        else R.string.set_export_body
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = txns.isNotEmpty(),
                    onClick = {
                        confirmExport = false
                        onExport()
                    },
                ) { Text(stringResource(R.string.action_export)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExport = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
                if (lastScanAt <= 0L) {
                    stringResource(R.string.set_no_scan_yet)
                } else {
                    stringResource(R.string.set_last_scan, relativeTime(lastScanAt))
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    counted(R.plurals.count_records, records),
                    counted(R.plurals.count_accounts, accounts),
                    messagesRead?.let {
                        stringResource(
                            R.string.set_messages_read,
                            counted(R.plurals.count_messages, it),
                        )
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "2 hours ago", or the date once that stops being a useful way to say it. */
@Composable
private fun relativeTime(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.set_just_now)
        minutes < 60 -> stringResource(
            R.string.set_ago,
            counted(R.plurals.count_minutes, minutes.toInt()),
        )
        minutes < 60 * 24 -> stringResource(
            R.string.set_ago,
            counted(R.plurals.count_hours, (minutes / 60).toInt()),
        )
        minutes < 60 * 24 * 7 -> stringResource(
            R.string.set_ago,
            counted(R.plurals.count_days, (minutes / (60 * 24)).toInt()),
        )
        else -> stringResource(
            R.string.set_on_date,
            settingsDayFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())),
        )
    }
}

@Composable
private fun rangeLabel(months: Int): String = when (months) {
    0 -> stringResource(R.string.set_range_everything)
    1 -> stringResource(R.string.set_range_one_month)
    12 -> stringResource(R.string.set_range_one_year)
    else -> stringResource(R.string.set_range_months, months)
}

@Composable
private fun senderSummary(bankOnly: Boolean, allowlistOn: Boolean, approved: Int): String = when {
    allowlistOn && approved == 0 -> stringResource(R.string.set_senders_nobody)
    allowlistOn -> counted(R.plurals.count_approved, approved)
    bankOnly && approved > 0 -> stringResource(R.string.set_senders_banks_plus, approved)
    bankOnly -> stringResource(R.string.set_senders_banks_only)
    else -> stringResource(R.string.set_senders_any)
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
                contentDescription = stringResource(R.string.help_about, title),
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
            // Scrollable, because one of these is now a release body written by whoever
            // wrote it. Without this the dialog clips at the bottom of the screen with
            // no way to reach the rest - and the part it was cutting off was the
            // warning about the signing key, which is the one paragraph that has to be
            // read before tapping Download.
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) { Text(detail) }
            },
            confirmButton = {
                TextButton(onClick = { showDetail = false }) {
                    Text(stringResource(R.string.action_got_it))
                }
            },
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
    enabled: Boolean = true,
) {
    SettingLine(title = title, value = value, detail = detail, valueIsWarning = valueIsWarning) {
        TextButton(
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier.pressBounce(),
        ) { Text(actionLabel) }
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
                contentDescription = if (open) {
                    stringResource(R.string.set_collapse, title)
                } else {
                    stringResource(R.string.set_expand, title)
                },
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
                Text(
                    stringResource(R.string.set_danger_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.set_danger_body),
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
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
                        contentDescription = stringResource(R.string.set_remove_item, item),
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
    label: String? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label ?: stringResource(R.string.set_add_keyword)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onAdd, enabled = value.isNotBlank()) {
            Text(stringResource(R.string.action_add))
        }
    }
}

/**
 * A GitHub release body as something to read in a dialog.
 *
 * The body is markdown, and this dialog is a Text: "**Upgrading clears your data.**"
 * arrives with its asterisks showing and "### Accounts" with its hashes. Nothing here
 * renders markdown, so the markers are taken off rather than displayed. Emphasis is
 * lost, which is a smaller cost than a screen of punctuation.
 */
private fun plainNotes(body: String): String = body.trim().lines().joinToString("\n") { line ->
    line.trimEnd()
        .replace(headingMark, "")
        .replace(boldMark, "")
        .replace(bulletMark, "• ")
}

private val headingMark = Regex("^#{1,6}\\s*")
private val bulletMark = Regex("^\\s*[-*]\\s+")
private val boldMark = Regex("\\*\\*|__")
