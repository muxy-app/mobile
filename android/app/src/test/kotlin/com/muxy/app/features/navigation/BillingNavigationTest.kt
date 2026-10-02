@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavBackStack
import com.muxy.app.features.addconnection.AddConnectionInbox
import com.muxy.app.features.addconnection.AddConnectionRequest
import com.muxy.app.features.billing.BillingEnforcement
import com.muxy.app.features.billing.Entitlement
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.networking.muxy1.ConnectionFocus
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.testing.device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingNavigationTest {
    private val connections = ConnectionKind.entries.map { device().copy(kind = it, serverId = "server") } + DemoConnection.connection
    private val entitlements = listOf(Entitlement.Loading, Entitlement.Trial(1), Entitlement.Expired, Entitlement.Unlocked)

    @Test
    fun everyConnectionKindAndDemoUseTheSameGateForEveryEntitlement() {
        for (connection in connections) {
            for (entitlement in entitlements) {
                val stack = NavBackStack<AppRoute>(AppRoute.Connections)
                stack.openConnection(connection, BillingEnforcement(isDebug = false), entitlement)
                val expected =
                    if (entitlement == Entitlement.Expired) {
                        AppRoute.Paywall
                    } else {
                        when (connection.kind) {
                            ConnectionKind.DEVICE -> AppRoute.Projects(connection.id)
                            ConnectionKind.SERVER -> AppRoute.ServerProjects(connection.id, "server")
                            ConnectionKind.SSH -> AppRoute.SshTerminal(connection.id)
                        }
                    }
                assertEquals(listOf(AppRoute.Connections, expected), stack.toList())
            }
        }
    }

    @Test
    fun unenforcedDebugOpensEveryConnectionEvenAfterExpiry() {
        for (connection in connections) {
            val stack = NavBackStack<AppRoute>(AppRoute.Connections)
            stack.openConnection(connection, BillingEnforcement(isDebug = true), Entitlement.Expired)
            assertTrue(stack.last() != AppRoute.Paywall)
            assertEquals(2, stack.size)
        }
    }

    @Test
    fun postPairOpeningAndRepeatedTapsCannotBypassOrDuplicateThePaywall() {
        for (connection in connections) {
            val stack = NavBackStack(AppRoute.Connections, AppRoute.AddConnection)
            stack.close(AppRoute.AddConnection)
            repeat(2) {
                stack.openConnection(connection, BillingEnforcement(isDebug = false), Entitlement.Expired)
            }
            assertEquals(listOf(AppRoute.Connections, AppRoute.Paywall), stack.toList())
        }
    }

    @Test
    fun thePaywallIsASerializableModalThatHoldsTheDeviceConnection() {
        assertTrue(AppRoute.Paywall.isModal)
        val routes: List<AppRoute> = listOf(AppRoute.Connections, AppRoute.Paywall)
        assertEquals(ConnectionFocus.Hold, routes.connectionFocus())
        assertEquals(routes, ProtocolJson.decodeFromString<List<AppRoute>>(ProtocolJson.encodeToString(routes)))
    }

    @Test
    fun aPaywallDefersPairingUntilItCloses() =
        runTest {
            val inbox = AddConnectionInbox()
            val stack = MutableStateFlow<List<AppRoute>>(listOf(AppRoute.Connections, AppRoute.Paywall))
            val delivered = mutableListOf<AddConnectionRequest>()
            backgroundScope.launch { pendingPairingRequests(inbox.request, stack).collect { delivered += it } }
            val request = AddConnectionRequest.PairingCode("pending-code")
            inbox.deliver(request)
            runCurrent()
            assertTrue(delivered.isEmpty())
            assertEquals(request, inbox.request.value)
            stack.value = listOf(AppRoute.Connections)
            runCurrent()
            assertEquals(listOf(request), delivered)
        }

    @Test
    fun aPurchaseFromTheTrialSheetDefersPairingUntilItFinishes() =
        runTest {
            val inbox = AddConnectionInbox()
            val stack = MutableStateFlow<List<AppRoute>>(listOf(AppRoute.Connections))
            val purchasing = MutableStateFlow(true)
            val delivered = mutableListOf<AddConnectionRequest>()
            backgroundScope.launch { pendingPairingRequests(inbox.request, stack, purchasing).collect { delivered += it } }
            inbox.deliver(AddConnectionRequest.PairingCode("first"))
            runCurrent()
            val latest = AddConnectionRequest.PairingCode("latest")
            inbox.deliver(latest)
            runCurrent()
            assertTrue(delivered.isEmpty())
            purchasing.value = false
            runCurrent()
            assertEquals(listOf(latest), delivered)
        }
}
