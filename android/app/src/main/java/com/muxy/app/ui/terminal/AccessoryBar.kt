package com.muxy.app.ui.terminal

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Sticky single-slot modifier state matching iOS TerminalAccessoryModel. */
class AccessoryState {
    var active: AccessoryModifier by mutableStateOf(AccessoryModifier.CTRL)
    var armed: Boolean by mutableStateOf(false)

    fun toggleArm() { armed = !armed }
    fun selectModifier(m: AccessoryModifier) { active = m; armed = false }
    /** Pop the armed modifier so the next character is transformed exactly once. */
    fun consume(): AccessoryModifier? {
        if (!armed) return null
        armed = false
        return active
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccessoryBar(
    state: AccessoryState,
    foreground: Color,
    background: Color,
    keyboardVisible: Boolean,
    onSendBytes: (ByteArray) -> Unit,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    canCopy: Boolean,
    onToggleKeyboard: () -> Unit,
) {
    val scroll = rememberScrollState()
    val active = state.active

    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(foreground.copy(alpha = 0.10f))
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Key("esc", foreground) { onSendBytes(byteArrayOf(0x1B)) }
            ModifierKey(active = active, armed = state.armed, foreground = foreground, background = background,
                onTap = { state.toggleArm() },
                onSelect = { state.selectModifier(it) })
            Key("tab", foreground) { onSendBytes(byteArrayOf(0x09)) }
            IconKey(Icons.Filled.ContentPaste, foreground, "Paste") { onPaste() }
            IconKey(Icons.Filled.ContentCopy, if (canCopy) foreground else foreground.copy(alpha = 0.4f), "Copy") {
                if (canCopy) onCopy()
            }
            Key("~", foreground) { onSendBytes("~".toByteArray()) }
            Key("|", foreground) { onSendBytes("|".toByteArray()) }
            Key("/", foreground) { onSendBytes("/".toByteArray()) }
            Key("-", foreground) { onSendBytes("-".toByteArray()) }
        }

        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(foreground.copy(alpha = 0.10f))
                .combinedClickable(onClick = onToggleKeyboard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (keyboardVisible) Icons.Filled.KeyboardArrowDown else Icons.Filled.Keyboard,
                contentDescription = if (keyboardVisible) "Hide keyboard" else "Show keyboard",
                tint = foreground,
            )
        }

        DPad(foreground = foreground) { payload -> onSendBytes(payload) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(label: String, foreground: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .widthIn(min = 32.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconKey(icon: androidx.compose.ui.graphics.vector.ImageVector, foreground: Color, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 32.dp, height = 28.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = foreground, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ModifierKey(
    active: AccessoryModifier,
    armed: Boolean,
    foreground: Color,
    background: Color,
    onTap: () -> Unit,
    onSelect: (AccessoryModifier) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val pillBg = if (armed) foreground else Color.Transparent
    val labelColor = if (armed) background else foreground
    Box {
        Row(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(pillBg)
                .combinedClickable(onClick = onTap, onLongClick = { menuOpen = true })
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(active.title, color = labelColor, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = labelColor.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AccessoryModifier.entries.forEach { m ->
                DropdownMenuItem(
                    enabled = m != active,
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.glyph, fontSize = 15.sp, modifier = Modifier.width(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(m.displayName.lowercase(), fontFamily = FontFamily.Monospace)
                        }
                    },
                    onClick = { menuOpen = false; onSelect(m) },
                )
            }
        }
    }
}

@Composable
private fun DPad(foreground: Color, onDirection: (ByteArray) -> Unit) {
    val outerSize = 44.dp
    val thumbSize = 18.dp
    val deadZone = 5f
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    var activeDir by remember { mutableStateOf<DPadDir?>(null) }
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    fun stop() { repeatJob?.cancel(); repeatJob = null }
    fun startRepeat(dir: DPadDir) {
        stop()
        onDirection(dir.payload)
        repeatJob = scope.launch {
            delay(300)
            while (isActive) { onDirection(dir.payload); delay(60) }
        }
    }

    Box(
        Modifier
            .size(outerSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val first = awaitPointerEvent().changes.first()
                        first.consume()
                        thumbOffset = Offset.Zero
                        var c = first
                        while (c.pressed) {
                            val ev = awaitPointerEvent()
                            c = ev.changes.first()
                            c.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val pos = c.position - center
                            val mag = hypot(pos.x.toDouble(), pos.y.toDouble())
                            if (mag < deadZone) {
                                if (activeDir != null) { stop(); activeDir = null }
                                thumbOffset = Offset.Zero
                                continue
                            }
                            val dir = if (abs(pos.x) > abs(pos.y))
                                (if (pos.x > 0) DPadDir.RIGHT else DPadDir.LEFT)
                            else
                                (if (pos.y > 0) DPadDir.DOWN else DPadDir.UP)
                            val maxReach = (size.width - thumbSize.toPx()) / 2f - 2f
                            thumbOffset = Offset(dir.unit.x * maxReach, dir.unit.y * maxReach)
                            if (dir != activeDir) { activeDir = dir; startRepeat(dir) }
                        }
                        stop(); activeDir = null; thumbOffset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(thumbSize)
                .clip(CircleShape)
                .background(foreground.copy(alpha = 0.55f)),
        )
    }
}

private enum class DPadDir(val unit: Offset, val payload: ByteArray) {
    UP(Offset(0f, -1f), byteArrayOf(0x1B, 0x5B, 'A'.code.toByte())),
    DOWN(Offset(0f, 1f), byteArrayOf(0x1B, 0x5B, 'B'.code.toByte())),
    LEFT(Offset(-1f, 0f), byteArrayOf(0x1B, 0x5B, 'D'.code.toByte())),
    RIGHT(Offset(1f, 0f), byteArrayOf(0x1B, 0x5B, 'C'.code.toByte())),
}

fun pasteFromClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context).toString()
}

fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText("muxy-terminal", text))
}
