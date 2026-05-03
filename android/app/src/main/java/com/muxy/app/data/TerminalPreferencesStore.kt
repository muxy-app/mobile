package com.muxy.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TerminalPreferences(
    val useNerdFont: Boolean = true,
    val fontSize: Int = 13,
) {
    companion object {
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 24
    }
}

class TerminalPreferencesStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<TerminalPreferences> = _flow.asStateFlow()

    private fun load(): TerminalPreferences {
        val useNerd = if (prefs.contains(KEY_USE_NERD_FONT)) {
            prefs.getBoolean(KEY_USE_NERD_FONT, true)
        } else true
        val size = prefs.getInt(KEY_FONT_SIZE, 13)
            .coerceIn(TerminalPreferences.MIN_FONT_SIZE, TerminalPreferences.MAX_FONT_SIZE)
        return TerminalPreferences(useNerdFont = useNerd, fontSize = size)
    }

    fun setUseNerdFont(value: Boolean) {
        prefs.edit().putBoolean(KEY_USE_NERD_FONT, value).apply()
        _flow.value = _flow.value.copy(useNerdFont = value)
    }

    fun setFontSize(value: Int) {
        val clamped = value.coerceIn(TerminalPreferences.MIN_FONT_SIZE, TerminalPreferences.MAX_FONT_SIZE)
        prefs.edit().putInt(KEY_FONT_SIZE, clamped).apply()
        _flow.value = _flow.value.copy(fontSize = clamped)
    }

    companion object {
        private const val FILE_NAME = "muxy_terminal_prefs"
        private const val KEY_USE_NERD_FONT = "use_nerd_font"
        private const val KEY_FONT_SIZE = "terminal_font_size"
    }
}
