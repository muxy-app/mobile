package com.muxy.app.features.terminalkit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme

@Composable
fun ModifierKey(
    sticky: StickyModifier,
    foreground: Color,
    onToggle: () -> Unit,
    onSelect: (TerminalModifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentSticky by rememberUpdatedState(sticky)
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var pickerOpen by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf<TerminalModifier?>(null) }
    var keyCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val rows = remember { mutableStateMapOf<TerminalModifier, Rect>() }
    val textColor = if (sticky.armed) contrasting(foreground) else foreground

    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = KEY_MIN_WIDTH, minHeight = KEY_HEIGHT)
                .onGloballyPositioned { keyCoordinates = it }
                .semantics {
                    role = Role.Button
                    contentDescription = sticky.active.displayName
                    stateDescription = if (sticky.armed) ARMED else NOT_ARMED
                    onClick {
                        currentOnToggle()
                        true
                    }
                }.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var cancelled = false
                        val up =
                            withTimeoutOrNull(PICKER_DELAY_MILLIS) {
                                waitForUpOrCancellation().also { if (it == null) cancelled = true }
                            }
                        if (cancelled) return@awaitEachGesture
                        if (up != null) {
                            up.consume()
                            currentOnToggle()
                            return@awaitEachGesture
                        }
                        pickerOpen = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            val position = keyCoordinates?.takeIf { it.isAttached }?.localToWindow(change.position)
                            hovered = position?.let { hoveredModifier(it, rows, currentSticky.active) }
                            if (!change.pressed) break
                        }
                        val selection = hovered
                        pickerOpen = false
                        hovered = null
                        if (selection == null || selection == currentSticky.active) return@awaitEachGesture
                        currentOnSelect(selection)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .height(KEY_PILL_HEIGHT)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (sticky.armed) foreground else Color.Transparent)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = sticky.active.title,
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = keyFontSize(),
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand_less),
                contentDescription = null,
                tint = textColor.copy(alpha = CHEVRON_ALPHA),
                modifier = Modifier.size(12.dp),
            )
        }
        if (pickerOpen) {
            Popup(popupPositionProvider = AboveAnchor(with(LocalDensity.current) { PICKER_GAP.roundToPx() })) {
                ModifierPicker(sticky.active, hovered) { entry, bounds -> rows[entry] = bounds }
            }
        }
    }
}

@Composable
private fun ModifierPicker(
    active: TerminalModifier,
    hovered: TerminalModifier?,
    onRowPlaced: (TerminalModifier, Rect) -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier =
            Modifier
                .width(PICKER_WIDTH)
                .clip(RoundedCornerShape(PICKER_RADIUS))
                .background(theme.secondaryGroupedBackground)
                .border(BorderStroke(1.dp, theme.separator), RoundedCornerShape(PICKER_RADIUS))
                .padding(vertical = 6.dp),
    ) {
        TerminalModifier.entries.forEach { entry ->
            val disabled = entry == active
            val color = if (disabled) theme.foreground.copy(alpha = DISABLED_ALPHA) else theme.foreground
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(PICKER_ROW_HEIGHT)
                        .onGloballyPositioned { onRowPlaced(entry, it.boundsInWindow()) }
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (entry == hovered) theme.foreground.copy(alpha = HIGHLIGHT_ALPHA) else Color.Transparent)
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(text = entry.glyph, color = color, fontWeight = FontWeight.SemiBold, fontSize = keyFontSize(GLYPH_SIZE))
                Text(text = entry.displayName.lowercase(), color = color, fontFamily = FontFamily.Monospace, fontSize = keyFontSize())
            }
        }
    }
}

private fun hoveredModifier(
    position: Offset,
    rows: Map<TerminalModifier, Rect>,
    active: TerminalModifier,
): TerminalModifier? = rows.entries.firstOrNull { (entry, bounds) -> entry != active && bounds.contains(position) }?.key

private fun contrasting(color: Color): Color {
    val brightness = color.red * RED_WEIGHT + color.green * GREEN_WEIGHT + color.blue * BLUE_WEIGHT
    return if (brightness > HALF) Color.Black else Color.White
}

private class AboveAnchor(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centered = anchorBounds.center.x - popupContentSize.width / 2
        val x = centered.coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

private const val PICKER_DELAY_MILLIS = 300L
private const val ARMED = "On"
private const val NOT_ARMED = "Off"
private const val CHEVRON_ALPHA = 0.6f
private const val DISABLED_ALPHA = 0.4f
private const val HIGHLIGHT_ALPHA = 0.18f
private const val GLYPH_SIZE = 15
private const val HALF = 0.5f
private const val RED_WEIGHT = 0.299f
private const val GREEN_WEIGHT = 0.587f
private const val BLUE_WEIGHT = 0.114f
private val KEY_MIN_WIDTH = 52.dp
private val KEY_HEIGHT = 48.dp
private val KEY_PILL_HEIGHT = 32.dp
private val PICKER_WIDTH = 180.dp
private val PICKER_ROW_HEIGHT = 44.dp
private val PICKER_RADIUS = 18.dp
private val PICKER_GAP = 8.dp
