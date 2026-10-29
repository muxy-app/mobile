package com.muxy.app.design.components

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.muxy.app.design.LocalAppTheme

private const val DISABLED_ALPHA = 0.38f

@Composable
fun themedSwitchColors(): SwitchColors {
    val theme = LocalAppTheme.current
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = theme.green,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = theme.green,
        uncheckedThumbColor = theme.secondaryForeground,
        uncheckedTrackColor = theme.tertiaryGroupedBackground,
        uncheckedBorderColor = theme.separator,
        uncheckedIconColor = theme.tertiaryGroupedBackground,
        disabledCheckedThumbColor = Color.White.copy(alpha = DISABLED_ALPHA),
        disabledCheckedTrackColor = theme.green.copy(alpha = DISABLED_ALPHA),
        disabledCheckedBorderColor = Color.Transparent,
        disabledUncheckedThumbColor = theme.secondaryForeground.copy(alpha = DISABLED_ALPHA),
        disabledUncheckedTrackColor = theme.tertiaryGroupedBackground.copy(alpha = DISABLED_ALPHA),
        disabledUncheckedBorderColor = theme.separator.copy(alpha = DISABLED_ALPHA),
    )
}

@Composable
fun ThemedToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val theme = LocalAppTheme.current
    ThemedListItem(
        headline = { Text(title, color = if (enabled) theme.foreground else theme.secondaryForeground) },
        supporting = supporting?.let { { Text(it) } },
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = themedSwitchColors()) },
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}
