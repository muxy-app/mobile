package com.muxy.app.features.files

import com.muxy.app.models.FileChange
import com.muxy.app.models.FileScope
import com.muxy.app.models.RemoteFileEntry
import com.muxy.app.models.RemoteFileStat
import com.muxy.app.models.RemoteTextFile
import kotlinx.coroutines.flow.Flow

interface FileBackend {
    val connected: Flow<Boolean>
    val events: Flow<FileBackendEvent>

    suspend fun currentScope(): FileScope

    suspend fun list(path: String): List<RemoteFileEntry>

    suspend fun stat(path: String): RemoteFileStat

    suspend fun readText(path: String): RemoteTextFile

    suspend fun readData(path: String): ByteArray

    suspend fun writeText(
        text: String,
        path: String,
        scope: FileScope,
    )

    suspend fun createDirectory(
        path: String,
        scope: FileScope,
    ): String

    suspend fun rename(
        path: String,
        name: String,
        scope: FileScope,
    ): String

    suspend fun move(
        paths: List<String>,
        directory: String,
        scope: FileScope,
    )

    suspend fun delete(
        paths: List<String>,
        scope: FileScope,
    )
}

sealed interface FileBackendEvent {
    data class ScopeChanged(
        val scope: FileScope,
    ) : FileBackendEvent

    data class FilesChanged(
        val change: FileChange,
    ) : FileBackendEvent
}
