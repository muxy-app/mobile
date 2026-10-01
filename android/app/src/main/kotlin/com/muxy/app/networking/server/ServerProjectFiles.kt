package com.muxy.app.networking.server

import uniffi.muxy_mobile.FileEntry
import uniffi.muxy_mobile.FileInfo

interface ServerProjectFiles : AutoCloseable {
    suspend fun list(path: String): List<FileEntry>

    suspend fun stat(path: String): FileInfo

    suspend fun readText(path: String): String

    suspend fun readBytes(path: String): ByteArray

    suspend fun writeText(
        path: String,
        text: String,
    ): String

    suspend fun createDirectory(path: String): String

    suspend fun rename(
        path: String,
        name: String,
    ): String

    suspend fun moveFiles(
        paths: List<String>,
        into: String,
    ): List<String>

    suspend fun deleteFiles(paths: List<String>)

    suspend fun watch()

    suspend fun unwatch()
}
