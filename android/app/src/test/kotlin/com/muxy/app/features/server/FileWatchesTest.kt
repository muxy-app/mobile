@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.server

import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.FakeServerFiles
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.serverCredential
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uniffi.muxy_mobile.ConnectionEvent

class FileWatchesTest {
    @get:Rule val main = MainDispatcherRule()
    private val files = mutableMapOf<String, FakeServerFiles>()
    private val connection = FakeServerConnection().apply { projectFiles = { files.getOrPut(it) { FakeServerFiles() } } }

    @Test
    fun subscriptionsAreReferenceCountedPerProject() =
        runTest {
            val watches = FileWatches(backgroundScope)
            watches.connectionChanged(connection)
            val first = backgroundScope.launch { watches.changes("muxy").collect() }
            val second = backgroundScope.launch { watches.changes("muxy").collect() }
            runCurrent()
            assertEquals(listOf("watch"), files.getValue("muxy").calls)
            first.cancel()
            runCurrent()
            assertEquals(listOf("watch"), files.getValue("muxy").calls)
            second.cancel()
            runCurrent()
            assertEquals(listOf("watch", "unwatch"), files.getValue("muxy").calls)
            assertEquals(2, files.getValue("muxy").closeCount)
        }

    @Test
    fun disconnectedSubscriptionsAreDeferredAndRewatchedOnNewConnection() =
        runTest {
            val watches = FileWatches(backgroundScope)
            val collector = backgroundScope.launch { watches.changes("muxy").collect() }
            runCurrent()
            assertTrue(files.isEmpty())
            watches.connectionChanged(connection)
            runCurrent()
            assertEquals(listOf("watch"), files.getValue("muxy").calls)
            watches.connectionChanged(null)
            val replacementFiles = FakeServerFiles()
            val replacement = FakeServerConnection().apply { projectFiles = { replacementFiles } }
            watches.connectionChanged(replacement)
            runCurrent()
            assertEquals(listOf("watch"), replacementFiles.calls)
            collector.cancel()
            runCurrent()
            assertEquals(listOf("watch", "unwatch"), replacementFiles.calls)
            assertEquals(listOf("watch"), files.getValue("muxy").calls)
        }

    @Test
    fun cancelledOfflineSubscriptionIsNeverWatched() =
        runTest {
            val watches = FileWatches(backgroundScope)
            val collector = backgroundScope.launch { watches.changes("muxy").collect() }
            runCurrent()
            collector.cancel()
            runCurrent()
            watches.connectionChanged(connection)
            runCurrent()
            assertTrue(files.isEmpty())
        }

    @Test
    fun thirtyTwoDistinctProjectsAreAllowedAndDuplicateSubscribersDoNotCountTwice() =
        runTest {
            val watches = FileWatches(backgroundScope)
            watches.connectionChanged(connection)
            val jobs = (1..32).map { id -> backgroundScope.launch { watches.changes("p$id").collect() } }
            backgroundScope.launch { watches.changes("p1").collect() }
            runCurrent()
            assertEquals(32, files.size)
            val rejected = async { runCatching { watches.changes("p33").first() } }
            runCurrent()
            assertTrue(rejected.await().exceptionOrNull() is IllegalStateException)
            jobs.last().cancel()
            runCurrent()
            backgroundScope.launch { watches.changes("p33").collect() }
            runCurrent()
            assertEquals(listOf("watch"), files.getValue("p33").calls)
        }

    @Test
    fun eventsOnlyReachSubscribersOfThatProject() =
        runTest {
            val watches = FileWatches(backgroundScope)
            val muxy = mutableListOf<List<String>>()
            val other = mutableListOf<List<String>>()
            backgroundScope.launch { watches.changes("muxy").collect { muxy += it } }
            backgroundScope.launch { watches.changes("other").collect { other += it } }
            runCurrent()
            watches.deliver("muxy", listOf("README.md"))
            watches.deliver("other", emptyList())
            runCurrent()
            assertEquals(listOf(listOf("README.md")), muxy)
            assertEquals(listOf(emptyList<String>()), other)
        }

    @Test
    fun overflowRequestsAFullRescanInsteadOfLosingChangesSilently() =
        runTest {
            val watches = FileWatches(backgroundScope)
            val received = mutableListOf<List<String>>()
            val release = CompletableDeferred<Unit>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                watches.changes("muxy").collect {
                    received += it
                    release.await()
                }
            }
            runCurrent()
            watches.deliver("muxy", listOf("first"))
            runCurrent()
            repeat(100) { watches.deliver("muxy", listOf("$it")) }
            release.complete(Unit)
            runCurrent()
            assertTrue(received.contains(emptyList()))
        }

    @Test
    fun controllerForwardsFilesChangedAndIgnoresOldConnectionCallbacks() =
        runTest {
            val connector = FakeServerConnector(listOf(Result.success(connection)))
            val controller = ServerController("server-1", connector, { serverCredential() }, backgroundScope)
            controller.setWantsConnection(true)
            runCurrent()
            val received = mutableListOf<List<String>>()
            backgroundScope.launch { controller.fileWatches.changes("muxy").collect { received += it } }
            runCurrent()
            connector.emit(ConnectionEvent.FilesChanged("muxy", listOf("README.md")))
            runCurrent()
            assertEquals(listOf(listOf("README.md")), received)
            controller.setWantsConnection(false)
            connector.emit(ConnectionEvent.FilesChanged("muxy", listOf("stale")))
            runCurrent()
            assertEquals(1, received.size)
        }
}
