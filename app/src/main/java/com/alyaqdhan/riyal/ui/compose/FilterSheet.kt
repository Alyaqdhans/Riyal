@file:OptIn(ExperimentalMaterial3Api::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Category

/**
 * The filters that don't fit on one row: categories and accounts, behind a button, so
 * the list of transactions starts near the top of the screen instead of below two rows
 * of chips the user scrolls past every time.
 *
 * The chips left on the screen are the ones worth a single tap (all / in / out /
 * transfers); everything with dozens of options lives here.
 */
@Composable
fun FilterSheet(
    categories: List<Category>,
    accounts: List<Account>,
    selectedCategoryId: String?,
    selectedAccountId: String?,
    onCategory: (String?) -> Unit,
    onAccount: (String?) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filters", style = MaterialTheme.typography.titleMedium)
                if (selectedCategoryId != null || selectedAccountId != null) {
                    TextButton(onClick = onClearAll) { Text("Clear") }
                }
            }

            if (accounts.isNotEmpty()) {
                Text("Account", style = MaterialTheme.typography.labelLarge)
                FilterFlow {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = selectedAccountId == account.id,
                            onClick = {
                                onAccount(if (selectedAccountId == account.id) null else account.id)
                            },
                            label = { Text(account.displayName) },
                        )
                    }
                }
            }

            if (categories.isNotEmpty()) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FilterFlow {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = {
                                onCategory(if (selectedCategoryId == category.id) null else category.id)
                            },
                            label = { Text(category.name) },
                            leadingIcon = { CategoryIcon(category.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}
