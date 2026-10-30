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
    inPane: Boolean = false,
) {
    if (connection.requiresUnlock(enforcement, entitlement)) {
        open(AppRoute.Paywall)
        return
    }
    when (connection.kind) {
        ConnectionKind.DEVICE -> {
            show(AppRoute.Projects(connection.id), inPane)
        }

        ConnectionKind.SERVER -> {
            val serverId = connection.serverRouteId
            if (serverId == null) {
                Log.connection.error("A Muxy 2 connection has no server id")
                return
            }
            show(AppRoute.ServerProjects(connection.id, serverId), inPane)
        }

        ConnectionKind.SSH -> {
            show(AppRoute.SshTerminal(connection.id), inPane)
        }
    }
}

internal fun NavBackStack<AppRoute>.openTab(
    selection: TabsSelection,
    enforcement: BillingEnforcement,
    entitlement: Entitlement,
    inPane: Boolean = false,
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
    show(route, inPane)
}

private fun NavBackStack<AppRoute>.show(
    route: AppRoute,
    inPane: Boolean,
) {
    if (inPane) {
        showInPane(route)
        return
    }
    open(route)
}

private fun Connection.requiresUnlock(
    enforcement: BillingEnforcement,
    entitlement: Entitlement,
): Boolean = (id != DemoConnection.id || kind != ConnectionKind.DEVICE) && enforcement.gates(entitlement)
