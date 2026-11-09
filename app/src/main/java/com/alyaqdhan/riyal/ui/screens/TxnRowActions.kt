package com.alyaqdhan.riyal.ui.screens

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.TxnEditSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// What a record's swipe and its sheet do, shared by every list that draws one, so
// archiving from the transactions list and archiving from a category say and do the
// same thing.

internal fun archiveWithUndo(
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    txn: Txn,
    archive: Boolean,
) {
    val context = vm.getApplication<Application>()
    vm.archiveTxn(txn, archive)
    scope.launch {
        val result = snackbar.showSnackbar(
            message = context.getString(
                if (archive) R.string.row_archived else R.string.row_unarchived
            ),
            actionLabel = context.getString(R.string.action_undo),
            // A notice with an action stays until dismissed unless told otherwise; this
            // one goes by itself, after long enough to reach Undo.
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) vm.archiveTxn(txn, !archive)
    }
}

internal fun removeForGood(
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    txn: Txn,
) {
    val context = vm.getApplication<Application>()
    vm.ignoreTxn(txn)
    scope.launch {
        // No Undo here, and deliberately no offer of one: the record is gone and the
        // message it came from stays out of future scans. Saying so is the honest thing
        // a button that cannot deliver would not be.
        snackbar.showSnackbar(
            context.getString(
                if (txn.manual) R.string.row_deleted else R.string.row_removed
            ),
        )
    }
}

/** A record's sheet, wired to the view model: every change it makes closes it. */
@Composable
internal fun RecordSheet(vm: MainViewModel, txn: Txn, accounts: List<Account>, onClose: () -> Unit) {
    val categoryUse by vm.categoryUse.collectAsState()
    val askEachTime by vm.askEachTime.collectAsState()
    TxnEditSheet(
        txn = txn,
        accounts = accounts,
        onApply = { categoryId, rulePattern ->
            vm.setCategory(txn, categoryId, rulePattern)
            onClose()
        },
        onDismiss = onClose,
        rememberByDefault = vm.prefs.smartRules,
        categoryUse = categoryUse,
        askEachTime = askEachTime,
        onAskEachTime = { vm.setAskEachTime(txn.merchant.orEmpty(), it) },
        onSetAccount = {
            vm.setTxnAccount(txn, it)
            onClose()
        },
        onMarkTransfer = { from, to ->
            vm.markAsTransfer(txn, from, to)
            onClose()
        },
        onSplitTransfer = {
            vm.splitTransfer(txn)
            onClose()
        },
        onReadDifferently = { fields ->
            vm.teachReading(txn.sender, txn.body, fields)
            onClose()
        },
        defaultCurrency = vm.prefs.defaultCurrency,
    )
}

/** What removing a record is called: a record you added is deleted; a transfer goes with its other half. */
@StringRes
internal fun removeLabelOf(txn: Txn): Int = when {
    txn.manual -> R.string.action_delete
    txn.isTransfer -> R.string.row_remove_both
    else -> R.string.row_remove
}
