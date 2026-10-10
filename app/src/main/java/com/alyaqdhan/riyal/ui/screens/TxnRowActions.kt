package com.alyaqdhan.riyal.ui.screens

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// What a swiped row does once you confirm it, shared by every list that draws one, so
// archiving from the transactions list and archiving from a category say and do the
// same thing.

/**
 * What the swipe asks before removing. Removal cannot be undone - the message is kept
 * out of future scans too - so the prompt says which of the three things is about to
 * happen rather than a bare "Remove?".
 */
/** The word on the revealed button. A label now, not a question: nothing is asked. */
@StringRes
internal fun deleteLabelFor(txn: Txn): Int = when {
    txn.manual -> R.string.action_delete
    txn.isTransfer -> R.string.row_remove_both
    else -> R.string.row_remove
}

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
