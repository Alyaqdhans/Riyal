@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import com.alyaqdhan.riyal.ui.compose.CurrencySign
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Backup
import com.alyaqdhan.riyal.core.Currencies
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Language
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.rememberDialogPop
import com.alyaqdhan.riyal.ui.compose.ConfirmDeleteButton
import com.alyaqdhan.riyal.ui.compose.CurrencyPickerSheet
import com.alyaqdhan.riyal.ui.compose.ExchangeRatesSheet
import com.alyaqdhan.riyal.ui.compose.ScanBar
import java.time.LocalDate
import com.alyaqdhan.riyal.ui.compose.PresetSheet
import com.alyaqdhan.riyal.ui.compose.Preset
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
    onOpenLists: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenSetup: () -> Unit,
    /** Delete everything, then start over from the welcome. */
    onWipe: () -> Unit,
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
                scan = scan,
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
                Currencies.of(currency)?.let { stringResource(it.name) } ?: currency,
                valueLead = { CurrencySign(currency, height = 14.dp) },
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
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.set_read_older)) }
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
            NavRow(
                R.drawable.ic_sym_filter_list,
                stringResource(R.string.set_lists),
                stringResource(R.string.set_lists_detail),
                onClick = onOpenLists,
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
            ) { dialog = SettingsDialog.BACKUP }
            // Nothing changes on picking a file: what is in it is offered first.
            NavRow(
                R.drawable.ic_sym_restore,
                stringResource(R.string.set_restore),
                stringResource(R.string.set_restore_detail),
                chevron = false,
                onClick = onRestore,
            )
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

    val close = { dialog = SettingsDialog.NONE }
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
            val since = Dates.pattern("d MMM uuuu")
            val options = listOf(
                Triple(1, R.drawable.ic_sym_calendar_today, stringResource(R.string.set_range_one_month)),
                Triple(3, R.drawable.ic_sym_date_range, stringResource(R.string.set_range_months, 3)),
                Triple(6, R.drawable.ic_sym_date_range, stringResource(R.string.set_range_months, 6)),
                Triple(12, R.drawable.ic_sym_event, stringResource(R.string.set_range_one_year)),
                Triple(0, R.drawable.ic_sym_all_inclusive, stringResource(R.string.set_range_everything)),
            )
            PresetSheet(
                title = stringResource(R.string.set_how_far_back),
                presets = options.map { (months, icon, label) ->
                    Preset(
                        icon = icon,
                        title = label.replaceFirstChar { it.titlecase() },
                        detail = if (months == 0) {
                            stringResource(R.string.range_everything_detail)
                        } else {
                            stringResource(R.string.range_since, since.format(LocalDate.now().minusMonths(months.toLong())))
                        },
                        selected = months == rangeMonths,
                    ) {
                        dialog = SettingsDialog.NONE
                        rangeMonths = months
                        prefs.scanRangeMonths = months
                        note(R.string.log_note_scan_range, label)
                    }
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
        SettingsDialog.LOG -> {
            val pop = rememberDialogPop()
            AlertDialog(
                onDismissRequest = { pop.leave(close) },
                modifier = Modifier.popIn(pop),
                title = { Text(stringResource(R.string.set_verbose_log)) },
                text = { Text(stringResource(R.string.set_help_verbose)) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { clipboard.setClipEntry(plainText(Verbose.dump())) }
                        note(R.string.log_note_log_copied)
                        pop.leave(close)
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_copy)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        Verbose.clear()
                        Verbose.info(R.string.log_note_log_cleared)
                        Verbose.flush()
                        pop.leave(close)
                    }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_clear)) }
                },
            )
        }
        SettingsDialog.WIPE -> {
            val pop = rememberDialogPop()
            AlertDialog(
                onDismissRequest = { pop.leave(close) },
                modifier = Modifier.popIn(pop),
                title = { Text(stringResource(R.string.set_wipe_title)) },
                text = { Text(stringResource(R.string.set_wipe_body)) },
                confirmButton = {
                    ConfirmDeleteButton(stringResource(R.string.action_delete)) {
                        pop.leave {
                            close()
                            onWipe()
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pop.leave(close) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
        SettingsDialog.BACKUP -> {
            var parts by remember { mutableStateOf(vm.backupParts) }
            var withMessages by remember { mutableStateOf(prefs.backupIncludeMessages) }
            val pop = rememberDialogPop()
            AlertDialog(
                onDismissRequest = { pop.leave(close) },
                modifier = Modifier.popIn(pop),
                title = { Text(stringResource(R.string.backup_save_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            stringResource(R.string.backup_save_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Backup.Part.entries.forEach { part ->
                            PartRow(
                                title = stringResource(part.label),
                                detail = stringResource(part.detail),
                                checked = part in parts,
                                onChange = { parts = if (it) parts + part else parts - part },
                            )
                        }
                        // Message text is in the records and in what was dismissed; a file
                        // of categories alone has none to leave out.
                        if (Backup.Part.RECORDS in parts || Backup.Part.LISTS in parts) {
                            PartRow(
                                title = stringResource(R.string.set_backup_messages),
                                detail = stringResource(R.string.set_backup_messages_detail),
                                checked = withMessages,
                                onChange = { withMessages = it },
                                switch = true,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = parts.isNotEmpty(),
                        onClick = {
                            vm.backupParts = parts
                            prefs.backupIncludeMessages = withMessages
                            pop.leave {
                                close()
                                onBackup()
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.action_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { pop.leave(close) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
        SettingsDialog.EXPORT -> {
            val pop = rememberDialogPop()
            AlertDialog(
                onDismissRequest = { pop.leave(close) },
                modifier = Modifier.popIn(pop),
                title = { Text(stringResource(R.string.set_export_title, counted(R.plurals.count_records, txns.size))) },
                text = {
                    Text(stringResource(if (txns.isEmpty()) R.string.set_export_empty else R.string.set_export_body))
                },
                confirmButton = {
                    TextButton(
                        enabled = txns.isNotEmpty(),
                        onClick = {
                            pop.leave {
                                close()
                                onExport()
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(stringResource(R.string.action_export)) }
                },
                dismissButton = {
                    TextButton(onClick = { pop.leave(close) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }

    // A backup picked to import from: what is in it, to choose from before anything changes.
    val offer by vm.importOffer.collectAsState()
    offer?.let { o ->
        var parts by remember(o) { mutableStateOf(o.contents.keys) }
        val pop = rememberDialogPop()
        AlertDialog(
            onDismissRequest = { pop.leave { vm.dismissImport() } },
            modifier = Modifier.popIn(pop),
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.backup_import_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    o.contents.forEach { (part, count) ->
                        PartRow(
                            title = stringResource(R.string.backup_part_count, stringResource(part.label), count),
                            detail = stringResource(part.detail),
                            checked = part in parts,
                            onChange = { parts = if (it) parts + part else parts - part },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = parts.isNotEmpty(),
                    onClick = {
                        val chosen = parts
                        pop.leave { vm.importParts(chosen) }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.backup_import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pop.leave { vm.dismissImport() } }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * One part of a backup to save or bring in: the whole row is its checkbox (or, for the
 * message text, its switch), and stays plain when on.
 *
 * It keeps the list item's own padding. Without it, the rounder corners a row morphs to
 * when it is ticked or pressed cut into the checkbox, the switch and the first letters.
 */
@Composable
private fun PartRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    switch: Boolean = false,
) {
    val plain = ListItemDefaults.colors(containerColor = Color.Transparent)
    ListItem(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.intoDialogMargin(),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent,
            selectedContentColor = plain.contentColor,
            selectedSupportingContentColor = plain.supportingContentColor,
            selectedTrailingContentColor = plain.trailingContentColor,
            selectedLeadingContentColor = plain.leadingContentColor,
        ),
        leadingContent = if (switch) null else ({ Checkbox(checked = checked, onCheckedChange = null) }),
        trailingContent = if (switch) ({ Switch(checked = checked, onCheckedChange = null) }) else null,
        supportingContent = { Text(detail) },
    ) { Text(title) }
}

/**
 * Widens a list item by its own side padding, out into the margin of the dialog it sits
 * in: its checkbox and text then line up with the dialog's own text, and its shape still
 * has room around them.
 */
private fun Modifier.intoDialogMargin(): Modifier = layout { measurable, constraints ->
    val start = ListItemDefaults.ContentPadding.calculateStartPadding(layoutDirection).roundToPx()
    val end = ListItemDefaults.ContentPadding.calculateEndPadding(layoutDirection).roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = start + end))
    layout(placeable.width - start - end, placeable.height) { placeable.placeRelative(-start, 0) }
}

/** The one dialog or sheet Settings can have open at a time. */
private enum class SettingsDialog { NONE, LANGUAGE, RANGE, CURRENCY, RATES, LOG, WIPE, BACKUP, EXPORT }

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
    /** Drawn before [value]: a currency's sign, where the value is a currency. */
    valueLead: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        enabled = enabled,
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                valueLead?.invoke()
                Text(
                    value,
                    color = if (warning) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
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
    val plain = ListItemDefaults.colors()
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // Saying a setting is on is the switch's job. Left to the list item, every row that
        // was on got filled in, and three in a row ran together into one block.
        colors = ListItemDefaults.colors(
            selectedContainerColor = plain.containerColor,
            selectedContentColor = plain.contentColor,
            selectedLeadingContentColor = plain.leadingContentColor,
            selectedTrailingContentColor = plain.trailingContentColor,
            selectedSupportingContentColor = plain.supportingContentColor,
            selectedOverlineContentColor = plain.overlineContentColor,
        ),
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
    val pop = rememberDialogPop()
    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton) { pop.leave { onPick(value) } }
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
            TextButton(onClick = { pop.leave(onDismiss) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * What the app has actually done, before anything it can be told to do: Settings is
 * where people go to find out whether the thing is working.
 */
@Composable
private fun StatusCard(
    scan: MainViewModel.ScanState,
    lastScanAt: Long,
    records: Int,
    accounts: Int,
    messagesRead: Int?,
) {
    val scheme = MaterialTheme.colorScheme
    val running = scan as? MainViewModel.ScanState.Running
    val failed = running == null && scan is MainViewModel.ScanState.Failed
    val never = lastScanAt <= 0L
    // One state, said once: the icon, the headline and the colour they share. Working
    // is the quiet case; only trouble takes a colour of its own.
    val icon = when {
        running != null -> R.drawable.ic_sym_sync
        failed -> R.drawable.ic_sym_error
        never -> R.drawable.ic_sym_info
        else -> R.drawable.ic_sym_check_circle
    }
    val (tone, onTone) = when {
        failed -> scheme.errorContainer to scheme.onErrorContainer
        never && running == null -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = tone, contentColor = onTone, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(
                            when {
                                running != null -> R.string.set_status_scanning
                                failed -> R.string.set_status_failed
                                never -> R.string.set_no_scan_yet
                                else -> R.string.set_status_up_to_date
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when {
                            running != null && running.total > 0 ->
                                stringResource(R.string.set_scan_progress, running.processed, running.total)
                            running != null -> stringResource(R.string.set_scan_starting)
                            never -> stringResource(R.string.set_status_never_body)
                            else -> stringResource(R.string.set_last_scan, relativeTime(lastScanAt))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (running != null) {
                ScanBar(
                    progress = if (running.total > 0) ({ running.processed.toFloat() / running.total }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!never) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(R.drawable.ic_sym_payments, counted(R.plurals.count_records, records))
                    StatusPill(R.drawable.ic_sym_account_balance, counted(R.plurals.count_accounts, accounts))
                    messagesRead?.let {
                        StatusPill(
                            R.drawable.ic_sym_mail_read,
                            stringResource(R.string.set_messages_read, counted(R.plurals.count_messages, it)),
                        )
                    }
                }
            }
        }
    }
}

/** One count under the status: an icon and the figure in words, on a tonal pill. */
@Composable
private fun StatusPill(@DrawableRes icon: Int, text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(text, style = MaterialTheme.typography.labelLarge)
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
