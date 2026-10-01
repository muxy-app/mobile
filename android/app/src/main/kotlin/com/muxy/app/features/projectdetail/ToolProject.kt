@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.features.projectdetail

import com.muxy.app.core.serialization.UuidSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
sealed interface ToolProject {
    val connectionId: UUID

    @Serializable
    data class Device(
        override val connectionId: UUID,
        val projectId: UUID,
        val name: String,
    ) : ToolProject

    @Serializable
    data class Server(
        override val connectionId: UUID,
        val serverId: String,
        val projectId: String,
    ) : ToolProject
}
