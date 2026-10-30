@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * How far a row travels, as a share of its width: room for an icon over a word and for a
 * thumb to land on without aiming, bounded either way so it stays a button on a small
 * phone and does not become a wall on a large one.
 */
private const val REVEAL_FRACTION = 0.38f
private val MinReveal = 116.dp
private val MaxReveal = 176.dp

/** Where a swiped row can come to rest: shut, or slid one way or the other. */
private enum class Reveal { CLOSED, SLID_RIGHT, SLID_LEFT }

/** Which way a tour slides its row. */
enum class SwipeWay { LEFT, RIGHT }

/**
 * What a swipe uncovers: the action, its word and icon, and whether it takes something
 * away (drawn in the error colours) or only moves it (in the secondary ones).
 */
class SwipeAction(
    val label: String,
    @param:DrawableRes val icon: Int,
    val danger: Boolean,
    val onClick: () -> Unit,
)

/**
 * A row you can swipe instead of hunting for a button: dragged right it uncovers
 * [dragRight] at its left edge, dragged left it uncovers [dragLeft] at its right edge. A
 * side with no action does not move.
 *
 * The swipe reveals, it does not decide. The row slides part of its width and holds
 * there with one action showing, and that action runs when it is tapped - the tap is the
 * confirmation. Let go short of halfway, or tap the row itself, and it closes again, so a
 * swipe begun by accident costs nothing.
 *
 * Built on Compose's own anchored drag, which settles and flings the row the way every
 * other swipe on the phone does. The colour behind is drawn only while the row is open,
 * inside the row's own outline ([shape]), so none of it shows at the rounded corners of
 * a row at rest.
 *
 * Given a [tour] with a step to show, the row opens itself that way, a bubble says what
 * the button it uncovered does, and closing the bubble closes the row and moves the tour
 * on. Taps meanwhile only close the bubble, so nothing is removed by the lesson.
 */
@Composable
fun SwipeRow(
    modifier: Modifier = Modifier,
    dragRight: SwipeAction? = null,
    dragLeft: SwipeAction? = null,
    shape: Shape = ListItemDefaults.shapes().shape,
    tour: SwipeTour? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember { AnchoredDraggableState(Reveal.CLOSED) }
    var reveal by remember { mutableFloatStateOf(0f) }
    val x = state.offset.takeUnless { it.isNaN() } ?: 0f
    val open = abs(x) > 0.5f
    val step = tour?.current
    val tip = rememberTooltipState(isPersistent = true)

    fun close() = scope.launch { state.animateTo(Reveal.CLOSED) }

    if (tour != null && step != null) {
        val inView = LocalTabInView.current
        LaunchedEffect(step, inView) {
            if (!inView) return@LaunchedEffect
            try {
                // Once the row has a width to slide by, and the screen has settled.
                snapshotFlow { reveal }.first { it > 0f }
                delay(TOUR_PAUSE_MS)
                state.animateTo(
                    if (step.way == SwipeWay.LEFT) Reveal.SLID_LEFT else Reveal.SLID_RIGHT,
                    tween(TOUR_SLIDE_MS, easing = FastOutSlowInEasing),
                )
                // Shown until dismissed: a tap on its button or anywhere else.
                launch { tip.show() }
                snapshotFlow { tip.isVisible }.first { it }
                snapshotFlow { tip.isVisible }.first { !it }
                state.animateTo(Reveal.CLOSED)
                tour.next()
            } finally {
                // Left halfway - the tab hidden, the row scrolled away - the bubble goes
                // and the row shuts; the step plays again from the start next time.
                tip.dismiss()
                if (state.targetValue != Reveal.CLOSED) close()
            }
        }
    }

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
                            if (dragRight != null) Reveal.SLID_RIGHT at distance
                            if (dragLeft != null) Reveal.SLID_LEFT at -distance
                        }
                    )
                }
            }
            // The same actions without the gesture, for TalkBack.
            .semantics {
                customActions = listOfNotNull(dragRight, dragLeft).map { action ->
                    CustomAccessibilityAction(action.label) { action.onClick(); true }
                }
            },
    ) {
        if (open) {
            val left = x < 0f
            val action = if (left) dragLeft else dragRight
            if (action != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(shape)
                        .background(
                            if (action.danger) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = if (left) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft,
                ) {
                    RevealedAction(
                        action = action,
                        width = with(density) { reveal.toDp() },
                        onClick = {
                            action.onClick()
                            close()
                        },
                    )
                }
            }
        }
        if (tour != null && step != null) {
            // What the bubble points at: the strip the step uncovers. It draws nothing and
            // takes no touches; the row slides over it.
            Box(
                Modifier.matchParentSize(),
                contentAlignment = if (step.way == SwipeWay.LEFT) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft,
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                    tooltip = {
                        RichTooltip(
                            title = { Text(stringResource(step.title)) },
                            action = {
                                TextButton(onClick = { tip.dismiss() }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(if (tour.last) R.string.action_got_it else R.string.action_next))
                                }
                            },
                            caretShape = TooltipDefaults.caretShape(),
                        ) { Text(stringResource(step.text)) }
                    },
                    state = tip,
                    focusable = true,
                    enableUserInput = false,
                    hasAction = true,
                ) {
                    Box(Modifier.width(with(density) { reveal.toDp() }).fillMaxHeight())
                }
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
 * A record row: dragged right it archives (or brings back from the archive), dragged left
 * it removes.
 */
@Composable
fun SwipeableTxnRow(
    archived: Boolean,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes removeLabel: Int = R.string.row_remove,
    tour: SwipeTour? = null,
    content: @Composable () -> Unit,
) {
    SwipeRow(
        modifier = modifier,
        dragRight = SwipeAction(
            label = stringResource(if (archived) R.string.row_unarchive else R.string.row_archive),
            icon = if (archived) R.drawable.ic_sym_unarchive else R.drawable.ic_sym_archive,
            danger = false,
            onClick = onArchive,
        ),
        dragLeft = SwipeAction(
            label = stringResource(removeLabel),
            icon = R.drawable.ic_sym_delete,
            danger = true,
            onClick = onRemove,
        ),
        tour = tour,
        content = content,
    )
}

/**
 * The action itself. It fills the strip the swipe opened, so the whole revealed area is
 * the target and the gesture does not have to be finished accurately.
 */
@Composable
private fun RevealedAction(action: SwipeAction, width: Dp, onClick: () -> Unit) {
    val tint = if (action.danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(action.icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(action.label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** How long a tour waits for its screen to settle, then how long its row takes to open. */
private const val TOUR_PAUSE_MS = 700L
private const val TOUR_SLIDE_MS = 650

/**
 * A first look at what a list's swipes uncover, played once on one row of it: the row
 * opens by itself, a bubble says what the uncovered button does, and dismissing the bubble
 * closes the row and goes on to the next [steps]. After the last, [onDone] records that it
 * has been seen.
 */
@Stable
class SwipeTour(private val steps: List<Step>, private val onDone: () -> Unit) {

    /** One way the row slides, and what the bubble says about the button it uncovers. */
    class Step(val way: SwipeWay, @param:StringRes val title: Int, @param:StringRes val text: Int)

    private var index by mutableIntStateOf(0)

    /** The step to show, or null once every step has been shown. */
    val current: Step? get() = steps.getOrNull(index)

    /** Whether [current] is the last step, so its bubble says "Got it" rather than "Next". */
    val last: Boolean get() = index == steps.lastIndex

    internal fun next() {
        index++
        if (index == steps.size) onDone()
    }
}

/** A tour of [steps], or none at all when it has [seen] before. */
@Composable
fun rememberSwipeTour(seen: Boolean, steps: List<SwipeTour.Step>, onDone: () -> Unit): SwipeTour? =
    remember { if (seen) null else SwipeTour(steps, onDone) }
