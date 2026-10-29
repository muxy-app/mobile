package com.muxy.app.models

import java.time.Instant

data class TabActivity(
    val state: State,
    val provider: String?,
    val since: Instant?,
) {
    enum class State {
        IDLE,
        WORKING,
        WAITING,
        FINISHED,
    }

    val isAgent: Boolean
        get() = provider != null

    val needsAttention: Boolean
        get() = state == State.WAITING || state == State.FINISHED

    val agentProvider: AgentProvider?
        get() = provider?.let(AgentProvider::fromName)

    val providerName: String?
        get() = agentProvider?.displayName ?: provider

    val statusLabel: String?
        get() =
            when (state) {
                State.IDLE -> providerName
                State.WORKING -> "Working"
                State.WAITING -> "Waiting for you"
                State.FINISHED -> "Finished"
            }

    companion object {
        val None = TabActivity(State.IDLE, provider = null, since = null)
    }
}
