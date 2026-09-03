package com.alyaqdhan.riyal.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// What a swiped row does once you confirm it, shared by every list that draws one, so
// archiving from Activity and archiving from a category say and do the same thing.

/**
 * What the swipe asks before removing. Removal cannot be undone - the message is kept
 * out of future scans too - so the prompt says which of the three things is about to
 * happen rather than a bare "Remove?".
 */
/** The word on the revealed button. A label now, not a question: nothing is asked. */
internal fun deleteLabelFor(txn: Txn): String = when {
    txn.manual -> "Delete"
    txn.isTransfer -> "Remove both"
    else -> "Remove"
}

internal fun archiveWithUndo(
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    txn: Txn,
    archive: Boolean,
) {
    vm.archiveTxn(txn, archive)
    scope.launch {
        val result = snackbar.showSnackbar(
            message = if (archive) "Archived" else "Back in your transactions",
            actionLabel = "Undo",
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
    vm.ignoreTxn(txn)
    scope.launch {
        // No Undo here, and deliberately no offer of one: the record is gone and the
        // message it came from stays out of future scans. Saying so is the honest thing
        // a button that cannot deliver would not be.
        snackbar.showSnackbar(
            if (txn.manual) "Deleted" else "Removed, and kept out of future scans",
        )
    }
}
