package com.alyaqdhan.riyal.ui.nav

import android.animation.Animator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.transition.Slide
import androidx.transition.TransitionValues

/** Which way the tabs are moving: set as a switch starts, read as its slide is built. */
internal object TabMotion {
    /** Towards a tab further along the toolbar. */
    var forward: Boolean = true

    /** Off for the tab the app opens on, and for a screen rebuilt in place: they simply appear. */
    var armed: Boolean = false
}

/**
 * A tab sliding the whole width of the screen, the way pages of one thing move: a tab
 * further along the toolbar comes in from the end and the one it replaces leaves by the
 * start, and the other way going back. Start and end follow the language, as the
 * toolbar's order does.
 *
 * One instance per fragment serves all four of its moments - entering, leaving, coming
 * back and being returned from - because which edge it uses is only known when a switch
 * begins, so it is read then rather than fixed when the fragment is made.
 */
class TabSlide : Slide() {

    init {
        duration = DURATION
        // Material's standard easing: quick to start, settling gently, no overshoot.
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    }

    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator? {
        if (!TabMotion.armed) return null
        slideEdge = if (TabMotion.forward) Gravity.END else Gravity.START
        return super.onAppear(sceneRoot, view, startValues, endValues)
    }

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator? {
        if (!TabMotion.armed) return null
        slideEdge = if (TabMotion.forward) Gravity.START else Gravity.END
        return super.onDisappear(sceneRoot, view, startValues, endValues)
    }

    private companion object {
        const val DURATION = 300L
    }
}
