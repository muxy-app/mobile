package com.muxy.app.features.server

import com.muxy.app.models.TabActivity
import uniffi.muxy_mobile.Activity
import uniffi.muxy_mobile.ActivityEvent
import uniffi.muxy_mobile.ActivityKind
import uniffi.muxy_mobile.Agent
import uniffi.muxy_mobile.AgentState
import java.time.Instant

internal object TabActivitySdk {
    fun resolve(
        agent: Agent?,
        event: ActivityEvent?,
    ): TabActivity =
        TabActivity(
            state = state(agent, event),
            provider = listOfNotNull(agent?.provider, event?.provider).firstOrNull(String::isNotEmpty),
            since = event?.let { Instant.ofEpochSecond(it.timestamp.toLong()) },
        )

    fun bySession(activity: Activity): Map<ULong, TabActivity> {
        val agents = activity.agents.associateBy(Agent::sessionId)
        val events =
            activity.events
                .groupBy(ActivityEvent::sessionId)
                .mapValues { (_, events) -> events.maxBy(ActivityEvent::timestamp) }
        return (agents.keys + events.keys).associateWith { resolve(agents[it], events[it]) }
    }

    private fun state(
        agent: Agent?,
        event: ActivityEvent?,
    ): TabActivity.State =
        when {
            agent?.state == AgentState.BLOCKED -> TabActivity.State.WAITING
            agent?.state == AgentState.WORKING -> TabActivity.State.WORKING
            event?.kind == ActivityKind.ATTENTION -> TabActivity.State.WAITING
            event?.kind == ActivityKind.COMPLETED -> TabActivity.State.FINISHED
            else -> TabActivity.State.IDLE
        }
}
