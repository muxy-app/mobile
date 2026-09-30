@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavKey
import com.muxy.app.core.serialization.UuidSerializer
import com.muxy.app.networking.muxy1.ConnectionFocus
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Connections : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object AddConnection : AppRoute

    @Serializable
    data class Projects(
        val connectionId: UUID,
    ) : AppRoute

    @Serializable
    data class ProjectDetail(
        val connectionId: UUID,
        val projectId: UUID,
        val projectName: String,
    ) : AppRoute
}

fun List<AppRoute>.connectionFocus(): ConnectionFocus =
    when (val top = lastOrNull()) {
        is AppRoute.Projects -> ConnectionFocus.Device(top.connectionId)
        is AppRoute.ProjectDetail -> ConnectionFocus.Device(top.connectionId)
        AppRoute.Connections, null -> ConnectionFocus.None
        AppRoute.AddConnection, AppRoute.Settings -> ConnectionFocus.Hold
    }
