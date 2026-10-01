package com.muxy.app.features.server

import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.networking.server.ServerProject

sealed interface ServerPhase {
    data object Idle : ServerPhase

    data object Connecting : ServerPhase

    data object Connected : ServerPhase

    data class Reconnecting(
        val reason: ReconnectReason,
    ) : ServerPhase

    data class Failed(
        val failure: ServerFailure,
    ) : ServerPhase

    val isConnectionLost: Boolean
        get() = this is Reconnecting || this is Failed
}

sealed interface ReconnectReason {
    data object ServerRestarting : ReconnectReason

    data class Lost(
        val failure: ServerFailure,
        val attempt: Int,
    ) : ReconnectReason
}

data class ProjectCatalog(
    val projects: List<ServerProject> = emptyList(),
    val hasLoaded: Boolean = false,
    val loadFailed: Boolean = false,
)
