package com.muxy.app.features.server

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.InMemoryCredentialStore
import com.muxy.app.testing.serverCredential
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDirectoryTest {
    private val owner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = error("Not used")
        }

    private val credentials =
        InMemoryCredentialStore().apply {
            items["server-1"] = serverCredential(id = "server-1", name = "Studio")
            items["server-2"] = serverCredential(id = "server-2", name = "Laptop")
        }

    private fun TestScope.directory(connector: FakeServerConnector) = ServerDirectory(credentials, connector, backgroundScope)

    private fun connections(count: Int) = FakeServerConnector(List(count) { Result.success(FakeServerConnection()) })

    @Test
    fun aServerConnectsOnlyInTheForegroundWhileItIsActive() =
        runTest {
            val connector = connections(2)
            val directory = directory(connector)
            val studio = directory.controller("server-1")
            directory.focus(ServerFocus("server-1"))
            runCurrent()
            assertEquals(0, connector.connectCount)
            directory.onStart(owner)
            runCurrent()
            assertEquals(ServerPhase.Connected, studio.phase.value)
        }

    @Test
    fun goingToTheBackgroundDisconnects() =
        runTest {
            val connection = FakeServerConnection()
            val directory = directory(FakeServerConnector(listOf(Result.success(connection))))
            directory.onStart(owner)
            directory.focus(ServerFocus("server-1"))
            val studio = directory.controller("server-1")
            runCurrent()
            directory.onStop(owner)
            runCurrent()
            assertTrue(connection.isDisconnected)
            assertEquals(ServerPhase.Idle, studio.phase.value)
        }

    @Test
    fun onlyTheActiveServerStaysConnected() =
        runTest {
            val first = FakeServerConnection()
            val directory = directory(FakeServerConnector(listOf(Result.success(first), Result.success(FakeServerConnection()))))
            directory.onStart(owner)
            val studio = directory.controller("server-1")
            val laptop = directory.controller("server-2")
            directory.focus(ServerFocus("server-1"))
            runCurrent()
            directory.focus(ServerFocus("server-2"))
            runCurrent()
            assertTrue(first.isDisconnected)
            assertEquals(ServerPhase.Idle, studio.phase.value)
            assertEquals(ServerPhase.Connected, laptop.phase.value)
        }

    @Test
    fun theFocusedProjectIsVisibleOnlyOnTheActiveServer() =
        runTest {
            val directory = directory(connections(1))
            directory.onStart(owner)
            val studio = directory.controller("server-1")
            directory.focus(ServerFocus("server-1", "muxy"))
            runCurrent()
            assertTrue(studio.projectModel("muxy").isVisible)
            directory.focus(ServerFocus("server-1"))
            assertFalse(studio.projectModel("muxy").isVisible)
        }

    @Test
    fun aControllerCreatedAfterFocusingConnectsAtOnce() =
        runTest {
            val connector = connections(1)
            val directory = directory(connector)
            directory.onStart(owner)
            directory.focus(ServerFocus("server-1", "muxy"))
            val studio = directory.controller("server-1")
            runCurrent()
            assertEquals(ServerPhase.Connected, studio.phase.value)
            assertTrue(studio.projectModel("muxy").isVisible)
        }

    @Test
    fun eachServerHasOneController() =
        runTest {
            val directory = directory(connections(0))
            assertSame(directory.controller("server-1"), directory.controller("server-1"))
        }

    @Test
    fun forgettingAServerDisconnectsItAndStartsOverNextTime() =
        runTest {
            val connection = FakeServerConnection()
            val directory = directory(FakeServerConnector(listOf(Result.success(connection))))
            directory.onStart(owner)
            directory.focus(ServerFocus("server-1"))
            val studio = directory.controller("server-1")
            runCurrent()
            directory.forget("server-1")
            assertTrue(connection.isDisconnected)
            assertFalse(studio === directory.controller("server-1"))
        }

    @Test
    fun aMissingCredentialFailsWithoutConnecting() =
        runTest {
            val connector = connections(1)
            val directory = directory(connector)
            directory.onStart(owner)
            directory.focus(ServerFocus("unknown"))
            val unknown = directory.controller("unknown")
            runCurrent()
            assertTrue(unknown.phase.value is ServerPhase.Failed)
            assertEquals(0, connector.connectCount)
        }
}
