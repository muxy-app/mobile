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

private val fallbackProjectColors =
    listOf(
        0xE5484D,
        0xF76B15,
        0xF5A623,
        0xEBCB00,
        0x9BCD1E,
        0x30A46C,
        0x12A594,
        0x05A2C2,
        0x3E63DD,
        0x5B5BD6,
        0x8E4EC6,
        0xD6409F,
    )
