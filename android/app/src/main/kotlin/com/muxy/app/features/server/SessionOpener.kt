package com.muxy.app.features.server

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.push.PushTarget
import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import uniffi.muxy_mobile.Session
import java.util.UUID

sealed interface SessionOpening {
    val projectId: String

    data class Opened(
        override val projectId: String,
        val tabId: UUID,
    ) : SessionOpening

    data class Ended(
        override val projectId: String,
    ) : SessionOpening

    data class Unavailable(
        override val projectId: String,
    ) : SessionOpening
}

data class SessionFocus(
    val target: PushTarget,
    val opening: SessionOpening? = null,
)

class SessionOpener(
    private val server: ServerController,
) {
    suspend fun open(target: PushTarget): SessionOpening? {
        val retries = server.requestRetrySchedule()
        var stale: ServerConnection? = null
        while (true) {
            val current = nextConnection(stale)
            val result = attempt { current.findSession(target.sessionId) }
            if (current !== server.connection) {
                stale = current
                continue
            }
            val failure = result.exceptionOrNull()?.let(ServerFailure::from) ?: return show(result.getOrNull(), target)
            Log.push.error("Finding a notification's session failed: $failure")
            if (!failure.isTransient) return SessionOpening.Unavailable(target.projectId)
            if (failure == ServerFailure.Disconnected) {
                stale = current
                continue
            }
            delay(retries.nextDelay())
        }
    }

    private fun show(
        session: Session?,
        target: PushTarget,
    ): SessionOpening? {
        if (session == null) {
            server.acknowledgePushEvent(target.eventId, target.sessionId)
            return SessionOpening.Ended(target.projectId)
        }
        val tab = server.projectModel(session.projectId).openTab(session)
        server.acknowledgeWhenShown(target.eventId, session.id)
        return SessionOpening.Opened(session.projectId, tab.id)
    }

    private suspend fun nextConnection(stale: ServerConnection?): ServerConnection =
        server.connectionState
            .filterNotNull()
            .first { it !== stale }

    private val ServerFailure.isTransient: Boolean
        get() = this == ServerFailure.Disconnected || this == ServerFailure.Timeout || this == ServerFailure.Unreachable
}
