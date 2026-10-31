package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import my.nanihadesuka.compose.InternalColumnScrollbar
import my.nanihadesuka.compose.InternalLazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionActionable
import my.nanihadesuka.compose.ScrollbarSettings

/**
 * The scroll thumb every list in the app carries, LazyColumnScrollbar's in Material's
 * colours: it shows while its list moves and for a moment after, as ytdlnis's does, and it
 * can be held and dragged to run down the list. Held, it takes the accent colour.
 *
 * It keeps to the end edge (the left in Arabic) and fades rather than sliding away. It only
 * takes touches while it shows, so the end of a row stays tappable the rest of the time.
 */
@Composable
private fun thumbSettings(): ScrollbarSettings {
    val scheme = MaterialTheme.colorScheme
    return ScrollbarSettings(
        thumbThickness = 6.dp,
        scrollbarPadding = 6.dp,
        thumbUnselectedColor = scheme.outline,
        thumbSelectedColor = scheme.primary,
        selectionActionable = ScrollbarSelectionActionable.WhenVisible,
        hideDelayMillis = 1500,
        hideDisplacement = 0.dp,
        durationAnimationMillis = 250,
    )
}

/**
 * [content] - a LazyColumn on [state] - with the thumb over its end edge. The thumb runs
 * the height of the list, less [bottom]: on a tab, the floating toolbar over its foot.
 *
 * Size and place [content] through [modifier] (Scaffold padding, weight), not the list's
 * own, so the thumb covers exactly the part that scrolls.
 */
@Composable
fun ScrollThumb(
    state: LazyListState,
    modifier: Modifier = Modifier,
    bottom: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        // A list that fits has nothing to show: a drag on it still counts as scrolling,
        // and would light up a thumb the whole height of the track.
        if (state.canScrollForward || state.canScrollBackward) {
            InternalLazyColumnScrollbar(
                state = state,
                settings = thumbSettings(),
                modifier = Modifier.matchParentSize().padding(bottom = bottom),
            )
        }
    }
}

/** [ScrollThumb] for a Column scrolled by [state]. */
@Composable
fun ScrollThumb(
    state: ScrollState,
    modifier: Modifier = Modifier,
    bottom: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        if (state.canScrollForward || state.canScrollBackward) {
            InternalColumnScrollbar(
                state = state,
                settings = thumbSettings(),
                visibleLengthDp = with(LocalDensity.current) { state.viewportSize.toDp() },
                modifier = Modifier.matchParentSize().padding(bottom = bottom),
            )
        }
    }
}
