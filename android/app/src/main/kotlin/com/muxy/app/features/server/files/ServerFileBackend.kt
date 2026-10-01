package com.muxy.app.features.server.files

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.features.files.FileBackend
import com.muxy.app.features.files.FileBackendEvent
import com.muxy.app.features.files.FileException
import com.muxy.app.features.server.ServerController
import com.muxy.app.features.server.ServerPhase
import com.muxy.app.features.server.withFiles
import com.muxy.app.models.FileChange
import com.muxy.app.models.FileScope
import com.muxy.app.models.RemoteFileEntry
import com.muxy.app.models.RemoteFileStat
import com.muxy.app.models.RemoteTextFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import uniffi.muxy_mobile.MobileException
import java.nio.charset.CharacterCodingException

class ServerFileBackend(
    private val projectId: String,
    private val server: ServerController,
) : FileBackend {
    override val connected = server.phase.map { it == ServerPhase.Connected }.distinctUntilChanged()
    override val events =
        server.fileWatches.changes(projectId).map {
            FileBackendEvent.FilesChanged(FileChange(FileScope(), it, it.isEmpty()))
        }

    override suspend fun currentScope() = FileScope()

    override suspend fun list(path: String) =
        server.withFiles(projectId) { files ->
            files.list(path).map { RemoteFileEntry(it.name, it.path, it.isDirectory, it.isIgnored) }
        }

    override suspend fun stat(path: String) =
        server.withFiles(projectId) { files ->
            val stat = files.stat(path)
            RemoteFileStat(stat.name, stat.path, stat.isDirectory, stat.size.coerceAtMost(Long.MAX_VALUE.toULong()).toLong())
        }

    override suspend fun readText(path: String): RemoteTextFile =
        server.withFiles(projectId) { files ->
            val text =
                try {
                    files.readText(path)
                } catch (error: MobileException.Server) {
                    val bytes = attempt { files.readBytes(path) }.getOrElse { throw error }
                    withContext(Dispatchers.Default) {
                        try {
                            bytes.decodeToString(throwOnInvalidSequence = true)
                        } catch (invalid: CharacterCodingException) {
                            throw FileException.NotText()
                        }
                    }
                }
            RemoteTextFile(path, text, text.toByteArray().size.toLong())
        }

    override suspend fun readData(path: String) = server.withFiles(projectId) { it.readBytes(path) }

    override suspend fun writeText(
        text: String,
        path: String,
        scope: FileScope,
    ) {
        server.withFiles(projectId) { it.writeText(path, text) }
    }

    override suspend fun createDirectory(
        path: String,
        scope: FileScope,
    ) = server.withFiles(projectId) { it.createDirectory(path) }

    override suspend fun rename(
        path: String,
        name: String,
        scope: FileScope,
    ) = server.withFiles(projectId) { it.rename(path, name) }

    override suspend fun move(
        paths: List<String>,
        directory: String,
        scope: FileScope,
    ) {
        server.withFiles(projectId) { it.moveFiles(paths, directory) }
    }

    override suspend fun delete(
        paths: List<String>,
        scope: FileScope,
    ) {
        server.withFiles(projectId) { it.deleteFiles(paths) }
    }
}
