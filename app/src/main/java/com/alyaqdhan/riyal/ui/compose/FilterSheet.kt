@file:OptIn(ExperimentalMaterial3Api::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Category

/**
 * The filters that don't fit on one row: accounts and categories, behind the filter
 * button, so the list of transactions starts near the top of the screen instead of
 * below rows of chips the user scrolls past every time.
 *
 * The chips left on the screen are the ones worth a single tap (all / expense / income /
 * transfer); everything with dozens of options lives here, laid out as it is everywhere
 * else a category is picked.
 *
 * Any number of accounts and categories can be on at once: a record shows when it is in
 * one of the chosen categories and touches one of the chosen accounts. Clear is always
 * in the header, off while nothing is chosen; appearing only once something was, it
 * pushed the whole sheet down under the finger.
 */
@Composable
fun FilterSheet(
    categories: List<Category>,
    accounts: List<Account>,
    selectedCategoryIds: Set<String>,
    selectedAccountIds: Set<String>,
    onToggleCategory: (String) -> Unit,
    onToggleAccount: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val scroll = rememberScrollState()
        ScrollThumb(scroll) {
        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MatchedIcon(R.drawable.ic_sym_filter_list, MaterialTheme.typography.titleLarge, Modifier.size(24.dp))
                Text(
                    stringResource(R.string.txn_filters),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                // Material's extra-small button: no taller than the title beside it.
                val height = ButtonDefaults.ExtraSmallContainerHeight
                TextButton(
                    onClick = onClearAll,
                    enabled = selectedCategoryIds.isNotEmpty() || selectedAccountIds.isNotEmpty(),
                    modifier = Modifier.heightIn(min = height),
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                ) {
                    Text(stringResource(R.string.action_clear), style = ButtonDefaults.textStyleFor(height))
                }
            }

            if (accounts.isNotEmpty()) {
                SectionHeader(R.drawable.ic_sym_account_balance, stringResource(R.string.label_account))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = account.id in selectedAccountIds,
                            onClick = { onToggleAccount(account.id) },
                            label = { Text(account.displayName, maxLines = 1) },
                            leadingIcon = {
                                MatchedIcon(iconFor(account.kind), MaterialTheme.typography.labelLarge, Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }

            if (categories.isNotEmpty()) {
                SectionHeader(R.drawable.ic_sym_category, stringResource(R.string.label_category))
                CategoryRows(
                    categories,
                    selected = { it.id in selectedCategoryIds },
                    onClick = { onToggleCategory(it.id) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun SectionHeader(@DrawableRes icon: Int, text: String) {
    Row(
        Modifier.padding(horizontal = 20.dp).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MatchedIcon(
            icon,
            MaterialTheme.typography.labelLarge,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
