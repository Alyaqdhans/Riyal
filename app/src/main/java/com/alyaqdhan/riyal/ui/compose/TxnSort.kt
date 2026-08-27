package com.alyaqdhan.riyal.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType

/**
 * How a list of records is ordered. Newest-first is what a transaction list is for; the
 * two "biggest" orders answer the other question people actually ask of one - what did
 * the money go on - without making them scroll a year of small card payments to find out.
 *
 * Shared by Activity and the per-category page, because the question is the same on both.
 */
enum class TxnSort(val label: String, val byDate: Boolean) {
    NEWEST("Newest", true),
    OLDEST("Oldest", true),
    BIGGEST_OUT("Biggest out", false),
    BIGGEST_IN("Biggest in", false),
    ;

    /** Only meaningful for the amount orders; the date orders group by day instead. */
    fun applyTo(txns: List<Txn>): List<Txn> = when (this) {
        NEWEST -> txns.sortedByDescending { it.atMillis }
        OLDEST -> txns.sortedBy { it.atMillis }
        // A ranking of spending should not be led by income that happens to be larger,
        // so the other side is ranked below rather than mixed in.
        BIGGEST_OUT -> txns.sortedWith(
            compareByDescending<Txn> { it.type == TxnType.EXPENSE }
                .thenByDescending { it.amountMinor },
        )
        BIGGEST_IN -> txns.sortedWith(
            compareByDescending<Txn> { it.type == TxnType.INCOME }
                .thenByDescending { it.amountMinor },
        )
    }
}

@Composable
fun SortChip(current: TxnSort, onSelect: (TxnSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = current != TxnSort.NEWEST,
            onClick = { open = true },
            label = { Text(current.label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TxnSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                    trailingIcon = {
                        if (option == current) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}
