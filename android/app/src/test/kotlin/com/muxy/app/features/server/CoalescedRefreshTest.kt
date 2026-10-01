package com.muxy.app.features.server

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CoalescedRefreshTest {
    @Test
    fun requestsDuringARefreshRunOnceAfterIt() =
        runTest {
            val refresh = CoalescedRefresh(backgroundScope)
            val gate = CompletableDeferred<Unit>()
            val runs = mutableListOf<String>()
            refresh.request {
                runs += "first"
                gate.await()
            }
            runCurrent()
            refresh.request { runs += "second" }
            refresh.request { runs += "third" }
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("first", "third"), runs)
        }

    @Test
    fun aRequestAfterTheLastRefreshStartsANewOne() =
        runTest {
            val refresh = CoalescedRefresh(backgroundScope)
            var runs = 0
            refresh.request { runs += 1 }
            runCurrent()
            refresh.request { runs += 1 }
            runCurrent()
            assertEquals(2, runs)
        }

    @Test
    fun cancellingDropsTheRunningAndPendingRefreshes() =
        runTest {
            val refresh = CoalescedRefresh(backgroundScope)
            val gate = CompletableDeferred<Unit>()
            val runs = mutableListOf<String>()
            refresh.request {
                gate.await()
                runs += "first"
            }
            runCurrent()
            refresh.request { runs += "pending" }
            refresh.cancel()
            gate.complete(Unit)
            runCurrent()
            assertEquals(emptyList<String>(), runs)
            refresh.request { runs += "after" }
            runCurrent()
            assertEquals(listOf("after"), runs)
        }
}
