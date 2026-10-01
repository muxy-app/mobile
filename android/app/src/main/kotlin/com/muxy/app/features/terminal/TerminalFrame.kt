package com.muxy.app.features.terminal

data class TerminalGridSize(
    val columns: Int,
    val rows: Int,
) {
    fun isUsable(): Boolean = columns >= MINIMUM_COLUMNS && rows >= MINIMUM_ROWS

    companion object {
        const val MINIMUM_COLUMNS = 20
        const val MINIMUM_ROWS = 4
    }
}

data class TerminalCellPosition(
    val row: Int,
    val column: Int,
) : Comparable<TerminalCellPosition> {
    override fun compareTo(other: TerminalCellPosition): Int = compareValuesBy(this, other, { it.row }, { it.column })
}

sealed interface TerminalColor {
    data object Default : TerminalColor

    data class Indexed(
        val index: Int,
    ) : TerminalColor

    data class Rgb(
        val rgb: Int,
    ) : TerminalColor
}

enum class UnderlineStyle {
    NONE,
    SINGLE,
    DOUBLE,
    CURLY,
    DOTTED,
    DASHED,
}

data class TerminalStyle(
    val foreground: TerminalColor = TerminalColor.Default,
    val background: TerminalColor = TerminalColor.Default,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val faint: Boolean = false,
    val underline: UnderlineStyle = UnderlineStyle.NONE,
    val underlineColor: TerminalColor = TerminalColor.Default,
    val strikethrough: Boolean = false,
    val overline: Boolean = false,
    val inverse: Boolean = false,
    val invisible: Boolean = false,
) {
    companion object {
        val PLAIN = TerminalStyle()
    }
}

sealed interface TerminalSpan {
    val text: String
    val width: Int
    val style: TerminalStyle

    data class Run(
        override val text: String,
        override val style: TerminalStyle,
    ) : TerminalSpan {
        override val width: Int
            get() = text.length
    }

    data class Cluster(
        override val text: String,
        override val width: Int,
        override val style: TerminalStyle,
    ) : TerminalSpan
}

data class TerminalLine(
    val spans: List<TerminalSpan>,
) {
    companion object {
        val EMPTY = TerminalLine(emptyList())
    }
}

enum class CursorShape {
    BLOCK,
    BAR,
    UNDERLINE,
    HOLLOW,
}

data class TerminalCursor(
    val row: Int,
    val column: Int,
    val visible: Boolean,
    val shape: CursorShape,
)

data class TerminalModes(
    val mouseTracking: Boolean = false,
    val alternateScroll: Boolean = false,
)

data class TerminalFrame(
    val columns: Int,
    val rows: Int,
    val lines: List<TerminalLine>,
    val cursor: TerminalCursor,
    val modes: TerminalModes,
    val title: String,
    val historyRows: Int,
)
