package com.muxy.app.features.files

import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.FileChange
import com.muxy.app.models.FileEncoding
import com.muxy.app.models.FileScope
import com.muxy.app.models.RemoteFileContent
import com.muxy.app.models.RemoteFileEntry
import com.muxy.app.models.RemoteFileStat
import com.muxy.app.models.RemoteTextFile
import com.muxy.app.models.Workspace
import com.muxy.app.networking.muxy1.ProjectChannel
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.FileChangedEvent
import com.muxy.app.networking.muxy1.protocol.FileDeleteParams
import com.muxy.app.networking.muxy1.protocol.FileMoveParams
import com.muxy.app.networking.muxy1.protocol.FilePathParams
import com.muxy.app.networking.muxy1.protocol.FileReadParams
import com.muxy.app.networking.muxy1.protocol.FileRenameParams
import com.muxy.app.networking.muxy1.protocol.FileWriteParams
import com.muxy.app.networking.muxy1.protocol.GetWorkspaceParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.UUID

class ChannelFileBackend(
    private val projectId: UUID,
    private val channel: ProjectChannel,
) : FileBackend {
    override val connected = channel.connected
    override val events = channel.events.mapNotNull(::event)

    override suspend fun currentScope(): FileScope {
        try {
            val workspace: Workspace = request(Method.GET_WORKSPACE, GetWorkspaceParams(projectId.uuidString), ResultType.WORKSPACE)
            if (workspace.projectId != projectId) throw FileException.UnexpectedResponse()
            return FileScope(workspace.worktreeId)
        } catch (error: ProtocolException) {
            if (error.code != ErrorCode.NOT_FOUND) throw error
            return FileScope()
        }
    }

    override suspend fun list(path: String): List<RemoteFileEntry> = request(Method.FILES_LIST, pathParams(path), ResultType.FILES)

    override suspend fun stat(path: String): RemoteFileStat = request(Method.FILES_STAT, pathParams(path), ResultType.FILE_STAT)

    override suspend fun readText(path: String): RemoteTextFile {
        try {
            val content = read(path, FileEncoding.UTF8)
            return RemoteTextFile(content.path, content.content, content.size)
        } catch (error: ProtocolException) {
            val message = error.body.message.lowercase()
            if (message.contains("utf-8") || message.contains("utf8")) throw FileException.NotText()
            throw error
        }
    }

    override suspend fun readData(path: String): ByteArray {
        val content = read(path, FileEncoding.BASE64)
        return withContext(Dispatchers.Default) {
            try {
                Base64.getDecoder().decode(content.content)
            } catch (error: IllegalArgumentException) {
                throw FileException.UnexpectedResponse()
            }
        }
    }

    override suspend fun writeText(
        text: String,
        path: String,
        scope: FileScope,
    ) {
        requireScope(scope)
        request<FileWriteParams, List<String>>(
            Method.FILES_WRITE,
            FileWriteParams(projectId.uuidString, path, text, FileEncoding.UTF8),
            ResultType.FILE_PATHS,
        )
    }

    override suspend fun createDirectory(
        path: String,
        scope: FileScope,
    ): String {
        requireScope(scope)
        return singlePath(request(Method.FILES_MKDIR, pathParams(path), ResultType.FILE_PATHS))
    }

    override suspend fun rename(
        path: String,
        name: String,
        scope: FileScope,
    ): String {
        requireScope(scope)
        return singlePath(request(Method.FILES_RENAME, FileRenameParams(projectId.uuidString, path, name), ResultType.FILE_PATHS))
    }

    override suspend fun move(
        paths: List<String>,
        directory: String,
        scope: FileScope,
    ) {
        requireScope(scope)
        request<FileMoveParams, List<String>>(
            Method.FILES_MOVE,
            FileMoveParams(projectId.uuidString, paths, directory.ifEmpty { "." }),
            ResultType.FILE_PATHS,
        )
    }

    override suspend fun delete(
        paths: List<String>,
        scope: FileScope,
    ) {
        requireScope(scope)
        val result = channel.request(Method.FILES_DELETE, FileDeleteParams(projectId.uuidString, paths))
        if (result.type != ResultType.OK) throw FileException.UnexpectedResponse()
    }

    private suspend fun read(
        path: String,
        encoding: FileEncoding,
    ): RemoteFileContent {
        val content: RemoteFileContent =
            request(
                Method.FILES_READ,
                FileReadParams(projectId.uuidString, path, encoding),
                ResultType.FILE_CONTENT,
            )
        if (content.encoding != encoding) throw FileException.UnexpectedResponse()
        return content
    }

    private suspend fun requireScope(scope: FileScope) {
        currentCoroutineContext().ensureActive()
        if (currentScope() != scope) throw FileException.WorktreeChanged()
        currentCoroutineContext().ensureActive()
    }

    private fun pathParams(path: String) = FilePathParams(projectId.uuidString, path.ifEmpty { "." })

    private fun singlePath(paths: List<String>): String = paths.singleOrNull() ?: throw FileException.UnexpectedResponse()

    private suspend inline fun <reified P, reified R> request(
        method: Method,
        params: P,
        type: String,
    ): R {
        val result = channel.request(method, params)
        if (result.type != type) throw FileException.UnexpectedResponse()
        return withContext(Dispatchers.Default) { result.decode<R>() }
    }

    private fun event(envelope: EventEnvelope): FileBackendEvent? {
        val data = envelope.data ?: return null
        try {
            if (envelope.event == EventName.WORKSPACE_CHANGED && data.type == EventType.WORKSPACE) {
                val workspace = data.decode<Workspace>()
                if (workspace.projectId != projectId) return null
                return FileBackendEvent.ScopeChanged(FileScope(workspace.worktreeId))
            }
            if (envelope.event != EventName.FILE_CHANGED || data.type != EventType.FILE_CHANGED) return null
            val change = data.decode<FileChangedEvent>()
            if (change.projectId != projectId) return null
            return FileBackendEvent.FilesChanged(FileChange(FileScope(change.worktreeId), change.paths, change.truncated))
        } catch (error: Exception) {
            Log.files.error("Invalid file event: ${error.javaClass.simpleName}")
            return null
        }
    }
}
