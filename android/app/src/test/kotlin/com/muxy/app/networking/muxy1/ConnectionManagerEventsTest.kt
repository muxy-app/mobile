package com.muxy.app.networking.muxy1

import app.cash.turbine.test
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.testing.Frames
import com.muxy.app.testing.TransportRecorder
import com.muxy.app.testing.connectionManager
import com.muxy.app.testing.device
import com.muxy.app.testing.tokenStoreWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionManagerEventsTest {
    private val projectsEvent = Frames.event(EventName.PROJECTS_CHANGED, "projects", """{ "projects": [] }""")

    @Test
    fun eventsAreForwardedToASubscriber() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            manager.events(studio.id).test {
                recorder.latest!!.enqueue(projectsEvent)
                assertEquals(EventName.PROJECTS_CHANGED, awaitItem().event)
            }
        }

    @Test
    fun eventsFanOutToSeveralSubscribers() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            manager.events(studio.id).test {
                val first = this
                manager.events(studio.id).test {
                    recorder.latest!!.enqueue(projectsEvent)
                    assertEquals(EventName.PROJECTS_CHANGED, awaitItem().event)
                    assertEquals(EventName.PROJECTS_CHANGED, first.awaitItem().event)
                }
            }
        }

    @Test
    fun aSubscriberSurvivesAReconnect() =
        runTest {
            val studio = device()
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio))
            manager.ensureConnected(studio)
            manager.events(studio.id).test {
                manager.disconnect()
                manager.ensureConnected(studio)
                recorder.latest!!.enqueue(projectsEvent)
                assertEquals(EventName.PROJECTS_CHANGED, awaitItem().event)
            }
        }

    @Test
    fun eventsOfAnotherMacAreNotDelivered() =
        runTest {
            val studio = device()
            val laptop = device(name = "Laptop", host = "laptop.local")
            val recorder = TransportRecorder()
            val manager = connectionManager(recorder, tokenStoreWith(studio, laptop))
            manager.ensureConnected(laptop)
            manager.events(laptop.id).test {
                val laptopEvents = this
                manager.events(studio.id).test {
                    recorder.latest!!.enqueue(projectsEvent)
                    assertEquals(EventName.PROJECTS_CHANGED, laptopEvents.awaitItem().event)
                    expectNoEvents()
                }
            }
        }
}
