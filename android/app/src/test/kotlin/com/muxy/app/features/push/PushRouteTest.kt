package com.muxy.app.features.push

import androidx.navigation3.runtime.NavBackStack
import com.muxy.app.features.billing.BillingEnforcement
import com.muxy.app.features.billing.Entitlement
import com.muxy.app.features.navigation.AppRoute
import com.muxy.app.features.navigation.openPush
import com.muxy.app.features.navigation.serverFocus
import com.muxy.app.features.projectdetail.ProjectTool
import com.muxy.app.features.projectdetail.ToolProject
import com.muxy.app.features.server.ServerFocus
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.ServerTransport
import com.muxy.app.testing.device
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PushRouteTest {
    private val paired = device(name = "Studio").copy(kind = ConnectionKind.SERVER, serverId = PushVectors.SERVER_ID)
    private val overSsh =
        device(
            name = "Studio over SSH",
        ).copy(kind = ConnectionKind.SERVER, serverId = PushVectors.SERVER_ID, serverTransport = ServerTransport.SSH)
    private val unlocked = BillingEnforcement(isDebug = true)

    private fun open(
        request: PushOpenRequest,
        enforcement: BillingEnforcement = unlocked,
        entitlement: Entitlement = Entitlement.Unlocked,
        stack: NavBackStack<AppRoute> = NavBackStack(AppRoute.Connections),
    ): List<AppRoute> {
        stack.openPush(request, listOf(overSsh, paired), enforcement, entitlement)
        return stack.toList()
    }

    @Test
    fun aTapOpensTheSessionOnItsPairedServer() {
        assertEquals(
            listOf(AppRoute.Connections, AppRoute.ServerSession(paired.id, PushVectors.SERVER_ID, PushVectors.target)),
            open(PushOpenRequest(PushVectors.target)),
        )
    }

    @Test
    fun aTapWhileAnotherPaneIsOpenSwitchesToTheTarget() {
        val stack =
            NavBackStack<AppRoute>(
                AppRoute.Connections,
                AppRoute.ServerProject(paired.id, PushVectors.SERVER_ID, "other"),
                AppRoute.Settings,
            )
        assertEquals(
            listOf(AppRoute.Connections, AppRoute.ServerSession(paired.id, PushVectors.SERVER_ID, PushVectors.target)),
            open(PushOpenRequest(PushVectors.target), stack = stack),
        )
    }

    @Test
    fun unreadableNotificationsAndUnknownServersOpenHome() {
        assertEquals(listOf(AppRoute.Connections), open(PushOpenRequest(null)))
        assertEquals(listOf(AppRoute.Connections), open(PushOpenRequest(PushVectors.target.copy(serverId = "unknown"))))
    }

    @Test
    fun anExpiredTrialAsksToUnlockFirst() {
        assertEquals(
            listOf(AppRoute.Connections, AppRoute.Paywall),
            open(PushOpenRequest(PushVectors.target), BillingEnforcement(isDebug = false), Entitlement.Expired),
        )
    }

    @Test
    fun theSessionRouteFocusesItsServerAndSession() {
        val session = AppRoute.ServerSession(paired.id, PushVectors.SERVER_ID, PushVectors.target)
        assertEquals(ServerFocus(PushVectors.SERVER_ID, session = PushVectors.target), listOf(AppRoute.Connections, session).serverFocus())
        val tools = AppRoute.ProjectTools(ToolProject.Server(paired.id, PushVectors.SERVER_ID, PushVectors.PROJECT_ID), ProjectTool.FILES)
        assertEquals(
            ServerFocus(PushVectors.SERVER_ID, session = PushVectors.target),
            listOf(AppRoute.Connections, session, tools).serverFocus(),
        )
    }

    @Test
    fun theSessionRouteSurvivesProcessDeath() {
        val route: AppRoute = AppRoute.ServerSession(paired.id, PushVectors.SERVER_ID, PushVectors.target)
        assertEquals(route, Json.decodeFromString(AppRoute.serializer(), Json.encodeToString(AppRoute.serializer(), route)))
    }
}
