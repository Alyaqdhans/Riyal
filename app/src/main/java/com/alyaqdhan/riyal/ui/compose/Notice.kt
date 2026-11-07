package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Where a screen's notices show: Material's snackbar, kept dark in a dark theme.
 *
 * Material 3 draws a snackbar in the inverse colours, so it stands out from any screen:
 * dark over a light one, and light over a dark one, where it glared. Material has nothing
 * newer for a notice. So in a dark theme the snackbar takes the highest of the theme's
 * own surfaces, with its text and accent colours, and lifts off the page by its shadow;
 * in a light theme it keeps Material's default, which is dark already.
 */
@Composable
fun NoticeHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    SnackbarHost(state, modifier) { data ->
        if (dark) {
            Snackbar(
                data,
                containerColor = scheme.surfaceContainerHighest,
                contentColor = scheme.onSurface,
                actionColor = scheme.primary,
                actionContentColor = scheme.primary,
                dismissActionContentColor = scheme.onSurfaceVariant,
            )
        } else {
            Snackbar(data)
        }
    }
}
