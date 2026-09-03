package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.launch

/**
 * How far a row travels, as a share of its width. Wide enough for an icon over a word
 * and for a thumb to land on without aiming, short enough that the swipe is a flick
 * rather than a haul across the screen. A fraction rather than a fixed size so the
 * gesture is the same shape on any phone, bounded either way so it stays a tappable
 * button on a small screen and does not become a wall on a tablet.
 */
private const val RevealFraction = 0.38f
private val MinReveal = 116.dp
private val MaxReveal = 176.dp

/**
 * How much of a drag past the stop still moves the row. Enough to feel the edge give
 * rather than hit a wall, not enough to look like the row might keep going.
 */
private const val Overdrag = 0.22f

/** Past this much of the travel, letting go opens rather than closes. */
private const val SettleAt = 0.5f

/** A flick faster than this decides the direction on its own, wherever it started. */
private const val FlingVelocity = 700f

/**
 * A row you can swipe instead of hunting for a button: one way archives it, the other
 * removes it.
 *
 * The swipe reveals, it does not decide. A row travels [RevealFraction] of its width
 * and stops there with one action showing, and that action runs when it is tapped.
 * Nothing happens at the end of a gesture, so there is no distance at which the row is
 * committed and no question to answer afterwards: a swipe begun by accident is undone
 * by letting go and swiping back, and a swipe meant on purpose costs one tap.
 *
 * Dragging past the stop still moves the row, with most of the travel taken out of it,
 * and it settles back to the stop on release. The give is what says the row has reached
 * its limit; a hard edge reads as the gesture having failed.
 */
@Composable
fun SwipeableTxnRow(
    archived: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    deleteLabel: String = "Remove",
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offset = remember { Animatable(0f) }
    var reveal by remember { mutableFloatStateOf(0f) }

    val x = offset.value
    // Negative is a swipe to the left, which uncovers the right-hand edge.
    val toStart = x < 0f
    val open = abs(x) > 0.5f

    fun close() = scope.launch { offset.animateTo(0f, spring()) }

    fun settle(velocity: Float) = scope.launch {
        val target = when {
            reveal <= 0f -> 0f
            velocity > FlingVelocity -> if (x > 0f) reveal else 0f
            velocity < -FlingVelocity -> if (x < 0f) -reveal else 0f
            x > reveal * SettleAt -> reveal
            x < -reveal * SettleAt -> -reveal
            else -> 0f
        }
        offset.animateTo(target, spring())
    }

    Box(
        modifier
            .fillMaxWidth()
            // The row slides inside its own bounds, not across the list's margin. Without
            // this the content escapes the card it belongs to and runs to the screen edge
            // while every row around it stays inset.
            .clip(RoundedCornerShape(20.dp))
            .onSizeChanged { size ->
                val min = with(density) { MinReveal.toPx() }
                val max = with(density) { MaxReveal.toPx() }
                reveal = (size.width * RevealFraction).coerceIn(min, max)
            },
    ) {
        if (open) {
            val remove = toStart
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        if (remove) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = if (remove) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                RevealedAction(
                    label = when {
                        remove -> deleteLabel
                        archived -> "Unarchive"
                        else -> "Archive"
                    },
                    remove = remove,
                    width = with(density) { reveal.toDp() },
                    onClick = {
                        if (remove) onDelete() else onArchive()
                        close()
                    },
                )
            }
        }

        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo(resist(offset.value + delta, reveal)) }
                    },
                    onDragStopped = { velocity -> settle(velocity) },
                ),
        ) {
            content()
            // An open row's content is a lid over the action, not a target. Tapping it
            // shuts the row instead of opening whatever the row itself opens, so a tap
            // aimed at the action but landing short costs nothing.
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

/** Past the stop the row still gives, but only by [Overdrag] of what the thumb asks for. */
private fun resist(target: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val over = abs(target) - limit
    if (over <= 0f) return target
    return sign(target) * (limit + over * Overdrag)
}

/**
 * The thing you tap. It fills the strip the swipe opened rather than sitting somewhere
 * inside it, so the whole revealed area is the target and the gesture does not have to
 * be finished accurately to be finished at all.
 */
@Composable
private fun RevealedAction(
    label: String,
    remove: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val tint = if (remove) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (remove) Icons.Filled.Delete else Icons.Filled.Refresh,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
