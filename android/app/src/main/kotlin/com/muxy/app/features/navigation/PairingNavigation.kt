package com.muxy.app.features.navigation

import com.muxy.app.features.addconnection.AddConnectionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

internal fun pendingPairingRequests(
    requests: Flow<AddConnectionRequest?>,
    routes: Flow<List<AppRoute>>,
): Flow<AddConnectionRequest> =
    combine(requests, routes) { request, stack ->
        request?.takeIf { stack.none { it is AppRoute.ProjectTools } }
    }.distinctUntilChanged().filterNotNull()
