package com.muxy.app.features.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.ui.NavDisplay

object NavigationTransitions {
    private const val DURATION_MILLIS = 350
    private const val PARALLAX_DIVISOR = 4

    private val slide = tween<IntOffset>(DURATION_MILLIS, easing = FastOutSlowInEasing)

    fun push(): ContentTransform = slideInHorizontally(slide) { it } togetherWith slideOutHorizontally(slide) { -it / PARALLAX_DIVISOR }

    fun pop(): ContentTransform = slideInHorizontally(slide) { -it / PARALLAX_DIVISOR } togetherWith slideOutHorizontally(slide) { it }

    private fun modalDismiss(): ContentTransform = EnterTransition.None togetherWith slideOutVertically(slide) { it }

    val modal: Map<String, Any> =
        NavDisplay.transitionSpec {
            slideInVertically(slide) { it } togetherWith ExitTransition.KeepUntilTransitionsFinished
        } +
            NavDisplay.popTransitionSpec { modalDismiss() } +
            NavDisplay.predictivePopTransitionSpec { modalDismiss() }
}
