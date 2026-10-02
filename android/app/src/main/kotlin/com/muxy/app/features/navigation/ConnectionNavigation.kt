package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavBackStack
import com.muxy.app.core.logging.Log
import com.muxy.app.features.billing.BillingEnforcement
import com.muxy.app.features.billing.Entitlement
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind

internal fun NavBackStack<AppRoute>.openConnection(
    connection: Connection,
    enforcement: BillingEnforcement,
    entitlement: Entitlement,
) {
    if (enforcement.gates(entitlement)) {
        open(AppRoute.Paywall)
        return
    }
    when (connection.kind) {
        ConnectionKind.DEVICE -> {
            open(AppRoute.Projects(connection.id))
        }

        ConnectionKind.SERVER -> {
            val serverId = connection.serverId
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
