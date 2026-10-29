package com.muxy.app.features.server

import com.muxy.app.models.TabActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.muxy_mobile.Activity
import uniffi.muxy_mobile.ActivityEvent
import uniffi.muxy_mobile.ActivityKind
import uniffi.muxy_mobile.Agent
import uniffi.muxy_mobile.AgentState
import java.time.Instant

class TabActivitySdkTest {
    private fun agent(
        session: ULong,
        state: AgentState,
        provider: String = "Claude Code",
    ) = Agent(sessionId = session, projectId = "muxy", provider = provider, state = state)

    private fun event(
        id: ULong,
        session: ULong,
        kind: ActivityKind,
        at: ULong,
        provider: String = "Codex",
    ) = ActivityEvent(id = id, sessionId = session, projectId = "muxy", provider = provider, timestamp = at, kind = kind)

    @Test
    fun agentStateWinsOverEvents() {
        assertEquals(TabActivity.State.WAITING, TabActivitySdk.resolve(agent(1u, AgentState.BLOCKED), null).state)
        assertEquals(
            TabActivity.State.WORKING,
            TabActivitySdk.resolve(agent(1u, AgentState.WORKING), event(1u, 1u, ActivityKind.COMPLETED, 1u)).state,
        )
    }

    @Test
    fun eventsDecideIdleAgents() {
        assertEquals(
            TabActivity.State.FINISHED,
            TabActivitySdk.resolve(agent(1u, AgentState.IDLE), event(1u, 1u, ActivityKind.COMPLETED, 1u)).state,
        )
        assertEquals(TabActivity.State.WAITING, TabActivitySdk.resolve(null, event(1u, 1u, ActivityKind.ATTENTION, 1u)).state)
        assertEquals(TabActivity.State.IDLE, TabActivitySdk.resolve(agent(1u, AgentState.IDLE), null).state)
    }

    @Test
    fun providerPrefersTheAgentAndTimeComesFromTheEvent() {
        val activity = TabActivitySdk.resolve(agent(1u, AgentState.IDLE), event(1u, 1u, ActivityKind.COMPLETED, 1_700_000_000u))

        assertEquals("Claude Code", activity.provider)
        assertEquals(Instant.ofEpochSecond(1_700_000_000), activity.since)
        assertEquals("Codex", TabActivitySdk.resolve(null, event(1u, 1u, ActivityKind.COMPLETED, 1u)).provider)
    }

    @Test
    fun bySessionKeepsTheLatestEventPerSession() {
        val activity =
            Activity(
                agents = listOf(agent(1u, AgentState.IDLE)),
                events =
                    listOf(
                        event(1u, 1u, ActivityKind.ATTENTION, 10u),
                        event(2u, 1u, ActivityKind.COMPLETED, 20u),
                        event(3u, 2u, ActivityKind.ATTENTION, 5u),
                    ),
            )

        val sessions = TabActivitySdk.bySession(activity)

        assertEquals(TabActivity.State.FINISHED, sessions[1u]?.state)
        assertEquals(TabActivity.State.WAITING, sessions[2u]?.state)
        assertEquals(2, sessions.size)
    }
}
