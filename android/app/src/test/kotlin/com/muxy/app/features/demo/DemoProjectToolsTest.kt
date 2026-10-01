package com.muxy.app.features.demo

import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.FileEncoding
import com.muxy.app.models.FileLimits
import com.muxy.app.models.RemoteFileContent
import com.muxy.app.models.RemoteFileEntry
import com.muxy.app.models.VcsStatus
import com.muxy.app.models.Worktree
import com.muxy.app.networking.muxy1.protocol.FileChangedEvent
import com.muxy.app.networking.muxy1.protocol.FilePathParams
import com.muxy.app.networking.muxy1.protocol.FileWriteParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.VcsProjectParams
import com.muxy.app.testing.expectFailure
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class DemoProjectToolsTest {
    @Test
    fun bothProjectsHaveIndependentFilesGitAndPrimaryWorktrees() =
        runTest {
            val backend = DemoBackend()
            val projects =
                backend
                    .handle(Method.LIST_PROJECTS, null)
                    .result
                    .decode<ProjectsResult>()
                    .projects
            for (project in projects) {
                val params = ProtocolJson.encodeToJsonElement(VcsProjectParams(project.id.uuidString))
                val status = backend.handle(Method.VCS_REFRESH, params).result.decode<VcsStatus>()
                val trees = backend.handle(Method.LIST_WORKTREES, params).result.decode<List<Worktree>>()
                val files =
                    backend
                        .handle(Method.FILES_LIST, ProtocolJson.encodeToJsonElement(FilePathParams(project.id.uuidString, ".")))
                        .result
                        .decode<List<RemoteFileEntry>>()
                assertTrue(files.any { it.path == ".build" && it.isIgnored })
                assertTrue(files.any { it.path == "archive.bin" })
                assertEquals(1, trees.size)
                assertTrue(trees.single().isPrimary)
                assertFalse(trees.single().canBeRemoved)
                if (project.name == "Muxy") {
                    assertEquals("main", status.branch)
                    assertEquals(1, status.stagedFiles.size)
                    assertEquals(1, status.changedFiles.size)
                } else {
                    assertEquals("feature/native-git", status.branch)
                    assertEquals(2L, status.aheadCount)
                    assertEquals(42L, status.pullRequest?.number)
                    assertEquals("4/4 passing", status.pullRequest?.checks?.label)
                    assertTrue(status.changedFiles.isEmpty())
                }
            }
            val project = projects.first()
            val reply =
                backend.handle(
                    Method.FILES_WRITE,
                    ProtocolJson.encodeToJsonElement(
                        FileWriteParams(project.id.uuidString, "new.md", "hello", FileEncoding.UTF8),
                    ),
                )
            val event =
                reply.events
                    .single()
                    .data!!
                    .decode<FileChangedEvent>()
            assertEquals(project.id, event.projectId)
            assertEquals(listOf("new.md"), event.paths)
            val otherFiles =
                backend
                    .handle(
                        Method.FILES_LIST,
                        ProtocolJson.encodeToJsonElement(
                            FilePathParams(projects.last().id.uuidString, "."),
                        ),
                    ).result
                    .decode<List<RemoteFileEntry>>()
            assertFalse(otherFiles.any { it.path == "new.md" })
        }

    @Test
    fun fileCrudPreservesContentsAndUsesUniqueDestinations() {
        val files = DemoFileStore("Demo")
        files.write("note.txt", "hello", FileEncoding.UTF8)
        assertEquals(listOf("note.txt"), files.changedPaths)
        assertEquals(listOf("renamed.txt"), files.rename("note.txt", "renamed.txt"))
        assertEquals(listOf("Sources/renamed.txt"), files.move(listOf("renamed.txt"), "Sources"))
        files.write("renamed.txt", "second", FileEncoding.UTF8)
        assertEquals(listOf("Sources/renamed 2.txt"), files.move(listOf("renamed.txt"), "Sources"))
        assertEquals("hello", files.read("Sources/renamed.txt", FileEncoding.UTF8).content)
        assertEquals("second", files.read("Sources/renamed 2.txt", FileEncoding.UTF8).content)
        assertEquals(listOf("Sources 2"), files.mkdir("Sources"))
        files.delete(listOf("Sources"))
        assertFalse(files.list("").any { it.path == "Sources" })
    }

    @Test
    fun binaryAndImageDataAreAvailableWithoutPretendingBinaryIsUtf8() =
        runTest {
            val files = DemoFileStore("Demo")
            expectFailure<ProtocolException> { files.read("archive.bin", FileEncoding.UTF8) }
            val image: RemoteFileContent = files.read("assets/icon.png", FileEncoding.BASE64)
            val bytes = Base64.getDecoder().decode(image.content)
            assertEquals(0x89, bytes.first().toInt() and 0xff)
            assertEquals("PNG", bytes.copyOfRange(1, 4).decodeToString())
        }

    @Test
    fun failedRenameAndInvalidBatchMoveLeaveFilesUnchanged() =
        runTest {
            val files = DemoFileStore("Demo")
            val before = files.read("README.md", FileEncoding.UTF8)
            expectFailure<ProtocolException> { files.rename("archive.bin", "README.md") }
            expectFailure<ProtocolException> { files.move(listOf("README.md", "missing"), "Sources") }
            assertEquals(before, files.read("README.md", FileEncoding.UTF8))
            assertTrue(files.list("").any { it.path == "archive.bin" })
        }

    @Test
    fun demoWritesEnforceTheSameFiveMiBLimit() =
        runTest {
            val files = DemoFileStore("Demo")
            files.write("large", "x".repeat(FileLimits.MAXIMUM_BYTES), FileEncoding.UTF8)
            assertEquals(FileLimits.MAXIMUM_BYTES.toLong(), files.stat("large").size)
            expectFailure<ProtocolException> { files.write("large", "x".repeat(FileLimits.MAXIMUM_BYTES + 1), FileEncoding.UTF8) }
            assertEquals(FileLimits.MAXIMUM_BYTES.toLong(), files.stat("large").size)
        }
}
