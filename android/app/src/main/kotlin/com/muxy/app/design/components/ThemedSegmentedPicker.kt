package com.muxy.app.design.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muxy.app.design.LocalAppTheme

private const val DISABLED_ALPHA = 0.38f

@Composable
fun <T> ThemedSegmentedPicker(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalAppTheme.current
    val colors =
        SegmentedButtonDefaults.colors(
            activeContainerColor = theme.accent,
            activeContentColor = theme.onAccent,
            activeBorderColor = theme.accent,
            inactiveContainerColor = theme.tertiaryGroupedBackground,
            inactiveContentColor = theme.foreground,
            inactiveBorderColor = theme.separator,
            disabledActiveContainerColor = theme.accent.copy(alpha = DISABLED_ALPHA),
            disabledActiveContentColor = theme.onAccent,
            disabledActiveBorderColor = theme.accent.copy(alpha = DISABLED_ALPHA),
            disabledInactiveContainerColor = theme.tertiaryGroupedBackground,
            disabledInactiveContentColor = theme.secondaryForeground,
            disabledInactiveBorderColor = theme.separator,
        )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                colors = colors,
                icon = {},
                label = { Text(label(option), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
            )
        }
    }
}
