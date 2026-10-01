package com.muxy.app.features.terminalkit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.muxy.app.R

interface TerminalAccessoryActions {
    fun key(key: TerminalKey)

    fun text(text: String)

    fun paste()

    fun copy()

    fun toggleModifier()

    fun selectModifier(modifier: TerminalModifier)

    fun toggleKeyboard()
}

object TerminalAccessoryBarDefaults {
    val height = 64.dp
}

@Composable
fun TerminalAccessoryBar(
    sticky: StickyModifier,
    canCopy: Boolean,
    keyboardVisible: Boolean,
    background: Color,
    foreground: Color,
    actions: TerminalAccessoryActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TerminalAccessoryBarDefaults.height)
                .background(background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KeyPill(foreground) {
            TextKey("esc", foreground) { actions.key(TerminalKey.Escape) }
            ModifierKey(sticky, foreground, actions::toggleModifier, actions::selectModifier)
            TextKey("tab", foreground) { actions.key(TerminalKey.Tab) }
            IconKey(R.drawable.ic_content_paste, PASTE, foreground, enabled = true, onClick = actions::paste)
            IconKey(R.drawable.ic_content_copy, COPY, foreground, enabled = canCopy, onClick = actions::copy)
            SYMBOLS.forEach { symbol -> TextKey(symbol, foreground) { actions.text(symbol) } }
        }
        IconKey(
            icon = if (keyboardVisible) R.drawable.ic_keyboard_hide else R.drawable.ic_keyboard,
            label = if (keyboardVisible) HIDE_KEYBOARD else SHOW_KEYBOARD,
            foreground = foreground,
            enabled = true,
            modifier = Modifier.clip(CircleShape).background(foreground.copy(alpha = CONTROL_ALPHA)),
            onClick = actions::toggleKeyboard,
        )
        DPadControl(tint = foreground, onDirection = actions::key)
    }
}

@Composable
private fun RowScope.KeyPill(
    foreground: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(KEY_SIZE)
                .clip(RoundedCornerShape(percent = 50))
                .background(foreground.copy(alpha = CONTROL_ALPHA)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun TextKey(
    title: String,
    foreground: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .defaultMinSize(minWidth = KEY_SIZE, minHeight = KEY_SIZE)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title, color = foreground, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = keyFontSize())
    }
}

@Composable
private fun IconKey(
    @DrawableRes icon: Int,
    label: String,
    foreground: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(KEY_SIZE)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = null, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon), contentDescription = label, tint = foreground, modifier = Modifier.size(ICON_SIZE))
    }
}

@Composable
internal fun keyFontSize(sizeDp: Int = KEY_TEXT_SIZE): TextUnit = with(LocalDensity.current) { sizeDp.dp.toSp() }

private const val PASTE = "Paste"
private const val COPY = "Copy"
private const val HIDE_KEYBOARD = "Hide Keyboard"
private const val SHOW_KEYBOARD = "Show Keyboard"
private const val CONTROL_ALPHA = 0.1f
private const val DISABLED_ALPHA = 0.45f
private const val KEY_TEXT_SIZE = 14
private val SYMBOLS = listOf("~", "|", "/", "-")
private val KEY_SIZE = 48.dp
private val ICON_SIZE = 20.dp
