@file:OptIn(ExperimentalMaterial3Api::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(painterResource(R.drawable.ic_sym_filter_list), contentDescription = null)
                Text(
                    stringResource(R.string.txn_filters),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (selectedCategoryId != null || selectedAccountId != null) {
                    TextButton(onClick = onClearAll) { Text(stringResource(R.string.action_clear)) }
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
                            selected = selectedAccountId == account.id,
                            onClick = {
                                onAccount(if (selectedAccountId == account.id) null else account.id)
                            },
                            label = { Text(account.displayName, maxLines = 1) },
                            leadingIcon = {
                                Icon(painterResource(iconFor(account.kind)), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }

            if (categories.isNotEmpty()) {
                SectionHeader(R.drawable.ic_sym_category, stringResource(R.string.label_category))
                CategoryRows(
                    categories,
                    selected = { it.id == selectedCategoryId },
                    onClick = { onCategory(if (selectedCategoryId == it.id) null else it.id) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
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
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
