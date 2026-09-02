@file:OptIn(ExperimentalMaterial3Api::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
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

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onChange(slice.shifted(back = true)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier period")
            }
            AnimatedContent(targetState = slice.label, label = "sliceTitle") { label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showPicker = true },
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pick a period")
                }
            }
            IconButton(
                onClick = { onChange(slice.shifted(back = false)) },
                enabled = allowFuture || slice.endExclusive <= System.currentTimeMillis(),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later period")
            }
        }
        // Now that the chosen period outlives the screen, there has to be one tap back to
        // the present - otherwise a month picked in March is still what greets you in June,
        // and the way home is three taps into the picker. It only appears when there is
        // somewhere to come back from.
        if (!slice.isThisMonth) {
            TextButton(
                onClick = { onChange(TimeSlice.thisMonth()) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Back to this month", style = MaterialTheme.typography.labelMedium) }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Pick a period") },
            text = {
                Column {
                    val now = YearMonth.now()
                    fun choose(s: TimeSlice) {
                        onChange(s)
                        showPicker = false
                    }
                    PickerOption("This month") { choose(TimeSlice.ofMonth(now)) }
                    PickerOption("Last month") { choose(TimeSlice.ofMonth(now.minusMonths(1))) }
                    PickerOption("This week") { choose(TimeSlice.thisWeek()) }
                    PickerOption("Last 3 months") { choose(TimeSlice.lastMonths(3)) }
                    PickerOption("Last 6 months") { choose(TimeSlice.lastMonths(6)) }
                    PickerOption("This year") { choose(TimeSlice.thisYear()) }
                    PickerOption("All time") { choose(TimeSlice.allTime(txns)) }
                    PickerOption("Custom range…") {
                        showPicker = false
                        showRange = true
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Close") } },
        )
    }

    if (showRange) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null &&
                        rangeState.selectedEndDateMillis != null,
                    onClick = {
                        onChange(
                            TimeSlice.ofDays(
                                TimeSlice.utcDay(rangeState.selectedStartDateMillis!!),
                                TimeSlice.utcDay(rangeState.selectedEndDateMillis!!),
                            )
                        )
                        showRange = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(460.dp))
        }
    }
}

@Composable
fun PickerOption(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}
