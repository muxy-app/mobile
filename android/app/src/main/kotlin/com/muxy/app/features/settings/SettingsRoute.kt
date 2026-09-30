package com.muxy.app.features.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface SettingsRoute : NavKey {
    @Serializable
    data object Main : SettingsRoute

    @Serializable
    data object Theme : SettingsRoute
}
