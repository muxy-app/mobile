package com.muxy.app.features.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.rgbColor
import com.muxy.app.features.projects.ProjectIconColor

@Composable
internal fun tabsProjectColor(project: TabsProject): Color {
    val isDark = LocalAppTheme.current.isDark
    return remember(project.id, project.color, isDark) {
        val index =
            project.id.toByteArray(Charsets.UTF_8).fold(0) { value, byte ->
                (value * 31 + (byte.toInt() and 0xFF)) % fallbackProjectColors.size
            }
        rgbColor(ProjectIconColor.rgb(project.color, isDark) ?: fallbackProjectColors[index])
    }
}

private val fallbackProjectColors = listOf(0xC370D3, 0x4796F0, 0x34D399, 0xE0AF68, 0x22D3EE, 0xEC4899)
