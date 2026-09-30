package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Connections : AppRoute

    @Serializable
    data object Settings : AppRoute
}
