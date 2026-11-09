package com.muxy.app.features.push

import android.content.Intent
import java.util.UUID

data class PushOpenRequest(
    val target: PushTarget?,
    val id: UUID = UUID.randomUUID(),
)

object PushIntents {
    const val ACTION_OPEN = "com.muxy.app.action.OPEN_NOTIFICATION"
    const val SERVER_ID = "server_id"
    const val PROJECT_ID = "project_id"
    const val SESSION_ID = "session_id"
    const val EVENT_ID = "event_id"

    fun extras(target: PushTarget): Map<String, String> =
        mapOf(
            SERVER_ID to target.serverId,
            PROJECT_ID to target.projectId,
            SESSION_ID to target.sessionId.toString(),
            EVENT_ID to target.eventId.toString(),
        )

    fun request(
        action: String?,
        flags: Int,
        extra: (String) -> String?,
    ): PushOpenRequest? {
        if (action != ACTION_OPEN) return null
        if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return PushOpenRequest(target(extra))
    }

    private fun target(extra: (String) -> String?): PushTarget? {
        val serverId = extra(SERVER_ID)?.takeIf { it.isNotEmpty() } ?: return null
        val projectId = extra(PROJECT_ID) ?: return null
        val sessionId = extra(SESSION_ID)?.toULongOrNull() ?: return null
        val eventId = extra(EVENT_ID)?.toULongOrNull() ?: return null
        return PushTarget(serverId, sessionId, projectId, eventId)
    }
}
