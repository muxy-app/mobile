package com.muxy.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentProviderTest {
    @Test
    fun matchesKnownNames() {
        mapOf(
            "Claude Code" to AgentProvider.CLAUDE,
            "claude-code" to AgentProvider.CLAUDE,
            "claude" to AgentProvider.CLAUDE,
            "Codex" to AgentProvider.CODEX,
            "OpenCode" to AgentProvider.OPEN_CODE,
            "GitHub Copilot" to AgentProvider.COPILOT,
            "Factory" to AgentProvider.DROID,
            "Oh My Pi" to AgentProvider.OH_MY_PI,
            "omp" to AgentProvider.OH_MY_PI,
            "Antigravity" to AgentProvider.ANTIGRAVITY,
        ).forEach { (name, provider) -> assertEquals(name, provider, AgentProvider.fromName(name)) }
    }

    @Test
    fun unknownNamesDoNotMatch() {
        assertNull(AgentProvider.fromName("Gemini CLI"))
        assertNull(AgentProvider.fromName(""))
    }

    @Test
    fun activityUsesTheProviderDisplayName() {
        assertEquals("Claude Code", TabActivity(TabActivity.State.IDLE, "claude", null).providerName)
        assertEquals("Gemini CLI", TabActivity(TabActivity.State.IDLE, "Gemini CLI", null).providerName)
        assertNull(TabActivity.None.providerName)
    }

    @Test
    fun statusLabels() {
        fun label(state: TabActivity.State) = TabActivity(state, "Claude Code", null).statusLabel
        assertEquals("Claude Code", label(TabActivity.State.IDLE))
        assertEquals("Working", label(TabActivity.State.WORKING))
        assertEquals("Waiting for you", label(TabActivity.State.WAITING))
        assertEquals("Finished", label(TabActivity.State.FINISHED))
    }
}
