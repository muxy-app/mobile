package com.muxy.app.design.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.blockingTouches(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
    }
