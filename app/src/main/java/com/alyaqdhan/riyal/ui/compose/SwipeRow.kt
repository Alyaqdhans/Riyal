@file:OptIn(ExperimentalMaterial3Api::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A row you can swipe instead of hunting for a button: one way archives it, the other
 * asks before deleting.
 *
 * The two directions are deliberately not symmetric. Archiving is reversible and takes
 * effect immediately; deleting says a record the bank sent was never real, so it goes
 * through a confirmation rather than happening under your thumb. That is also why the
 * delete side springs back on release - the row only disappears once you have said yes.
 */
@Composable
fun SwipeableTxnRow(
    archived: Boolean,
    onArchive: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        // Neither direction dismisses the row by itself: archiving redraws it in place,
        // deleting waits for the dialog's answer.
        confirmValueChange = { false },
    )

    // A settled swipe past the threshold is the gesture; the box itself never dismisses.
    LaunchedEffect(state.targetValue) {
        when (state.targetValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onArchive()
                state.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onRequestDelete()
                state.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val toStart = state.targetValue == SwipeToDismissBoxValue.EndToStart ||
                state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val colour = if (toStart) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
            val onColour = if (toStart) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colour)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (toStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (toStart) Icons.Filled.Delete else Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = onColour,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        when {
                            toStart -> "Delete"
                            archived -> "Unarchive"
                            else -> "Archive"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = onColour,
                    )
                }
            }
        },
        content = { Box(Modifier.fillMaxWidth()) { content() } },
    )
}
