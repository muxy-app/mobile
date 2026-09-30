package com.muxy.app.networking.muxy1

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.services.pairing.PairingStatus
import com.muxy.app.testing.Frames
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.TransportRecorder
import com.muxy.app.testing.connectionManager
import com.muxy.app.testing.credential
import com.muxy.app.testing.device
import com.muxy.app.testing.tokenStoreWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLifecycleTest {
    private fun TestScope.foregroundScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext[Job]))

    private val owner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = error("Not used")
        }

    @Test
    fun aMacScreenInTheForegroundConnects() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            val lifecycle = ConnectionLifecycle(manager, InMemoryConnectionStore(listOf(studio)), foregroundScope())
            lifecycle.onStart(owner)
            lifecycle.focus(ConnectionFocus.Device(studio.id))
            advanceUntilIdle()
            assertEquals(ConnectionState.Connected, manager.status.value.of(studio.id))
            assertEquals(1, recorder.transports.size)
        }

    @Test
    fun returningFromTheBackgroundReconnectsOnceForStackedScreens() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            val lifecycle = ConnectionLifecycle(manager, InMemoryConnectionStore(listOf(studio)), foregroundScope())
            lifecycle.onStart(owner)
            lifecycle.focus(ConnectionFocus.Device(studio.id))
            advanceUntilIdle()
            lifecycle.focus(ConnectionFocus.Device(studio.id))
            advanceUntilIdle()
            assertEquals(1, recorder.transports.size)
            lifecycle.onStop(owner)
            advanceUntilIdle()
            assertEquals(ConnectionState.Disconnected, manager.status.value.state)
            assertTrue(recorder.transports.first().didClose)
            lifecycle.onStart(owner)
            advanceUntilIdle()
            assertEquals(ConnectionState.Connected, manager.status.value.of(studio.id))
            assertEquals(2, recorder.transports.size)
        }

    @Test
    fun returningToTheListDisconnects() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            val lifecycle = ConnectionLifecycle(manager, InMemoryConnectionStore(listOf(studio)), foregroundScope())
            lifecycle.onStart(owner)
            lifecycle.focus(ConnectionFocus.Device(studio.id))
            advanceUntilIdle()
            lifecycle.focus(ConnectionFocus.None)
            advanceUntilIdle()
            assertEquals(ConnectionState.Disconnected, manager.status.value.state)
        }

    @Test
    fun aModalOnTopKeepsTheConnection() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            val lifecycle = ConnectionLifecycle(manager, InMemoryConnectionStore(listOf(studio)), foregroundScope())
            lifecycle.onStart(owner)
            lifecycle.focus(ConnectionFocus.Device(studio.id))
            advanceUntilIdle()
            lifecycle.focus(ConnectionFocus.Hold)
            advanceUntilIdle()
            assertEquals(ConnectionState.Connected, manager.status.value.of(studio.id))
        }

    @Test
    fun nothingConnectsWhileInTheBackground() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            val lifecycle = ConnectionLifecycle(manager, InMemoryConnectionStore(listOf(studio)), foregroundScope())
            lifecycle.focus(ConnectionFocus.Device(studio.id))
            advanceUntilIdle()
            assertTrue(recorder.transports.isEmpty())
        }

    @Test
    fun aPairingSurvivesTheBackground() =
        runTest {
            val studio = device()
            val recorder =
                TransportRecorder { _, frame ->
                    if (Frames.method(frame) ==
                        "authenticateDevice"
                    ) {
                        listOf(Frames.error(Frames.id(frame), 401))
                    } else {
                        emptyList()
                    }
                }
            val manager = connectionManager(recorder, tokenStoreWith())
            val lifecycle = ConnectionLifecycle(manager, InMemoryConnectionStore(), foregroundScope())
            lifecycle.onStart(owner)
            lifecycle.focus(ConnectionFocus.Hold)
            val statuses = mutableListOf<PairingStatus>()
            launch { manager.beginPairing(studio, credential(studio)) { statuses += it } }
            runCurrent()
            lifecycle.onStop(owner)
            runCurrent()
            assertEquals(PairingStatus.AwaitingApproval, statuses.last())
            assertFalse(recorder.latest!!.didClose)
        }
}
