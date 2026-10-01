package com.muxy.app.networking.server

import com.muxy.app.networking.server.ServerFailure.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.MobileException
import java.io.IOException

class ServerFailureTest {
    @Test
    fun mapsEveryMobileException() {
        assertEquals(ServerFailure.InvalidLink, ServerFailure.from(MobileException.InvalidLink()))
        assertEquals(ServerFailure.InvalidCredential, ServerFailure.from(MobileException.InvalidCredential()))
        assertEquals(ServerFailure.Unreachable, ServerFailure.from(MobileException.Unreachable("refused")))
        assertEquals(ServerFailure.IdentityMismatch, ServerFailure.from(MobileException.IdentityMismatch()))
        assertEquals(ServerFailure.Unauthorized, ServerFailure.from(MobileException.Unauthorized()))
        assertEquals(ServerFailure.IncompatibleVersion, ServerFailure.from(MobileException.IncompatibleVersion()))
        assertEquals(ServerFailure.Unsupported, ServerFailure.from(MobileException.Unsupported()))
        assertEquals(ServerFailure.Timeout, ServerFailure.from(MobileException.Timeout()))
        assertEquals(ServerFailure.Disconnected, ServerFailure.from(MobileException.Disconnected()))
        assertEquals(ServerFailure.Server("Unknown project."), ServerFailure.from(MobileException.Server("Unknown project.")))
    }

    @Test
    fun errorsFromOutsideTheSdkAreUnknown() {
        assertEquals(ServerFailure.Unknown, ServerFailure.from(IOException("closed")))
    }

    @Test
    fun failuresOnlyTheUserCanFixAreFatal() {
        listOf(
            ServerFailure.InvalidCredential,
            ServerFailure.IdentityMismatch,
            ServerFailure.Unauthorized,
            ServerFailure.IncompatibleVersion,
            ServerFailure.Unsupported,
        ).forEach { assertTrue("$it", it.isFatal) }
    }

    @Test
    fun transientFailuresAreRetried() {
        listOf(
            ServerFailure.Unreachable,
            ServerFailure.Timeout,
            ServerFailure.Disconnected,
            ServerFailure.Server("busy"),
            ServerFailure.Unknown,
            ServerFailure.InvalidLink,
        ).forEach { assertFalse("$it", it.isFatal) }
    }

    @Test
    fun onlyIdentityProblemsAskToPairAgain() {
        assertTrue(ServerFailure.Unauthorized.requiresPairing)
        assertTrue(ServerFailure.IdentityMismatch.requiresPairing)
        assertTrue(ServerFailure.InvalidCredential.requiresPairing)
        assertFalse(ServerFailure.IncompatibleVersion.requiresPairing)
        assertFalse(ServerFailure.Unreachable.requiresPairing)
    }

    @Test
    fun unauthorizedMeansAnExpiredCodeWhilePairing() {
        assertTrue(ServerFailure.Unauthorized.message(Context.PAIRING, "Studio").contains("Show a new code on your computer."))
    }

    @Test
    fun unauthorizedMeansRevokedWhenConnecting() {
        assertEquals("This phone isn't paired with Studio anymore.", ServerFailure.Unauthorized.message(Context.CONNECTING, "Studio"))
    }

    @Test
    fun identityMismatchWhilePairingPointsAtTheWrongComputer() {
        assertEquals(
            "The computer that answered isn't the one showing this code.",
            ServerFailure.IdentityMismatch.message(Context.PAIRING, "Studio"),
        )
    }

    @Test
    fun unreachableNamesTheComputerWhenConnecting() {
        assertTrue(ServerFailure.Unreachable.message(Context.CONNECTING, "Studio").startsWith("Can't reach Studio."))
    }

    @Test
    fun unreachableWhilePairingHasNoIosPermissionHint() {
        assertEquals(
            "Can't reach the computer. Check that it's awake and on the same network or VPN.",
            ServerFailure.Unreachable.message(Context.PAIRING, "Studio"),
        )
    }

    @Test
    fun invalidLinkMatchesTheSpecText() {
        assertEquals("This isn't a Muxy pairing code.", ServerFailure.InvalidLink.message(Context.PAIRING, ""))
    }

    @Test
    fun serverReasonIsShownAsIs() {
        assertEquals("No such project", ServerFailure.Server("No such project").message(Context.REQUEST, "Studio"))
    }
}
