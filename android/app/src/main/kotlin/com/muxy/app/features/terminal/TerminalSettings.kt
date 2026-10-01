package com.muxy.app.features.terminal

import com.muxy.app.persistence.settings.AppSettings

data class TerminalSettings(
    val useNerdFont: Boolean = true,
    val autoFocus: Boolean = false,
) {
    companion object {
        fun from(settings: AppSettings): TerminalSettings = TerminalSettings(settings.useNerdFont, settings.autoFocusTerminal)
    }
}
