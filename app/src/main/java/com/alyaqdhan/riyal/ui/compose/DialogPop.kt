package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val arrive = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
private val depart = tween<Float>(durationMillis = 120, easing = FastOutLinearInEasing)

/**
 * How every dialog comes and goes: in with a small spring, out faster than it came,
 * because by the time it closes the user is done with it. The Add form had it first and
 * the owner asked for it on every dialog.
 *
 * A dialog puts [popIn] on its modifier and sends every way of closing - its buttons,
 * back, a tap outside - through [leave], so the exit plays before the caller removes it.
 * Only what closes the dialog goes through [leave]; a button that keeps it open calls its
 * action directly.
 */
@Stable
class DialogPop internal constructor(private val scope: CoroutineScope) {
    internal val progress = Animatable(0f)
    private var leaving = false

    fun leave(then: () -> Unit) {
        if (leaving) return
        leaving = true
        scope.launch {
            progress.animateTo(0f, depart)
            then()
            // Whoever closed it removes it, which cancels this. Still here a couple of
            // frames on means it was kept open after all, so it comes back rather than
            // sitting there invisible over the screen.
            withFrameNanos {}
            withFrameNanos {}
            leaving = false
            progress.animateTo(1f, arrive)
        }
    }
}

@Composable
fun rememberDialogPop(): DialogPop {
    val scope = rememberCoroutineScope()
    val pop = remember { DialogPop(scope) }
    LaunchedEffect(pop) { pop.progress.animateTo(1f, arrive) }
    return pop
}

fun Modifier.popIn(pop: DialogPop): Modifier = graphicsLayer {
    val p = pop.progress.value
    scaleX = 0.85f + 0.15f * p
    scaleY = 0.85f + 0.15f * p
    alpha = p.coerceIn(0f, 1f)
}
