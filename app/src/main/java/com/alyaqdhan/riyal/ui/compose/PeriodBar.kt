@file:OptIn(ExperimentalMaterial3Api::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Txn
import java.time.YearMonth

/**
 * The period control used on Analysis, on the budget section and on a category page:
 * chevrons step by the slice's own length, and the title opens a picker with the usual
 * presets plus a calendar range. One control, so stepping months feels identical
 * wherever the user does it.
 */
@Composable
fun PeriodBar(
    slice: TimeSlice,
    onChange: (TimeSlice) -> Unit,
    txns: List<Txn>,
    modifier: Modifier = Modifier,
    allowFuture: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }
    var showRange by remember { mutableStateOf(false) }

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(slice.shifted(back = true)) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.period_earlier),
            )
        }
        // The period is a button that looks like one, not a title that happens to open
        // something when tapped.
        FilledTonalButton(onClick = { showPicker = true }, shapes = ButtonDefaults.shapes()) {
            Icon(
                painterResource(R.drawable.ic_sym_calendar_month),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(slice.title(), maxLines = 1)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.period_pick),
            )
        }
        IconButton(
            onClick = { onChange(slice.shifted(back = false)) },
            enabled = allowFuture || slice.endExclusive <= System.currentTimeMillis(),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.period_later),
            )
        }
    }

    if (showPicker) {
        val now = YearMonth.now()
        val choices = listOf(
            Triple(R.drawable.ic_sym_view_week, stringResource(R.string.period_this_week), TimeSlice.thisWeek()),
            Triple(R.drawable.ic_sym_calendar_month, stringResource(R.string.period_this_month), TimeSlice.ofMonth(now)),
            Triple(R.drawable.ic_sym_calendar_today, stringResource(R.string.period_last_month), TimeSlice.ofMonth(now.minusMonths(1))),
            Triple(R.drawable.ic_sym_date_range, stringResource(R.string.period_last_n_months, 3), TimeSlice.lastMonths(3)),
            Triple(R.drawable.ic_sym_date_range, stringResource(R.string.period_last_n_months, 6), TimeSlice.lastMonths(6)),
            Triple(R.drawable.ic_sym_event, stringResource(R.string.period_this_year), TimeSlice.thisYear()),
            Triple(R.drawable.ic_sym_all_inclusive, stringResource(R.string.period_all_time), TimeSlice.allTime(txns)),
        )
        fun same(a: TimeSlice, b: TimeSlice) = a.start == b.start && a.endExclusive == b.endExclusive
        val custom = choices.none { same(it.third, slice) }
        PresetSheet(
            title = stringResource(R.string.period_pick),
            presets = choices.map { (icon, name, choice) ->
                Preset(icon, name, choice.span(), selected = same(choice, slice)) {
                    onChange(choice)
                    showPicker = false
                }
            } + Preset(
                R.drawable.ic_sym_edit_calendar,
                stringResource(R.string.period_custom),
                if (custom) slice.span() else stringResource(R.string.period_custom_detail),
                selected = custom,
            ) {
                showPicker = false
                showRange = true
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showRange) {
        val rangeState = rememberDateRangePickerState()
        val pop = rememberDialogPop()
        DatePickerDialog(
            onDismissRequest = { pop.leave { showRange = false } },
            modifier = Modifier.popIn(pop),
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null &&
                        rangeState.selectedEndDateMillis != null,
                    onClick = {
                        val picked = TimeSlice.ofDays(
                            TimeSlice.utcDay(rangeState.selectedStartDateMillis!!),
                            TimeSlice.utcDay(rangeState.selectedEndDateMillis!!),
                        )
                        pop.leave {
                            onChange(picked)
                            showRange = false
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { pop.leave { showRange = false } }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(460.dp))
        }
    }
}
