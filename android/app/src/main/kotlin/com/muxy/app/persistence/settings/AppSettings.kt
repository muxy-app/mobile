package com.muxy.app.persistence.settings

data class AppSettings(
    val hasCompletedOnboarding: Boolean = false,
    val themeName: String = DEFAULT_THEME_NAME,
    val useNerdFont: Boolean = true,
    val autoFocusTerminal: Boolean = false,
    val demoMode: Boolean = false,
) {
    companion object {
        const val DEFAULT_THEME_NAME = "Muxy"
    }
}
