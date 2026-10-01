package com.muxy.app.features.connections

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.features.server.ServerDirectory
import com.muxy.app.features.server.ServerFocus
import com.muxy.app.models.ConnectionKind
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.InMemoryCredentialStore
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.device
import com.muxy.app.testing.serverCredential
import com.muxy.app.testing.tokenStoreWith
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConnectionsListViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val owner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = error("Not used")
        }

    @Test
    fun deletingAConnectionAlsoDeletesItsSecrets() =
        runTest {
            val studio = device()
            val laptop = device(name = "Laptop")
            val store = InMemoryConnectionStore(listOf(studio, laptop))
            val tokens = tokenStoreWith(studio, laptop)
            val viewModel =
                ConnectionsListViewModel(
                    store,
                    tokens,
                    InMemoryCredentialStore(),
                    ServerDirectory(InMemoryCredentialStore(), FakeServerConnector(emptyList()), backgroundScope),
                )
            viewModel.delete(studio)
            advanceUntilIdle()
            assertEquals(listOf(laptop), viewModel.connections.value)
            assertNull(tokens.credential(studio.id))
            assertEquals(laptop.id.toString().uppercase(), tokens.credential(laptop.id)?.deviceId)
        }

    @Test
    fun deletingAComputerForgetsItsControllerAndCredential() =
        runTest {
            val computer = device(name = "Studio").copy(kind = ConnectionKind.SERVER, serverId = "server-1")
            val store = InMemoryConnectionStore(listOf(computer))
            val credentials = InMemoryCredentialStore().apply { items["server-1"] = serverCredential() }
            val connection = FakeServerConnection()
            val directory = ServerDirectory(credentials, FakeServerConnector(listOf(Result.success(connection))), backgroundScope)
            val controller = directory.controller("server-1")
            directory.onStart(owner)
            directory.focus(ServerFocus("server-1"))
            runCurrent()
            ConnectionsListViewModel(store, tokenStoreWith(), credentials, directory).delete(computer)
            runCurrent()
            assertTrue(store.load().isEmpty())
            assertTrue(credentials.items.isEmpty())
            assertTrue(connection.isDisconnected)
            assertNotSame(controller, directory.controller("server-1"))
        }

    @Test
    fun subtitlesShowTheHostAndPort() {
        assertEquals("studio.local:4865", device().subtitle)
    }
}
