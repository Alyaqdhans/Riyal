@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Currencies
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Language
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.CurrencyPickerSheet
import com.alyaqdhan.riyal.ui.compose.ExchangeRatesSheet
import com.alyaqdhan.riyal.ui.compose.ScanBar
import com.alyaqdhan.riyal.ui.compose.ScanSheetHost
import com.alyaqdhan.riyal.ui.compose.appVersion
import com.alyaqdhan.riyal.ui.compose.counted
import com.alyaqdhan.riyal.ui.compose.plainText
import com.alyaqdhan.riyal.ui.compose.relativeTime
import com.alyaqdhan.riyal.ui.compose.toolbarSpace
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Settings as a phone's own settings app lays them out: a short status at the top, then
 * groups of rows, each row an icon, a name, what it is set to, and - where it holds a
 * value - its switch. Every row is Material's own list item, so a row looks and responds
 * like a row anywhere else on the phone.
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = vm.prefs
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val hasPerm by vm.hasSmsPermission.collectAsState()
    val rules by vm.rules.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val txns by vm.txns.collectAsState()
    val lastSummary by vm.lastSummary.collectAsState()
    val currency by vm.defaultCurrency.collectAsState()
    val budgetsOn by vm.budgetsOn.collectAsState()
    val autoConfirm by vm.autoConfirmOn.collectAsState()
    val updateState by vm.updateState.collectAsState()
    val scan by vm.scanState.collectAsState()
    val running = scan as? MainViewModel.ScanState.Running

    var rangeMonths by remember { mutableStateOf(prefs.scanRangeMonths) }
    var freshStart by remember { mutableStateOf(prefs.scanSinceMillis) }
    var scanOnLaunch by remember { mutableStateOf(prefs.scanOnLaunch) }
    var smartRules by remember { mutableStateOf(prefs.smartRules) }
    var backupMessages by remember { mutableStateOf(prefs.backupIncludeMessages) }
    var dialog by remember { mutableStateOf(SettingsDialog.NONE) }
    val language = Language.current()

    fun note(@StringRes id: Int, vararg args: Any?) {
        Verbose.info(Verbose.str(R.string.log_setting_changed, Verbose.str(id, *args)))
        Verbose.flush()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = toolbarSpace + 16.dp),
        ) {
            StatusCard(
                lastScanAt = prefs.lastScanAt,
                records = txns.size,
                accounts = accounts.size,
                messagesRead = lastSummary?.scanned,
            )

            SectionLabel(R.string.set_section_general)
            NavRow(
                R.drawable.ic_sym_language,
                stringResource(R.string.settings_language),
                when (language) {
                    Language.Choice.ARABIC -> stringResource(R.string.settings_language_arabic)
                    Language.Choice.ENGLISH -> stringResource(R.string.settings_language_english)
                    Language.Choice.SYSTEM -> stringResource(R.string.settings_language_system)
                },
            ) { dialog = SettingsDialog.LANGUAGE }
            NavRow(
                R.drawable.ic_sym_payments,
                stringResource(R.string.set_default_currency),
                Currencies.of(currency)?.let { it.flag + "  " + stringResource(it.name) } ?: currency,
            ) { dialog = SettingsDialog.CURRENCY }
            NavRow(
                R.drawable.ic_sym_currency_exchange,
                stringResource(R.string.cur_rates_title),
                stringResource(R.string.cur_rates_short),
            ) { dialog = SettingsDialog.RATES }

            SectionLabel(R.string.set_card_scanning)
            NavRow(
                R.drawable.ic_sym_lock,
                stringResource(R.string.set_sms_permission),
                stringResource(if (hasPerm) R.string.set_allowed else R.string.set_off),
                warning = !hasPerm,
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }
            NavRow(
                R.drawable.ic_sym_school,
                stringResource(R.string.set_teach),
                stringResource(R.string.set_teach_short),
                onClick = onOpenSetup,
            )
            SwitchRow(
                R.drawable.ic_sym_sync,
                stringResource(R.string.set_scan_on_open),
                stringResource(R.string.set_scan_on_open_detail),
                checked = scanOnLaunch,
            ) {
                scanOnLaunch = it
                prefs.scanOnLaunch = it
                note(if (it) R.string.log_note_scan_open_on else R.string.log_note_scan_open_off)
            }
            NavRow(
                R.drawable.ic_sym_calendar,
                stringResource(R.string.set_how_far_back),
                rangeLabel(rangeMonths),
            ) { dialog = SettingsDialog.RANGE }
            // The fresh-start floor overrides the range above, so it stays on screen: it
            // is why "everything" can still show nothing older than the day it was set.
            if (freshStart > 0L) {
                ListItem(
                    trailingContent = {
                        TextButton(onClick = {
                            prefs.scanSinceMillis = 0L
                            freshStart = 0L
                            note(R.string.log_note_fresh_start_lifted)
                        }) { Text(stringResource(R.string.set_read_older)) }
                    },
                ) {
                    Text(
                        stringResource(
                            R.string.set_fresh_start,
                            Dates.pattern("d MMM uuuu").format(Instant.ofEpochMilli(freshStart).atZone(ZoneId.systemDefault())),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            NavRow(
                R.drawable.ic_sym_mail_read,
                stringResource(R.string.set_scan_now),
                running?.let { p ->
                    if (p.total > 0) stringResource(R.string.set_scan_progress, p.processed, p.total)
                    else stringResource(R.string.set_scan_starting)
                } ?: stringResource(R.string.set_scan_now_detail),
                enabled = running == null,
                chevron = false,
            ) { vm.startScan() }
            if (running != null) {
                ScanBar(
                    progress = if (running.total > 0) ({ running.processed.toFloat() / running.total }) else null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            NavRow(
                R.drawable.ic_sym_history,
                stringResource(R.string.set_rescan_all),
                stringResource(R.string.set_rescan_detail),
                enabled = running == null,
                chevron = false,
            ) { vm.startScan(full = true) }

            SectionLabel(R.string.set_card_money)
            NavRow(
                R.drawable.ic_sym_account_balance,
                stringResource(R.string.acct_title),
                if (accounts.isEmpty()) stringResource(R.string.an_none_yet)
                else counted(R.plurals.count_accounts, accounts.size),
                onClick = onOpenAccounts,
            )
            NavRow(
                R.drawable.ic_sym_category,
                stringResource(R.string.cats_title),
                if (rules.isEmpty()) stringResource(R.string.set_categories_detail)
                else counted(R.plurals.count_learned, rules.size),
                onClick = onOpenCategories,
            )
            SwitchRow(
                R.drawable.ic_sym_savings,
                stringResource(R.string.budget_title),
                if (budgetsOn && budgets.isNotEmpty()) counted(R.plurals.count_plans, budgets.size)
                else stringResource(R.string.set_budget_detail),
                checked = budgetsOn,
            ) {
                vm.budgetsEnabled = it
                note(if (it) R.string.log_note_budget_on else R.string.log_note_budget_off)
            }
            SwitchRow(
                R.drawable.ic_sym_swap,
                stringResource(R.string.set_confirm_transfers),
                stringResource(R.string.set_confirm_transfers_detail),
                checked = autoConfirm,
            ) {
                vm.autoConfirmTransfers = it
                note(if (it) R.string.log_note_autoconfirm_on else R.string.log_note_autoconfirm_off)
            }
            SwitchRow(
                R.drawable.ic_sym_auto_awesome,
                stringResource(R.string.set_learn_corrections),
                stringResource(R.string.set_learn_detail),
                checked = smartRules,
            ) {
                smartRules = it
                prefs.smartRules = it
                note(if (it) R.string.log_note_smart_on else R.string.log_note_smart_off)
            }

            SectionLabel(R.string.set_card_data)
            NavRow(
                R.drawable.ic_sym_share,
                stringResource(R.string.set_export_txns),
                stringResource(R.string.set_export_detail),
                chevron = false,
            ) { dialog = SettingsDialog.EXPORT }
            NavRow(
                R.drawable.ic_sym_backup,
                stringResource(R.string.set_backup),
                stringResource(R.string.set_backup_detail),
                chevron = false,
                onClick = onBackup,
            )
            SwitchRow(
                R.drawable.ic_sym_sms,
                stringResource(R.string.set_backup_messages),
                stringResource(R.string.set_backup_messages_detail),
                checked = backupMessages,
            ) {
                backupMessages = it
                prefs.backupIncludeMessages = it
            }
            NavRow(
                R.drawable.ic_sym_restore,
                stringResource(R.string.set_restore),
                stringResource(R.string.set_restore_detail),
                chevron = false,
            ) { dialog = SettingsDialog.RESTORE }
            NavRow(
                R.drawable.ic_sym_log,
                stringResource(R.string.set_verbose_log),
                stringResource(R.string.set_log_detail),
            ) { dialog = SettingsDialog.LOG }
            ListItem(
                leadingContent = { Icon(painterResource(R.drawable.ic_sym_info), contentDescription = null) },
                supportingContent = { Text(stringResource(R.string.set_this_phone_only)) },
            ) { Text(stringResource(R.string.set_where_it_lives)) }

            SectionLabel(R.string.set_card_about)
            NavRow(
                R.drawable.ic_sym_system_update,
                stringResource(R.string.upd_title),
                when (val u = updateState) {
                    MainViewModel.UpdateState.Checking -> stringResource(R.string.set_checking)
                    is MainViewModel.UpdateState.Available -> stringResource(R.string.set_update_available, u.release.tag)
                    else -> stringResource(R.string.upd_app_version, appVersion(context))
                },
                warning = updateState is MainViewModel.UpdateState.Available,
                onClick = onOpenUpdates,
            )

            // Kept away from everything else: the one control here that cannot be undone.
            SectionLabel(R.string.set_danger_title)
            ListItem(
                onClick = { dialog = SettingsDialog.WIPE },
                leadingContent = {
                    Icon(painterResource(R.drawable.ic_sym_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                supportingContent = { Text(stringResource(R.string.set_danger_body)) },
            ) { Text(stringResource(R.string.set_wipe_title), color = MaterialTheme.colorScheme.error) }
        }
    }

    // The scan sheet is hosted here, on the screen whose rows start a scan.
    ScanSheetHost(vm)

    when (dialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.LANGUAGE -> {
            val options = listOf(
                Language.Choice.SYSTEM to stringResource(R.string.settings_language_system),
                Language.Choice.ENGLISH to stringResource(R.string.settings_language_english),
                Language.Choice.ARABIC to stringResource(R.string.settings_language_arabic),
            )
            ChoiceDialog(
                title = stringResource(R.string.settings_language),
                options = options,
                selected = language,
                onPick = { choice ->
                    dialog = SettingsDialog.NONE
                    if (choice != language) {
                        note(R.string.log_note_language, options.first { it.first == choice }.second)
                        Language.set(choice)
                    }
                },
                onDismiss = { dialog = SettingsDialog.NONE },
            )
        }
        SettingsDialog.RANGE -> {
            val options = listOf(
                1 to stringResource(R.string.set_range_one_month),
                3 to stringResource(R.string.set_range_months, 3),
                6 to stringResource(R.string.set_range_months, 6),
                12 to stringResource(R.string.set_range_one_year),
                0 to stringResource(R.string.set_range_everything),
            )
            ChoiceDialog(
                title = stringResource(R.string.set_how_far_back),
                options = options,
                selected = rangeMonths,
                onPick = { months ->
                    dialog = SettingsDialog.NONE
                    rangeMonths = months
                    prefs.scanRangeMonths = months
                    note(R.string.log_note_scan_range, options.first { it.first == months }.second)
                },
                onDismiss = { dialog = SettingsDialog.NONE },
            )
        }
        SettingsDialog.CURRENCY -> CurrencyPickerSheet(
            selected = currency,
            onPick = {
                vm.setDefaultCurrency(it)
                dialog = SettingsDialog.NONE
            },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.RATES -> {
            val overrides by vm.fxOverrides.collectAsState()
            ExchangeRatesSheet(
                base = currency,
                overrides = overrides,
                onSetRate = { code, rate -> vm.setRate(code, rate) },
                onDismiss = { dialog = SettingsDialog.NONE },
            )
        }
        SettingsDialog.LOG -> AlertDialog(
            onDismissRequest = { dialog = SettingsDialog.NONE },
            title = { Text(stringResource(R.string.set_verbose_log)) },
            text = { Text(stringResource(R.string.set_help_verbose)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { clipboard.setClipEntry(plainText(Verbose.dump())) }
                    note(R.string.log_note_log_copied)
                    dialog = SettingsDialog.NONE
                }) { Text(stringResource(R.string.action_copy)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Verbose.clear()
                    Verbose.info(R.string.log_note_log_cleared)
                    Verbose.flush()
                    dialog = SettingsDialog.NONE
                }) { Text(stringResource(R.string.action_clear)) }
            },
        )
        SettingsDialog.WIPE -> AlertDialog(
            onDismissRequest = { dialog = SettingsDialog.NONE },
            title = { Text(stringResource(R.string.set_wipe_title)) },
            text = { Text(stringResource(R.string.set_wipe_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.wipeAll()
                        dialog = SettingsDialog.NONE
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = SettingsDialog.NONE }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        SettingsDialog.RESTORE -> AlertDialog(
            onDismissRequest = { dialog = SettingsDialog.NONE },
            title = { Text(stringResource(R.string.set_restore_title)) },
            text = { Text(stringResource(R.string.set_restore_body)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = SettingsDialog.NONE
                    onRestore()
                }) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = SettingsDialog.NONE }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        SettingsDialog.EXPORT -> AlertDialog(
            onDismissRequest = { dialog = SettingsDialog.NONE },
            title = { Text(stringResource(R.string.set_export_title, counted(R.plurals.count_records, txns.size))) },
            text = {
                Text(stringResource(if (txns.isEmpty()) R.string.set_export_empty else R.string.set_export_body))
            },
            confirmButton = {
                TextButton(
                    enabled = txns.isNotEmpty(),
                    onClick = {
                        dialog = SettingsDialog.NONE
                        onExport()
                    },
                ) { Text(stringResource(R.string.action_export)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = SettingsDialog.NONE }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The one dialog or sheet Settings can have open at a time. */
private enum class SettingsDialog { NONE, LANGUAGE, RANGE, CURRENCY, RATES, LOG, WIPE, RESTORE, EXPORT }

/** A group's name, in the primary colour, the way the phone's own settings title one. */
@Composable
private fun SectionLabel(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** A row that opens something or does something once. */
@Composable
private fun NavRow(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    warning: Boolean = false,
    enabled: Boolean = true,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        enabled = enabled,
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        supportingContent = {
            Text(
                value,
                color = if (warning) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        trailingContent = if (chevron) {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
        } else {
            null
        },
    ) { Text(title) }
}

/** A row that holds a value: the whole row toggles it, the switch shows it. */
@Composable
private fun SwitchRow(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        supportingContent = { Text(detail) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) { Text(title) }
}

/** One of a few choices, as radio buttons: the standard way to pick exactly one. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton) { onPick(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * What the app has actually done, before anything it can be told to do: Settings is
 * where people go to find out whether the thing is working.
 */
@Composable
private fun StatusCard(lastScanAt: Long, records: Int, accounts: Int, messagesRead: Int?) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (lastScanAt <= 0L) stringResource(R.string.set_no_scan_yet)
                else stringResource(R.string.set_last_scan, relativeTime(lastScanAt)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    counted(R.plurals.count_records, records),
                    counted(R.plurals.count_accounts, accounts),
                    messagesRead?.let {
                        stringResource(R.string.set_messages_read, counted(R.plurals.count_messages, it))
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rangeLabel(months: Int): String = when (months) {
    0 -> stringResource(R.string.set_range_everything)
    1 -> stringResource(R.string.set_range_one_month)
    12 -> stringResource(R.string.set_range_one_year)
    else -> stringResource(R.string.set_range_months, months)
}
