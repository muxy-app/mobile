package com.muxy.app.features.connections

import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.device
import com.muxy.app.testing.tokenStoreWith
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ConnectionsListViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun deletingAConnectionAlsoDeletesItsSecrets() =
        runTest {
            val studio = device()
            val laptop = device(name = "Laptop")
            val store = InMemoryConnectionStore(listOf(studio, laptop))
            val tokens = tokenStoreWith(studio, laptop)
            val viewModel = ConnectionsListViewModel(store, tokens)
            viewModel.delete(studio)
            advanceUntilIdle()
            assertEquals(listOf(laptop), viewModel.connections.value)
            assertNull(tokens.credential(studio.id))
            assertEquals(laptop.id.toString().uppercase(), tokens.credential(laptop.id)?.deviceId)
        }

    @Test
    fun subtitlesShowTheHostAndPort() {
        assertEquals("studio.local:4865", device().subtitle)
    }
}
