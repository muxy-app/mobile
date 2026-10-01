@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.files

import com.muxy.app.features.demo.DemoBackend
import com.muxy.app.models.FileEncoding
import com.muxy.app.models.FileScope
import com.muxy.app.models.RemoteFileContent
import com.muxy.app.models.Workspace
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.FileChangedEvent
import com.muxy.app.networking.muxy1.protocol.GetWorkspaceParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.RawTagged
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.testing.DEMO_PROJECT_ID
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.RecordingProjectChannel
import com.muxy.app.testing.expectFailure
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ChannelFileBackendTest {
    @get:Rule val main = MainDispatcherRule()
    private val demo = DemoBackend()
    private val channel = RecordingProjectChannel { method, params -> demo.handle(method, params).result }
    private val backend = ChannelFileBackend(DEMO_PROJECT_ID, channel)

    @Test
    fun everyMutationChecksCurrentWorkspaceBeforeWriting() =
        runTest {
            val scope = backend.currentScope()
            channel.calls.clear()
            backend.writeText("hello", "new.txt", scope)
            backend.createDirectory("New", scope)
            backend.rename("new.txt", "renamed.txt", scope)
            backend.move(listOf("renamed.txt"), "New", scope)
            backend.delete(listOf("New"), scope)
            assertEquals(
                listOf(Method.FILES_WRITE, Method.FILES_MKDIR, Method.FILES_RENAME, Method.FILES_MOVE, Method.FILES_DELETE),
                channel.calls.chunked(2).map { pair ->
                    assertEquals(Method.GET_WORKSPACE, pair[0].first)
                    pair[1].first
                },
            )
        }

    @Test
    fun changedWorkspaceRejectsAllMutationTypes() =
        runTest {
            val stale = FileScope(UUID.randomUUID())
            expectFailure<FileException.WorktreeChanged> { backend.writeText("hello", "a", stale) }
            expectFailure<FileException.WorktreeChanged> { backend.createDirectory("a", stale) }
            expectFailure<FileException.WorktreeChanged> { backend.rename("a", "b", stale) }
            expectFailure<FileException.WorktreeChanged> { backend.move(listOf("a"), "", stale) }
            expectFailure<FileException.WorktreeChanged> { backend.delete(listOf("a"), stale) }
            assertTrue(channel.calls.all { it.first == Method.GET_WORKSPACE })
        }

    @Test
    fun rootPathsAreSentAsDotAndBinaryReadsUseBase64() =
        runTest {
            backend.list("")
            assertEquals(
                ".",
                channel.calls
                    .last()
                    .second!!
                    .jsonObject
                    .getValue("path")
                    .jsonPrimitive.content,
            )
            val bytes = backend.readData("assets/icon.png")
            assertEquals(0x89, bytes.first().toInt() and 0xff)
            assertEquals(
                "base64",
                channel.calls
                    .last()
                    .second!!
                    .jsonObject
                    .getValue("encoding")
                    .jsonPrimitive.content,
            )
            expectFailure<FileException.NotText> { backend.readText("archive.bin") }
        }

    @Test
    fun wrongResultTypeEncodingAndInvalidBase64AreRejected() =
        runTest {
            channel.handle = { _, _ -> RawTagged(ResultType.OK) }
            expectFailure<FileException.UnexpectedResponse> { backend.list("") }
            channel.handle = { _, _ -> tagged(ResultType.FILE_CONTENT, RemoteFileContent("a", "bad", 3, FileEncoding.BASE64)) }
            expectFailure<FileException.UnexpectedResponse> { backend.readText("a") }
            channel.handle = { _, _ -> tagged(ResultType.FILE_CONTENT, RemoteFileContent("a", "!!", 2, FileEncoding.BASE64)) }
            expectFailure<FileException.UnexpectedResponse> { backend.readData("a") }
        }

    @Test
    fun scopeAndFileEventsAreFilteredByProjectAndMalformedEventsAreIgnored() =
        runTest {
            val received = mutableListOf<FileBackendEvent>()
            backgroundScope.launch { backend.events.collect { received += it } }
            runCurrent()
            val scope = backend.currentScope()
            val workspace =
                demo
                    .handle(
                        Method.GET_WORKSPACE,
                        ProtocolJson.encodeToJsonElement(
                            GetWorkspaceParams(DEMO_PROJECT_ID.toString()),
                        ),
                    ).result
                    .decode<Workspace>()
            channel.events.emit(
                EventEnvelope(EventName.WORKSPACE_CHANGED, tagged(EventType.WORKSPACE, workspace.copy(projectId = UUID.randomUUID()))),
            )
            channel.events.emit(
                EventEnvelope(
                    EventName.FILE_CHANGED,
                    tagged(
                        EventType.FILE_CHANGED,
                        FileChangedEvent(UUID.randomUUID(), scope.worktreeId, listOf("other"), false),
                    ),
                ),
            )
            channel.events.emit(EventEnvelope(EventName.FILE_CHANGED, RawTagged(EventType.FILE_CHANGED)))
            channel.events.emit(EventEnvelope(EventName.WORKSPACE_CHANGED, tagged(EventType.WORKSPACE, workspace)))
            channel.events.emit(
                EventEnvelope(
                    EventName.FILE_CHANGED,
                    tagged(
                        EventType.FILE_CHANGED,
                        FileChangedEvent(DEMO_PROJECT_ID, scope.worktreeId, listOf("README.md"), true),
                    ),
                ),
            )
            runCurrent()
            assertEquals(2, received.size)
            assertEquals(FileBackendEvent.ScopeChanged(scope), received[0])
            val changed = received[1] as FileBackendEvent.FilesChanged
            assertEquals(scope, changed.change.scope)
            assertTrue(changed.change.requiresRescan)
            assertEquals(listOf("README.md"), changed.change.paths)
        }

    private inline fun <reified T> tagged(
        type: String,
        value: T,
    ) = RawTagged(type, ProtocolJson.encodeToJsonElement(value))
}
