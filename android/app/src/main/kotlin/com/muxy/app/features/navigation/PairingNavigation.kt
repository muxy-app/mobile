package com.muxy.app.features.navigation

import com.muxy.app.features.addconnection.AddConnectionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf

internal fun pendingPairingRequests(
    requests: Flow<AddConnectionRequest?>,
    routes: Flow<List<AppRoute>>,
    purchasing: Flow<Boolean> = flowOf(false),
): Flow<AddConnectionRequest> =
    combine(requests, routes, purchasing) { request, stack, isPurchasing ->
        val blocked = isPurchasing || stack.any { it is AppRoute.ProjectTools || it == AppRoute.Paywall }
        request?.takeUnless { blocked }
    }.distinctUntilChanged().filterNotNull()
