package com.muxy.app.features.files

import com.muxy.app.models.FileLimits
import com.muxy.app.models.FileScope
import com.muxy.app.testing.FakeFileBackend
import com.muxy.app.testing.expectFailure
import com.muxy.app.testing.fileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileClientTest {
    private val backend = FakeFileBackend()
    private val client = FileClient(backend)
    private val scope = FileScope()

    @Test
    fun listsFoldersFirstInNaturalOrder() =
        runTest {
            backend.listing = listOf(fileEntry("file10"), fileEntry("z", true), fileEntry("file2"), fileEntry("a", true))
            assertEquals(listOf("a", "z", "file2", "file10"), client.list("").entries.map { it.name })
            assertEquals(listOf("list:"), backend.calls)
        }

    @Test
    fun rejectsDuplicateOrEscapingListingEntries() =
        runTest {
            backend.listing = listOf(fileEntry("a"), fileEntry("a"))
            expectFailure<FileException.UnexpectedResponse> { client.list("") }
            backend.listing = listOf(fileEntry("../secret"))
            expectFailure<FileException.Message> { client.list("") }
        }

    @Test
    fun statsBeforeReadingTextOrBytes() =
        runTest {
            client.readText("README.md")
            client.readData("README.md")
            assertEquals(listOf("stat:README.md", "read:README.md", "stat:README.md", "bytes:README.md"), backend.calls)
        }

    @Test
    fun oversizedFilesAndDirectoriesAreNeverRead() =
        runTest {
            backend.size = FileLimits.MAXIMUM_BYTES.toLong() + 1
            expectFailure<FileException.Message> { client.readText("README.md") }
            expectFailure<FileException.Message> { client.readData("README.md") }
            expectFailure<FileException.Message> { client.readText("Sources") }
            assertTrue(backend.calls.all { it.startsWith("stat:") })
        }

    @Test
    fun exactlyFiveMiBIsReadableAndWritable() =
        runTest {
            val text = "a".repeat(FileLimits.MAXIMUM_BYTES)
            backend.texts["README.md"] = text
            assertEquals(text, client.readText("README.md").text)
            client.write("README.md", text, scope)
            assertEquals(text, backend.texts["README.md"])
        }

    @Test
    fun writeLimitCountsUtf8BytesRatherThanCharacters() =
        runTest {
            expectFailure<FileException.Message> { client.write("README.md", "é".repeat(FileLimits.MAXIMUM_BYTES / 2 + 1), scope) }
            assertTrue(backend.calls.isEmpty())
        }

    @Test
    fun aFileGrowingAfterStatIsRejected() =
        runTest {
            backend.size = 1
            backend.onRead = { "a".repeat(FileLimits.MAXIMUM_BYTES + 1) }
            expectFailure<FileException.UnexpectedResponse> { client.readText("README.md") }
        }

    @Test
    fun createsThroughATemporaryFileThenRenamesWithoutWritingTheDestination() =
        runTest {
            assertEquals("new.md", client.create("new.md", scope))
            val write = backend.calls.first()
            assertTrue(write.startsWith("write:.muxy-mobile-create-"))
            assertEquals("rename:${write.removePrefix("write:")}:new.md", backend.calls[1])
            assertEquals("", backend.texts["new.md"])
            assertFalse(backend.texts.keys.any { it.startsWith(".muxy-mobile-create-") })
        }

    @Test
    fun failedOrCancelledRenamesCleanUpTheTemporaryFile() =
        runTest {
            backend.onRename = { throw IllegalStateException("already exists") }
            expectFailure<IllegalStateException> { client.create("README.md", scope) }
            assertEquals("original", backend.texts["README.md"])
            assertTrue(backend.calls.last().startsWith("delete:.muxy-mobile-create-"))
            backend.onRename = { throw CancellationException("cancelled") }
            expectFailure<CancellationException> { client.create("new.md", scope) }
            assertFalse(backend.texts.keys.any { it.startsWith(".muxy-mobile-create-") })
        }

    @Test
    fun invalidMutationsNeverReachTheBackend() =
        runTest {
            expectFailure<FileException.Message> { client.write("../secret", "x", scope) }
            expectFailure<FileException.Message> { client.rename("README.md", "../secret", scope) }
            expectFailure<FileException.Message> { client.move(listOf("Sources"), "Sources/Child", scope) }
            expectFailure<FileException.Message> { client.delete(emptyList(), scope) }
            expectFailure<FileException.Message> { client.delete(listOf(""), scope) }
            assertTrue(backend.calls.isEmpty())
        }
}
