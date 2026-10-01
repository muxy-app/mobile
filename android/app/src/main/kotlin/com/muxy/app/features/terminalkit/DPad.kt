package com.muxy.app.features.terminalkit

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class DPadDirection(
    val key: TerminalKey,
    val title: String,
    val unitX: Float,
    val unitY: Float,
) {
    UP(TerminalKey.Up, "Up", 0f, -1f),
    DOWN(TerminalKey.Down, "Down", 0f, 1f),
    LEFT(TerminalKey.Left, "Left", -1f, 0f),
    RIGHT(TerminalKey.Right, "Right", 1f, 0f),
    ;

    companion object {
        fun of(
            dx: Float,
            dy: Float,
            deadZone: Float,
        ): DPadDirection? {
            if (hypot(dx, dy) <= deadZone) return null
            if (abs(dx) > abs(dy)) return if (dx > 0f) RIGHT else LEFT
            return if (dy > 0f) DOWN else UP
        }
    }
}

object DPadTiming {
    const val INITIAL_DELAY_MILLIS = 300L
    const val REPEAT_INTERVAL_MILLIS = 60L
}

@Composable
fun DPadControl(
    tint: Color,
    onDirection: (TerminalKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val deadZone = with(density) { DEAD_ZONE.toPx() }
    val maxReach = with(density) { ((OUTER_SIZE - THUMB_SIZE) / 2 - THUMB_INSET).toPx() }
    val currentOnDirection by rememberUpdatedState(onDirection)
    var direction by remember { mutableStateOf<DPadDirection?>(null) }
    val thumb by animateOffsetAsState(
        targetValue = direction?.let { Offset(it.unitX * maxReach, it.unitY * maxReach) } ?: Offset.Zero,
        animationSpec = spring(dampingRatio = THUMB_DAMPING, stiffness = Spring.StiffnessHigh),
        label = "dpad",
    )
    LaunchedEffect(direction) {
        val active = direction ?: return@LaunchedEffect
        currentOnDirection(active.key)
        delay(DPadTiming.INITIAL_DELAY_MILLIS)
        while (true) {
            currentOnDirection(active.key)
            delay(DPadTiming.REPEAT_INTERVAL_MILLIS)
        }
    }
    Box(
        modifier =
            modifier
                .size(OUTER_SIZE)
                .clip(CircleShape)
                .background(tint.copy(alpha = OUTER_ALPHA))
                .semantics {
                    contentDescription = ACCESSIBILITY_LABEL
                    customActions =
                        DPadDirection.entries.map { entry ->
                            CustomAccessibilityAction(entry.title) {
                                currentOnDirection(entry.key)
                                true
                            }
                        }
                }.pointerInput(deadZone) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            val delta = change.position - down.position
                            val next = DPadDirection.of(delta.x, delta.y, deadZone)
                            if (next != direction) direction = next
                        }
                        direction = null
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(thumb.x.roundToInt(), thumb.y.roundToInt()) }
                    .size(THUMB_SIZE)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = THUMB_ALPHA)),
        )
    }
}

private val OUTER_SIZE = 48.dp
private val THUMB_SIZE = 18.dp
private val THUMB_INSET = 2.dp
private val DEAD_ZONE = 5.dp
private const val OUTER_ALPHA = 0.12f
private const val THUMB_ALPHA = 0.55f
private const val THUMB_DAMPING = 0.8f
private const val ACCESSIBILITY_LABEL = "Arrow Keys"
