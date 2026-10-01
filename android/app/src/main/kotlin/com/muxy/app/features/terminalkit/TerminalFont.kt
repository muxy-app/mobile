package com.muxy.app.features.terminalkit

import android.content.res.Resources
import android.graphics.Typeface
import com.muxy.app.R

class TerminalTypefaces(
    val normal: Typeface,
    val bold: Typeface,
    val italic: Typeface,
    val boldItalic: Typeface,
)

object TerminalFont {
    const val SIZE_DP = 13f

    private var nerdFont: TerminalTypefaces? = null

    private val systemMonospace: TerminalTypefaces by lazy {
        val bold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        TerminalTypefaces(normal = Typeface.MONOSPACE, bold = bold, italic = Typeface.MONOSPACE, boldItalic = bold)
    }

    @Synchronized
    fun typefaces(
        resources: Resources,
        useNerdFont: Boolean,
    ): TerminalTypefaces {
        if (!useNerdFont) return systemMonospace
        return nerdFont ?: loadNerdFont(resources).also { nerdFont = it }
    }

    private fun loadNerdFont(resources: Resources): TerminalTypefaces =
        TerminalTypefaces(
            normal = resources.getFont(R.font.caskaydia_mono_regular),
            bold = resources.getFont(R.font.caskaydia_mono_bold),
            italic = resources.getFont(R.font.caskaydia_mono_italic),
            boldItalic = resources.getFont(R.font.caskaydia_mono_bold_italic),
        )
}
