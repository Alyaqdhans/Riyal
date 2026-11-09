@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Banks
import com.alyaqdhan.riyal.data.MoneySenders
import com.alyaqdhan.riyal.data.SenderEvidence

/**
 * Why a sender left out looks like money, in a line: its balance moving with what it
 * sends when it did, or else how many of its messages quote a balance.
 */
@Composable
fun moneyEvidence(e: SenderEvidence): String =
    if (e.proofs >= MoneySenders.PROOFS) {
        stringResource(R.string.money_evidence_moves, counted(R.plurals.count_messages, e.messages))
    } else {
        stringResource(R.string.money_evidence_quotes, counted(R.plurals.count_messages, e.withBalance))
    }

/**
 * The question about one sender: read what it sends from now on, or set it aside for
 * good. Closing it some other way leaves it to be asked about again.
 */
@Composable
fun MoneySenderDialog(
    evidence: SenderEvidence,
    onRead: () -> Unit,
    onNotMine: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pop = rememberDialogPop()
    val name = Banks.displayName(evidence.sender)
    AlertDialog(
        onDismissRequest = { pop.leave(onDismiss) },
        modifier = Modifier.popIn(pop),
        icon = { Icon(painterResource(R.drawable.ic_sym_wallet), contentDescription = null) },
        title = { Text(stringResource(R.string.money_ask_title, name)) },
        text = { Text(moneyEvidence(evidence) + "\n\n" + stringResource(R.string.money_ask_body)) },
        confirmButton = {
            Button(
                onClick = {
                    pop.leave {
                        onRead()
                        onDismiss()
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.money_read)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    pop.leave {
                        onNotMine()
                        onDismiss()
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.money_not_mine)) }
        },
    )
}
