package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavBackStack
import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.parseUuid
import com.muxy.app.features.billing.BillingEnforcement
import com.muxy.app.features.billing.Entitlement
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.features.tabs.TabsSelection
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind

internal fun NavBackStack<AppRoute>.openConnection(
    connection: Connection,
    enforcement: BillingEnforcement,
    entitlement: Entitlement,
) {
    if (connection.requiresUnlock(enforcement, entitlement)) {
        open(AppRoute.Paywall)
        return
    }
    when (connection.kind) {
        ConnectionKind.DEVICE -> {
            open(AppRoute.Projects(connection.id))
        }

        ConnectionKind.SERVER -> {
            val serverId = connection.serverRouteId
            if (serverId == null) {
                Log.connection.error("A Muxy 2 connection has no server id")
                return
            }
            open(AppRoute.ServerProjects(connection.id, serverId))
        }

        ConnectionKind.SSH -> {
            open(AppRoute.SshTerminal(connection.id))
        }
    }
}

internal fun NavBackStack<AppRoute>.openTab(
    selection: TabsSelection,
    enforcement: BillingEnforcement,
    entitlement: Entitlement,
) {
    val connection = selection.connection
    if (connection.requiresUnlock(enforcement, entitlement)) {
        open(AppRoute.Paywall)
        return
    }
    val project = selection.project
    val route =
        when (connection.kind) {
            ConnectionKind.DEVICE -> {
                AppRoute.ProjectDetail(connection.id, parseUuid(project.id) ?: return, project.name, selection.tabId)
            }

            ConnectionKind.SERVER -> {
                AppRoute.ServerProject(connection.id, connection.serverRouteId ?: return, project.id, selection.tabId)
            }

            ConnectionKind.SSH -> {
                return
            }
        }
    open(route)
}

private fun Connection.requiresUnlock(
    enforcement: BillingEnforcement,
    entitlement: Entitlement,
): Boolean = (id != DemoConnection.id || kind != ConnectionKind.DEVICE) && enforcement.gates(entitlement)
