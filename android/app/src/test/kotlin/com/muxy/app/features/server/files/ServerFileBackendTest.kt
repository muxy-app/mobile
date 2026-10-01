package com.muxy.app.features.server.files

import com.muxy.app.features.files.FileException
import com.muxy.app.networking.server.ServerRequestError
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerFiles
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.expectFailure
import com.muxy.app.testing.toolServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uniffi.muxy_mobile.MobileException

class ServerFileBackendTest {
    @get:Rule val main = MainDispatcherRule()
    private val files = FakeServerFiles()
    private val connection = FakeServerConnection().apply { projectFiles = { files } }

    @Test
    fun successfulTextReadNeverFallsBackAndClosesTheHandle() =
        runTest {
            val backend = ServerFileBackend("muxy", toolServer(connection))
            val result = backend.readText("README.md")
            assertEquals("hello", result.text)
            assertEquals(5L, result.size)
            assertEquals(listOf("text:README.md"), files.calls)
            assertEquals(1, files.closeCount)
        }

    @Test
    fun serverErrorFallsBackToStrictUtf8Bytes() =
        runTest {
            files.text = { throw MobileException.Server("unsupported") }
            files.bytes = { "héllo 🌍".toByteArray() }
            val result = ServerFileBackend("muxy", toolServer(connection)).readText("README.md")
            assertEquals("héllo 🌍", result.text)
            assertEquals("héllo 🌍".toByteArray().size.toLong(), result.size)
            assertEquals(listOf("text:README.md", "bytes:README.md"), files.calls)
            assertEquals(1, files.closeCount)
        }

    @Test
    fun invalidUtf8IsNotSilentlyReplaced() =
        runTest {
            files.text = { throw MobileException.Server("not text") }
            files.bytes = { byteArrayOf(0xc3.toByte(), 0x28) }
            expectFailure<FileException.NotText> { ServerFileBackend("muxy", toolServer(connection)).readText("archive.bin") }
            assertEquals(1, files.closeCount)
        }

    @Test
    fun aFailedFallbackPreservesTheOriginalServerMessage() =
        runTest {
            files.text = { throw MobileException.Server("permission denied") }
            files.bytes = { throw MobileException.Timeout() }
            val error = expectFailure<ServerRequestError> { ServerFileBackend("muxy", toolServer(connection)).readText("README.md") }
            assertTrue(error.message.orEmpty().contains("permission denied"))
            assertEquals(1, files.closeCount)
        }

    @Test
    fun transportFailureDoesNotAttemptASecondRead() =
        runTest {
            files.text = { throw MobileException.Disconnected() }
            expectFailure<ServerRequestError> { ServerFileBackend("muxy", toolServer(connection)).readText("README.md") }
            assertEquals(listOf("text:README.md"), files.calls)
            assertEquals(1, files.closeCount)
        }

    @Test
    fun cancellationPropagatesAndClosesTheHandle() =
        runTest {
            files.text = { throw CancellationException("cancelled") }
            expectFailure<CancellationException> { ServerFileBackend("muxy", toolServer(connection)).readText("README.md") }
            assertEquals(listOf("text:README.md"), files.calls)
            assertEquals(1, files.closeCount)
        }

    @Test
    fun responseFromDisconnectedConnectionIsRejected() =
        runTest {
            val server = toolServer(connection)
            val started = CompletableDeferred<Unit>()
            val response = CompletableDeferred<String>()
            files.text = {
                started.complete(Unit)
                response.await()
            }
            val result = async { runCatching { ServerFileBackend("muxy", server).readText("README.md") } }
            started.await()
            server.setWantsConnection(false)
            response.complete("stale")
            assertTrue(result.await().exceptionOrNull() is ServerRequestError)
            assertEquals(1, files.closeCount)
        }

    @Test
    fun unsignedFileSizesCannotWrapIntoAnAllowedSize() =
        runTest {
            files.info = files.info.copy(size = ULong.MAX_VALUE)
            assertEquals(Long.MAX_VALUE, ServerFileBackend("muxy", toolServer(connection)).stat("README.md").size)
        }
}
