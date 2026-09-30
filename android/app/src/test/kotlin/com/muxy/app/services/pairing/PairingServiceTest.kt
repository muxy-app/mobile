package com.muxy.app.services.pairing

import com.muxy.app.networking.muxy1.MuxyClient
import com.muxy.app.networking.muxy1.protocol.AuthParams
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.testing.Frames
import com.muxy.app.testing.MockTransport
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class PairingServiceTest {
    private val params = AuthParams("d", "iPhone", "t")

    @Test
    fun anAcceptedCredentialPairsWithoutApproval() =
        runTest {
            val statuses = mutableListOf<PairingStatus>()
            val status = LivePairingService().pair(client { listOf(Frames.pairing(Frames.id(it))) }, params) { statuses += it }
            assertTrue(status is PairingStatus.Paired)
            assertTrue(PairingStatus.Authenticating in statuses)
            assertFalse(PairingStatus.AwaitingApproval in statuses)
            assertEquals(status, statuses.last())
        }

    @Test
    fun anUnknownDeviceFallsBackToPairing() =
        runTest {
            val statuses = mutableListOf<PairingStatus>()
            val client =
                client { frame ->
                    listOf(
                        if (Frames.method(frame) ==
                            "authenticateDevice"
                        ) {
                            Frames.error(Frames.id(frame), 401)
                        } else {
                            Frames.pairing(Frames.id(frame))
                        },
                    )
                }
            val status = LivePairingService().pair(client, params) { statuses += it }
            assertTrue(status is PairingStatus.Paired)
            assertTrue(PairingStatus.AwaitingApproval in statuses)
        }

    @Test
    fun aDeniedApprovalReportsApprovalDenied() =
        runTest {
            val client =
                client { frame ->
                    listOf(
                        Frames.error(
                            Frames.id(frame),
                            if (Frames.method(frame) ==
                                "authenticateDevice"
                            ) {
                                401
                            } else {
                                403
                            },
                        ),
                    )
                }
            assertEquals(PairingStatus.Failed(PairingError.ApprovalDenied), LivePairingService().pair(client, params) {})
        }

    @Test
    fun aServerTimeoutReportsApprovalTimedOut() =
        runTest {
            val client =
                client { frame ->
                    listOf(
                        Frames.error(
                            Frames.id(frame),
                            if (Frames.method(frame) ==
                                "authenticateDevice"
                            ) {
                                401
                            } else {
                                408
                            },
                        ),
                    )
                }
            assertEquals(PairingStatus.Failed(PairingError.ApprovalTimedOut), LivePairingService().pair(client, params) {})
        }

    @Test
    fun aWrongTokenReportsWrongToken() =
        runTest {
            val statuses = mutableListOf<PairingStatus>()
            val status = LivePairingService().pair(client { listOf(Frames.error(Frames.id(it), 403)) }, params) { statuses += it }
            assertEquals(PairingStatus.Failed(PairingError.WrongToken), status)
            assertFalse(PairingStatus.AwaitingApproval in statuses)
        }

    @Test
    fun otherErrorsCarryTheServerMessage() =
        runTest {
            val status = LivePairingService().pair(client { listOf(Frames.error(Frames.id(it), 500, "boom")) }, params) {}
            assertEquals(PairingStatus.Failed(PairingError.Server(500, "boom")), status)
        }

    @Test
    fun anUnexpectedResultReportsAnInvalidResponse() =
        runTest {
            val status = LivePairingService().pair(client { listOf(Frames.result(Frames.id(it), ResultType.OK)) }, params) {}
            assertEquals(PairingStatus.Failed(PairingError.InvalidResponse), status)
        }

    @Test
    fun theApprovalWaitsTwoMinutesBeforeTimingOut() =
        runTest {
            val client =
                client { frame ->
                    if (Frames.method(frame) ==
                        "authenticateDevice"
                    ) {
                        listOf(Frames.error(Frames.id(frame), 401))
                    } else {
                        emptyList()
                    }
                }
            val status = LivePairingService().pair(client, params) {}
            assertEquals(PairingStatus.Failed(PairingError.ApprovalTimedOut), status)
            assertEquals(120.seconds.inWholeMilliseconds, testScheduler.currentTime)
        }

    @Test
    fun anAuthenticationTimeoutIsAConnectionFailure() =
        runTest {
            val status = LivePairingService().pair(client { emptyList() }, params) {}
            assertEquals(PairingStatus.Failed(PairingError.ConnectionFailed), status)
        }

    @Test
    fun sendsTheGivenDeviceNameInBothRequests() =
        runTest {
            val transport =
                MockTransport { frame ->
                    listOf(
                        if (Frames.method(frame) ==
                            "authenticateDevice"
                        ) {
                            Frames.error(Frames.id(frame), 401)
                        } else {
                            Frames.pairing(Frames.id(frame))
                        },
                    )
                }
            LivePairingService().pair(MuxyClient(transport, backgroundScope).also { it.start() }, AuthParams("d", "Saeed's Pixel", "t")) {}
            assertEquals(
                listOf("Saeed's Pixel", "Saeed's Pixel"),
                transport.sentFrames.map {
                    Frames
                        .params(it)
                        .getValue("deviceName")
                        .toString()
                        .trim('"')
                },
            )
        }

    private fun TestScope.client(reply: (String) -> List<String>): MuxyClient =
        MuxyClient(MockTransport(autoReply = reply), backgroundScope).also {
            it.start()
        }
}
