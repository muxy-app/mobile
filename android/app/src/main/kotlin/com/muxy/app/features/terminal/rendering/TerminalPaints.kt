package com.muxy.app.features.terminal.rendering

import android.graphics.Paint
import android.graphics.Typeface
import com.muxy.app.features.terminal.TerminalStyle
import com.muxy.app.features.terminalkit.TerminalTypefaces

class TerminalPaints private constructor(
    private val runs: List<Paint>,
    private val clusters: List<Paint>,
) {
    fun run(
        style: TerminalStyle,
        color: Int,
    ): Paint = runs[face(style)].also { it.color = color }

    fun cluster(
        style: TerminalStyle,
        color: Int,
    ): Paint = clusters[face(style)].also { it.color = color }

    private fun face(style: TerminalStyle): Int = (if (style.bold) BOLD else 0) + (if (style.italic) ITALIC else 0)

    companion object {
        private const val BOLD = 1
        private const val ITALIC = 2
        private const val NO_LIGATURES = "'liga' 0, 'clig' 0, 'calt' 0"
        private const val ADVANCE_SAMPLE = "M"

        fun base(
            typeface: Typeface,
            textSize: Float,
        ): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.typeface = typeface
                this.textSize = textSize
                fontFeatureSettings = NO_LIGATURES
            }

        fun create(
            typefaces: TerminalTypefaces,
            textSize: Float,
            cellWidth: Float,
        ): TerminalPaints {
            val faces = listOf(typefaces.normal, typefaces.bold, typefaces.italic, typefaces.boldItalic)
            val clusters = faces.map { base(it, textSize) }
            val runs =
                faces.map { typeface ->
                    base(typeface, textSize).apply {
                        letterSpacing = (cellWidth - measureText(ADVANCE_SAMPLE)) / textSize
                    }
                }
            return TerminalPaints(runs, clusters)
        }
    }
}
