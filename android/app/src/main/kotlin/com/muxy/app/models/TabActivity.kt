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

    val providerName: String?
        get() = provider?.let { knownProviders[it.lowercase()] ?: it.replaceFirstChar(Char::titlecase) }

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

        private val knownProviders =
            mapOf(
                "claude" to "Claude Code",
                "claude-code" to "Claude Code",
                "claudecode" to "Claude Code",
                "codex" to "Codex",
                "copilot" to "Copilot",
                "cursor" to "Cursor",
                "droid" to "Droid",
                "grok" to "Grok",
                "kiro" to "Kiro",
                "opencode" to "OpenCode",
                "antigravity" to "Antigravity",
            )
    }
}
