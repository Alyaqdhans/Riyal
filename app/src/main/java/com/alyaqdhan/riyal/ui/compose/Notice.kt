package com.alyaqdhan.riyal.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/**
 * Where a screen's notices show: Material's snackbar, in the theme's own colours, and
 * swiped either way to put it away.
 *
 * Material 3 draws a snackbar in the inverse colours, so it stands out from any screen:
 * dark over a light one, and light over a dark one, where it glared. Material has nothing
 * newer for a notice. So the snackbar takes the highest of the theme's own surfaces, with
 * its text and accent colours - light in a light theme, dark in a dark one - and its
 * shadow lifts it off the page.
 *
 * A notice goes by itself after its time, or sooner with a swipe sideways, as Material's
 * own swipe-to-dismiss does it.
 */
@Composable
fun NoticeHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    SnackbarHost(state, modifier) { data ->
        // Each notice starts unswiped, whatever the one before it did.
        key(data) {
            SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) {
                Snackbar(
                    data,
                    containerColor = scheme.surfaceContainerHighest,
                    contentColor = scheme.onSurface,
                    actionColor = scheme.primary,
                    actionContentColor = scheme.primary,
                    dismissActionContentColor = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
