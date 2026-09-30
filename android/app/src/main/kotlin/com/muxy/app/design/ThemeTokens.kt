package com.muxy.app.design

import kotlin.math.abs

data class ThemeTokens(
    val isDark: Boolean,
    val background: ThemeColor,
    val secondaryBackground: ThemeColor,
    val groupedBackground: ThemeColor,
    val secondaryGroupedBackground: ThemeColor,
    val separator: ThemeColor,
    val foreground: ThemeColor,
    val secondaryForeground: ThemeColor,
    val accent: ThemeColor,
    val onAccent: ThemeColor,
    val red: ThemeColor,
    val green: ThemeColor,
    val yellow: ThemeColor,
    val cyan: ThemeColor,
) {
    companion object {
        const val MINIMUM_CONTRAST = 4.5

        private const val SECONDARY_EMPHASIS = 0.6
        private val darkStep = LightnessStep(total = 10.0, rowShare = 0.6, separator = 12.0)
        private val lightStep = LightnessStep(total = 7.0, rowShare = 0.4, separator = 14.0)

        fun from(palette: ThemePalette): ThemeTokens {
            val background = ThemeColor(palette.background)
            val text = ThemeColor(palette.foreground)
            val isDark = background.luminance < text.luminance
            val step = if (isDark) darkStep else lightStep
            val raiseTarget = if (isDark) text else ThemeColor.WHITE
            val recedeTarget = if (isDark) ThemeColor.BLACK else text

            val lift = minOf(step.total * step.rowShare, abs(raiseTarget.lightness - background.lightness))
            val rows = background.shiftingLightness(lift, raiseTarget)
            val recession = minOf(step.total - lift, abs(background.lightness - recedeTarget.lightness))
            val canvas = background.shiftingLightness(recession, recedeTarget)

            val layers = listOf(background, canvas, rows)
            val extreme = if (isDark) ThemeColor.WHITE else ThemeColor.BLACK
            val foreground = text.ensuringContrast(MINIMUM_CONTRAST, layers, listOf(extreme))
            val legible: (ThemeColor) -> ThemeColor = {
                it.ensuringContrast(MINIMUM_CONTRAST, layers, listOf(foreground, extreme))
            }
            val accent = legible(ansiColor(4, palette))

            return ThemeTokens(
                isDark = isDark,
                background = background,
                secondaryBackground = if (isDark) rows else canvas,
                groupedBackground = canvas,
                secondaryGroupedBackground = rows,
                separator = rows.shiftingLightness(step.separator, text),
                foreground = foreground,
                secondaryForeground =
                    background
                        .mixed(foreground, SECONDARY_EMPHASIS)
                        .ensuringContrast(MINIMUM_CONTRAST, layers, listOf(foreground)),
                accent = accent,
                onAccent = onAccent(accent, background),
                red = legible(ansiColor(1, palette)),
                green = legible(ansiColor(2, palette)),
                yellow = legible(ansiColor(3, palette)),
                cyan = legible(ansiColor(6, palette)),
            )
        }

        private fun onAccent(
            accent: ThemeColor,
            background: ThemeColor,
        ): ThemeColor {
            if (accent.contrast(background) >= MINIMUM_CONTRAST) return background
            return if (accent.contrast(ThemeColor.WHITE) >= accent.contrast(ThemeColor.BLACK)) {
                ThemeColor.WHITE
            } else {
                ThemeColor.BLACK
            }
        }

        private fun ansiColor(
            index: Int,
            palette: ThemePalette,
        ): ThemeColor = ThemeColor(palette.ansi.getOrNull(index) ?: ThemeCatalog.muxy.ansi[index])
    }

    private data class LightnessStep(
        val total: Double,
        val rowShare: Double,
        val separator: Double,
    )
}
