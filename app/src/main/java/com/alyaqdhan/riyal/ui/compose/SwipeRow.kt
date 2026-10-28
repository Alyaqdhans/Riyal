@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * How far a row travels, as a share of its width: room for an icon over a word and for a
 * thumb to land on without aiming, bounded either way so it stays a button on a small
 * phone and does not become a wall on a large one.
 */
private const val REVEAL_FRACTION = 0.38f
private val MinReveal = 116.dp
private val MaxReveal = 176.dp

/** Where a swiped row can come to rest. */
private enum class Reveal { CLOSED, ARCHIVE, REMOVE }

/**
 * A record row you can swipe instead of hunting for a button: towards the end archives
 * it, towards the start removes it.
 *
 * The swipe reveals, it does not decide. The row slides part of its width and holds
 * there with one action showing, and that action runs when it is tapped - the tap is the
 * confirmation. Let go short of halfway, or tap the row itself, and it closes again, so a
 * swipe begun by accident costs nothing.
 *
 * Built on Compose's own anchored drag, which settles and flings the row the way every
 * other swipe on the phone does. The colour behind is drawn only while the row is open,
 * inside the list item's own outline, so none of it shows at the rounded corners of a row
 * at rest.
 */
@Composable
fun SwipeableTxnRow(
    archived: Boolean,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes removeLabel: Int = R.string.row_remove,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember { AnchoredDraggableState(Reveal.CLOSED) }
    var reveal by remember { mutableFloatStateOf(0f) }
    val x = state.offset.takeUnless { it.isNaN() } ?: 0f
    val open = abs(x) > 0.5f

    fun close() = scope.launch { state.animateTo(Reveal.CLOSED) }

    val archiveLabel = stringResource(if (archived) R.string.row_unarchive else R.string.row_archive)
    val removeText = stringResource(removeLabel)
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                val min = with(density) { MinReveal.toPx() }
                val max = with(density) { MaxReveal.toPx() }
                val distance = (size.width * REVEAL_FRACTION).coerceIn(min, max)
                if (distance != reveal) {
                    reveal = distance
                    // Dragged right, the row uncovers its left edge; dragged left, its right.
                    state.updateAnchors(
                        DraggableAnchors {
                            Reveal.CLOSED at 0f
                            Reveal.ARCHIVE at distance
                            Reveal.REMOVE at -distance
                        }
                    )
                }
            }
            // The same two actions without the gesture, for TalkBack.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(archiveLabel) { onArchive(); true },
                    CustomAccessibilityAction(removeText) { onRemove(); true },
                )
            },
    ) {
        if (open) {
            val remove = x < 0f
            Box(
                Modifier
                    .matchParentSize()
                    .clip(ListItemDefaults.shapes().shape)
                    .background(
                        if (remove) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = if (remove) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft,
            ) {
                RevealedAction(
                    label = if (remove) removeText else archiveLabel,
                    icon = when {
                        remove -> R.drawable.ic_sym_delete
                        archived -> R.drawable.ic_sym_unarchive
                        else -> R.drawable.ic_sym_archive
                    },
                    remove = remove,
                    width = with(density) { reveal.toDp() },
                    onClick = {
                        if (remove) onRemove() else onArchive()
                        close()
                    },
                )
            }
        }
        Box(
            Modifier
                .absoluteOffset { IntOffset(x.roundToInt(), 0) }
                .fillMaxWidth()
                .anchoredDraggable(state, Orientation.Horizontal),
        ) {
            content()
            // An open row is a lid over its action, not a target: tapping it shuts the row
            // instead of opening the record, so a tap that lands short costs nothing.
            if (open) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { close() }
                )
            }
        }
    }
}

/**
 * The action itself. It fills the strip the swipe opened, so the whole revealed area is
 * the target and the gesture does not have to be finished accurately.
 */
@Composable
private fun RevealedAction(
    label: String,
    icon: Int,
    remove: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val tint = if (remove) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
