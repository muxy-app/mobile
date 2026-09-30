package com.muxy.app.networking.muxy1

import com.muxy.app.networking.muxy1.protocol.AuthParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.PairingResult
import com.muxy.app.networking.muxy1.protocol.ProtocolErrorBody
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.protocol.SelectProjectParams
import com.muxy.app.networking.muxy1.protocol.SelectTabParams
import com.muxy.app.networking.muxy1.transport.TransportException
import com.muxy.app.networking.muxy1.transport.TransportFailure
import com.muxy.app.testing.Frames
import com.muxy.app.testing.MockTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MuxyClientTest {
    private val authParams = ProtocolJson.encodeToJsonElement(AuthParams.serializer(), AuthParams("d", "iPhone", "t"))

    @Test
    fun resolvesMatchingResponse() =
        runTest {
            val client = client(MockTransport { listOf(Frames.pairing(Frames.id(it), clientId = "c-1")) })
            val result = client.request(Method.AUTHENTICATE_DEVICE, authParams)
            assertEquals(ResultType.PAIRING, result.type)
            assertEquals("c-1", result.decode(PairingResult.serializer()).clientId)
            client.stop()
        }

    @Test
    fun resolvesOkResponseWithoutValue() =
        runTest {
            val client = client(MockTransport { listOf(Frames.result(Frames.id(it), ResultType.OK)) })
            val result =
                client.request(
                    Method.SELECT_PROJECT,
                    ProtocolJson.encodeToJsonElement(SelectProjectParams.serializer(), SelectProjectParams("p")),
                )
            assertEquals(ResultType.OK, result.type)
            client.stop()
        }

    @Test
    fun errorResponseThrowsProtocolException() =
        runTest {
            val client = client(MockTransport { listOf(Frames.error(Frames.id(it), 403, "Pairing denied")) })
            val error = runCatching { client.request(Method.AUTHENTICATE_DEVICE, authParams) }.exceptionOrNull()
            assertEquals(ProtocolErrorBody(403, "Pairing denied"), (error as ProtocolException).body)
            client.stop()
        }

    @Test
    fun mismatchedIdDoesNotResolveAndTimesOut() =
        runTest {
            val transport = MockTransport { listOf(Frames.pairing("other")) }
            val client = MuxyClient(transport, backgroundScope, requestTimeout = 200.milliseconds).also { it.start() }
            val error = runCatching { client.request(Method.AUTHENTICATE_DEVICE, authParams) }.exceptionOrNull()
            assertEquals(TransportFailure.TIMED_OUT, (error as TransportException).failure)
            client.stop()
        }

    @Test
    fun aPerRequestTimeoutOverridesTheDefault() =
        runTest {
            val client = MuxyClient(MockTransport(), backgroundScope, requestTimeout = 1.seconds).also { it.start() }
            val pending = async { runCatching { client.request(Method.PAIR_DEVICE, authParams, timeout = 120.seconds) }.exceptionOrNull() }
            testScheduler.advanceTimeBy(119.seconds)
            runCurrent()
            assertTrue(pending.isActive)
            testScheduler.advanceTimeBy(2.seconds)
            assertEquals(TransportFailure.TIMED_OUT, (pending.await() as TransportException).failure)
            client.stop()
        }

    @Test
    fun transportCloseFailsOutstandingRequest() =
        runTest {
            val transport = MockTransport()
            val client = client(transport)
            val pending = async { runCatching { client.request(Method.AUTHENTICATE_DEVICE, authParams) }.exceptionOrNull() }
            runCurrent()
            transport.failReaders()
            assertEquals(TransportFailure.CLOSED, (pending.await() as TransportException).failure)
            client.stop()
        }

    @Test
    fun stopFailsOutstandingRequestsAndClosesTheTransport() =
        runTest {
            val transport = MockTransport()
            val client = client(transport)
            val pending = async { runCatching { client.request(Method.AUTHENTICATE_DEVICE, authParams) }.exceptionOrNull() }
            runCurrent()
            client.stop()
            client.stop()
            assertEquals(TransportFailure.CLOSED, (pending.await() as TransportException).failure)
            assertTrue(transport.didClose)
        }

    @Test
    fun eventsAreSurfaced() =
        runTest {
            val transport = MockTransport()
            val client = client(transport)
            transport.enqueue(Frames.event("projectsChanged", "projects", "{}"))
            assertEquals("projectsChanged", client.events.first().event)
            client.stop()
        }

    @Test
    fun requestIdsAreUppercaseUuids() =
        runTest {
            val transport = MockTransport { listOf(Frames.pairing(Frames.id(it))) }
            val client = client(transport)
            client.request(Method.AUTHENTICATE_DEVICE, authParams)
            val id = Frames.id(transport.sentFrames.single())
            assertEquals(id.uppercase(), id)
            assertEquals(36, id.length)
            client.stop()
        }

    @Test
    fun notifySendsRequestShapedFrame() =
        runTest {
            val transport = MockTransport()
            val client = client(transport)
            client.notify(Method.SELECT_TAB, ProtocolJson.encodeToJsonElement(SelectTabParams.serializer(), SelectTabParams("p", "a", "t")))
            val frame = transport.sentFrames.single()
            assertEquals("selectTab", Frames.method(frame))
            assertEquals(
                "t",
                Frames
                    .params(frame)
                    .getValue("tabID")
                    .jsonPrimitive.content,
            )
            client.stop()
        }

    @Test
    fun notifyReturnsWithoutWaitingForAResponse() =
        runTest {
            val client = client(MockTransport())
            client.notify(Method.SELECT_TAB, null)
            assertEquals(0L, testScheduler.currentTime)
            client.stop()
        }

    @Test
    fun notifyDoesNotConsumeTheResponseSlotOfALaterRequest() =
        runTest {
            val transport =
                MockTransport { frame ->
                    if (Frames.method(frame) ==
                        "selectProject"
                    ) {
                        listOf(Frames.result(Frames.id(frame), ResultType.OK))
                    } else {
                        emptyList()
                    }
                }
            val client = client(transport)
            client.notify(Method.SELECT_TAB, null)
            val result =
                client.request(
                    Method.SELECT_PROJECT,
                    ProtocolJson.encodeToJsonElement(SelectProjectParams.serializer(), SelectProjectParams("p")),
                )
            assertEquals(ResultType.OK, result.type)
            client.stop()
        }

    private fun TestScope.client(transport: MockTransport): MuxyClient = MuxyClient(transport, backgroundScope).also { it.start() }
}
