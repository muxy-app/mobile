package com.muxy.app.features.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.rgbColor

@Composable
fun rememberProjectColor(
    id: String,
    token: String?,
): Color {
    val isDark = LocalAppTheme.current.isDark
    return remember(id, token, isDark) { rgbColor(ProjectIconColor.stableRgb(token, isDark, id)) }
}
