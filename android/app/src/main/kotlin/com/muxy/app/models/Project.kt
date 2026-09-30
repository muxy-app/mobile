@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.models

import com.muxy.app.core.serialization.UuidSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
data class Project(
    val id: UUID,
    val name: String,
    val path: String,
    val sortOrder: Double,
    val createdAt: String,
    val icon: String? = null,
    val logo: String? = null,
    val iconColor: String? = null,
    val preferredWorktreeParentPath: String? = null,
    val worktreesEnabled: Boolean? = null,
    val workspaceKind: String? = null,
    @SerialName("workspaceID")
    val workspaceId: UUID? = null,
    val workspaceName: String? = null,
)

data class ProjectWorkspace(
    val id: UUID,
    val name: String,
)

data class Pairing(
    val clientId: String,
    val deviceName: String,
)
