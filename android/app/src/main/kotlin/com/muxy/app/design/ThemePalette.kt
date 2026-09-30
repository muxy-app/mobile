package com.muxy.app.design

data class ThemePalette(
    val name: String,
    val foreground: Int,
    val background: Int,
    val ansi: List<Int>,
    val cursor: Int,
    val cursorText: Int,
    val selectionBackground: Int,
    val selectionForeground: Int,
) {
    fun replacingBackground(background: Int): ThemePalette = copy(background = background)
}
