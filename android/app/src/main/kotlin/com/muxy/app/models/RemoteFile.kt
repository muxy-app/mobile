package com.muxy.app.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RemoteFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isIgnored: Boolean,
)

@Serializable
enum class FileEncoding {
    @SerialName("utf8")
    UTF8,

    @SerialName("base64")
    BASE64,
}

@Serializable
data class RemoteFileContent(
    val path: String,
    val content: String,
    val size: Long,
    val encoding: FileEncoding,
)

@Serializable
data class RemoteFileStat(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
)

data class RemoteTextFile(
    val path: String,
    val text: String,
    val size: Long,
)

data class FileScope(
    val worktreeId: UUID? = null,
)

data class FileChange(
    val scope: FileScope,
    val paths: List<String>,
    val requiresRescan: Boolean,
)

object FileLimits {
    const val MAXIMUM_BYTES = 5 * 1024 * 1024
    const val PREVIEW_CHARACTERS = 200_000
    const val IMAGE_PIXELS = 2048
}
