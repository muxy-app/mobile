package com.muxy.app.networking.server.sdk

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SdkLanesTest {
    private val events = mutableListOf<String>()
    private val handle = AutoCloseable { events += "closed" }

    private fun TestScope.lanes() = SdkLanes(StandardTestDispatcher(testScheduler))

    @Test
    fun aHandleClosesAfterTheInputQueuedBeforeIt() =
        runTest {
            val lanes = lanes()
            lanes.send { events += "a" }
            lanes.send { events += "b" }
            lanes.closeAfterInput(handle)
            runCurrent()
            assertEquals(listOf("a", "b", "closed"), events)
        }

    @Test
    fun aFailedSendDoesNotStopTheQueue() =
        runTest {
            val lanes = lanes()
            lanes.send { error("Terminal object has already been destroyed") }
            lanes.send { events += "next" }
            lanes.closeAfterInput(handle)
            runCurrent()
            assertEquals(listOf("next", "closed"), events)
        }

    @Test
    fun aDisconnectDropsQueuedInputButStillClosesTheHandle() =
        runTest {
            val lanes = lanes()
            lanes.send { events += "dropped" }
            lanes.closeAfterInput(handle)
            lanes.close()
            runCurrent()
            assertEquals(listOf("closed"), events)
        }

    @Test
    fun aHandleReleasedAfterADisconnectIsStillClosed() =
        runTest {
            val lanes = lanes()
            lanes.close()
            lanes.closeAfterInput(handle)
            runCurrent()
            assertEquals(listOf("closed"), events)
        }
}
