@file:OptIn(ExperimentalMaterial3Api::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A row you can swipe instead of hunting for a button: one way archives it, the other
 * removes it.
 *
 * Neither happens under your thumb. A swipe past the threshold holds the row open and
 * asks, in the space the swipe just opened, and the action only runs when you say yes
 * there. Putting the question where the gesture happened - rather than in a dialog over
 * the whole screen - keeps the answer next to the row it concerns, and means a swipe
 * begun by accident costs one tap on Cancel.
 */
@Composable
fun SwipeableTxnRow(
    archived: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    deletePrompt: String = "Remove?",
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            // Which side is showing: the direction being dragged, or - once the row is
            // held open - the side it settled on.
            val settled = state.currentValue
            val toStart = settled == SwipeToDismissBoxValue.EndToStart ||
                (settled == SwipeToDismissBoxValue.Settled &&
                    state.dismissDirection == SwipeToDismissBoxValue.EndToStart)
            val asking = settled != SwipeToDismissBoxValue.Settled

            val colour by animateColorAsState(
                if (toStart) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
                label = "swipeBackground",
            )
            val onColour = if (toStart) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
            val action = when {
                toStart -> "Remove"
                archived -> "Unarchive"
                else -> "Archive"
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colour)
                    .padding(horizontal = 20.dp),
                contentAlignment = when {
                    asking -> Alignment.Center
                    toStart -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                },
            ) {
                if (asking) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (toStart) deletePrompt else "$action?",
                            style = MaterialTheme.typography.labelLarge,
                            color = onColour,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { scope.launch { state.reset() } },
                                colors = ButtonDefaults.textButtonColors(contentColor = onColour),
                            ) { Text("Cancel") }
                            TextButton(
                                onClick = {
                                    if (toStart) onDelete() else onArchive()
                                    scope.launch { state.reset() }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = onColour),
                            ) { Text(action) }
                        }
                    }
                } else {
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
                            action,
                            style = MaterialTheme.typography.labelLarge,
                            color = onColour,
                        )
                    }
                }
            }
        },
        content = { Box(Modifier.fillMaxWidth()) { content() } },
    )
}
