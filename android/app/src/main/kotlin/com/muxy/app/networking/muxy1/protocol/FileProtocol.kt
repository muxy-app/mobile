@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.networking.muxy1.protocol

import com.muxy.app.core.serialization.UuidSerializer
import com.muxy.app.models.FileEncoding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
data class FileChangedEvent(
    @SerialName("projectID") val projectId: UUID,
    @SerialName("worktreeID") val worktreeId: UUID? = null,
    val paths: List<String>,
    val truncated: Boolean,
)

@Serializable
data class FilePathParams(
    @SerialName("projectID") val projectId: String,
    val path: String,
)

@Serializable
data class FileReadParams(
    @SerialName("projectID") val projectId: String,
    val path: String,
    val encoding: FileEncoding,
)

@Serializable
data class FileWriteParams(
    @SerialName("projectID") val projectId: String,
    val path: String,
    val contents: String,
    val encoding: FileEncoding,
)

@Serializable
data class FileRenameParams(
    @SerialName("projectID") val projectId: String,
    val path: String,
    val newName: String,
)

@Serializable
data class FileMoveParams(
    @SerialName("projectID") val projectId: String,
    val paths: List<String>,
    val into: String,
)

@Serializable
data class FileDeleteParams(
    @SerialName("projectID") val projectId: String,
    val paths: List<String>,
)
