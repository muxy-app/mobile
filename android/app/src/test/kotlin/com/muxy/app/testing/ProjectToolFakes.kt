package com.muxy.app.testing

import com.muxy.app.features.demo.DemoBackend
import com.muxy.app.features.files.FileBackend
import com.muxy.app.features.files.FileBackendEvent
import com.muxy.app.features.files.RemoteFilePath
import com.muxy.app.models.FileScope
import com.muxy.app.models.RemoteFileEntry
import com.muxy.app.models.RemoteFileStat
import com.muxy.app.models.RemoteTextFile
import com.muxy.app.models.Worktree
import com.muxy.app.networking.muxy1.ProjectChannel
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.RawTagged
import com.muxy.app.persistence.worktrees.WorktreeCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertTrue
import java.util.UUID

val DEMO_PROJECT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000201")
val DEMO_WEB_PROJECT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000202")

class FakeFileBackend : FileBackend {
    override val connected = MutableStateFlow(true)
    override val events = MutableSharedFlow<FileBackendEvent>(extraBufferCapacity = 64)
    var scope = FileScope()
    val calls = mutableListOf<String>()
    val texts = mutableMapOf("README.md" to "original")
    val directories = mutableSetOf("", "Sources")
    var listing = listOf(fileEntry("README.md"), fileEntry("Sources", true))
    var size: Long? = null
    var onList: suspend (String) -> List<RemoteFileEntry> = { listing }
    var onRead: suspend (String) -> String = { texts.getValue(it) }
    var onWrite: suspend () -> Unit = {}
    var onRename: suspend () -> Unit = {}

    override suspend fun currentScope(): FileScope = scope

    override suspend fun list(path: String): List<RemoteFileEntry> {
        calls += "list:$path"
        return onList(path)
    }

    override suspend fun stat(path: String): RemoteFileStat {
        calls += "stat:$path"
        return RemoteFileStat(
            RemoteFilePath.name(path),
            path,
            path in directories,
            size ?: texts[path]
                .orEmpty()
                .toByteArray()
                .size
                .toLong(),
        )
    }

    override suspend fun readText(path: String): RemoteTextFile {
        calls += "read:$path"
        val text = onRead(path)
        return RemoteTextFile(path, text, text.toByteArray().size.toLong())
    }

    override suspend fun readData(path: String): ByteArray {
        calls += "bytes:$path"
        return texts.getValue(path).toByteArray()
    }

    override suspend fun writeText(
        text: String,
        path: String,
        scope: FileScope,
    ) {
        calls += "write:$path"
        onWrite()
        texts[path] = text
    }

    override suspend fun createDirectory(
        path: String,
        scope: FileScope,
    ): String {
        calls += "mkdir:$path"
        directories += path
        return path
    }

    override suspend fun rename(
        path: String,
        name: String,
        scope: FileScope,
    ): String {
        calls += "rename:$path:$name"
        onRename()
        val destination = RemoteFilePath.join(RemoteFilePath.parent(path), name)
        texts.remove(path)?.let { texts[destination] = it }
        return destination
    }

    override suspend fun move(
        paths: List<String>,
        directory: String,
        scope: FileScope,
    ) {
        calls += "move:${paths.joinToString()}:$directory"
    }

    override suspend fun delete(
        paths: List<String>,
        scope: FileScope,
    ) {
        calls += "delete:${paths.joinToString()}"
        paths.forEach(texts::remove)
    }
}

fun fileEntry(
    path: String,
    directory: Boolean = false,
) = RemoteFileEntry(RemoteFilePath.name(path), path, directory, false)

class RecordingProjectChannel(
    var handle: suspend (Method, JsonElement?) -> RawTagged,
) : ProjectChannel {
    override val connected = MutableStateFlow(true)
    override val events = MutableSharedFlow<EventEnvelope>(extraBufferCapacity = 64)
    val calls = mutableListOf<Pair<Method, JsonElement?>>()

    override suspend fun request(
        method: Method,
        params: JsonElement?,
    ): RawTagged {
        calls += method to params
        return handle(method, params)
    }
}

class DemoProjectChannel : ProjectChannel {
    val backend = DemoBackend()
    override val connected = MutableStateFlow(true)
    override val events = MutableSharedFlow<EventEnvelope>(extraBufferCapacity = 64)

    override suspend fun request(
        method: Method,
        params: JsonElement?,
    ): RawTagged {
        val reply = backend.handle(method, params)
        reply.events.forEach { events.emit(it) }
        return reply.result
    }
}

class InMemoryWorktreeCache : WorktreeCache {
    private val values = mutableMapOf<Pair<UUID, UUID>, List<Worktree>>()

    override suspend fun load(
        connectionId: UUID,
        projectId: UUID,
    ): List<Worktree>? = values[connectionId to projectId]

    override suspend fun save(
        worktrees: List<Worktree>,
        connectionId: UUID,
        projectId: UUID,
    ) {
        values[connectionId to projectId] = worktrees
    }
}

suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit): T {
    val error = runCatching { block() }.exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName}, got $error", error is T)
    return error as T
}
