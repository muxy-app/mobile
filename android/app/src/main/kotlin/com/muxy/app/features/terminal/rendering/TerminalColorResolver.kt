package com.muxy.app.features.terminal.rendering

import com.muxy.app.design.ThemePalette
import com.muxy.app.features.terminal.TerminalColor
import com.muxy.app.features.terminal.TerminalStyle
import kotlin.math.roundToInt

data class ResolvedCellStyle(
    val foreground: Int,
    val background: Int,
    val decoration: Int,
    val drawsText: Boolean,
)

class TerminalColorResolver(
    val theme: ThemePalette,
) {
    fun resolve(style: TerminalStyle): ResolvedCellStyle {
        var foreground = rgb(style.foreground, theme.foreground)
        var background = rgb(style.background, theme.background)
        if (style.inverse) {
            val swapped = foreground
            foreground = background
            background = swapped
        }
        if (style.faint) {
            foreground = blend(foreground, background, FAINT_AMOUNT)
        }
        return ResolvedCellStyle(
            foreground = foreground,
            background = background,
            decoration = rgb(style.underlineColor, foreground),
            drawsText = !style.invisible,
        )
    }

    fun rgb(
        color: TerminalColor,
        fallback: Int,
    ): Int =
        when (color) {
            TerminalColor.Default -> fallback
            is TerminalColor.Indexed -> indexed(color.index, fallback)
            is TerminalColor.Rgb -> color.rgb and RGB_MASK
        }

    private fun indexed(
        index: Int,
        fallback: Int,
    ): Int {
        if (index >= ANSI_COLORS) return xterm(index)
        return theme.ansi.getOrNull(index) ?: fallback
    }

    companion object {
        private const val ANSI_COLORS = 16
        private const val CUBE_START = 16
        private const val GRAY_START = 232
        private const val CUBE_SIDE = 6
        private const val FAINT_AMOUNT = 0.5
        private const val RGB_MASK = 0xFFFFFF

        fun xterm(index: Int): Int {
            if (index >= GRAY_START) {
                val gray = 8 + (index - GRAY_START) * 10
                return pack(gray, gray, gray)
            }
            val value = index - CUBE_START
            return pack(level(value / (CUBE_SIDE * CUBE_SIDE)), level((value / CUBE_SIDE) % CUBE_SIDE), level(value % CUBE_SIDE))
        }

        fun blend(
            color: Int,
            target: Int,
            amount: Double,
        ): Int {
            fun channel(shift: Int): Int {
                val from = (color shr shift) and 0xFF
                val to = (target shr shift) and 0xFF
                return (from + (to - from) * amount).roundToInt()
            }
            return pack(channel(16), channel(8), channel(0))
        }

        private fun level(component: Int): Int = if (component == 0) 0 else 55 + component * 40

        private fun pack(
            red: Int,
            green: Int,
            blue: Int,
        ): Int = (red shl 16) or (green shl 8) or blue
    }
}
