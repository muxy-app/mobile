package com.muxy.app.design.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidth {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    val isWide: Boolean
        get() = this != COMPACT

    companion object {
        private val mediumMinWidth = 600.dp
        private val expandedMinWidth = 840.dp

        fun of(width: Dp): WindowWidth =
            when {
                width >= expandedMinWidth -> EXPANDED
                width >= mediumMinWidth -> MEDIUM
                else -> COMPACT
            }
    }
}

@Composable
fun currentWindowWidth(): WindowWidth {
    val width =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    return WindowWidth.of(width)
}
