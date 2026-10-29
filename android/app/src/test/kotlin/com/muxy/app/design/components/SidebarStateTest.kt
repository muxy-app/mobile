package com.muxy.app.design.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarStateTest {
    @Test
    fun toggleFlipsVisibility() {
        val state = SidebarState(isVisible = true)
        state.toggle()
        assertFalse(state.isVisible)
        state.toggle()
        assertTrue(state.isVisible)
    }

    @Test
    fun clearingAnotherTabKeepsTheActiveOne() {
        val state = SidebarState(isVisible = true)
        state.select("agent")
        state.clear("nvim")
        assertEquals("agent", state.activeTabId)
    }

    @Test
    fun clearingTheActiveTabRemovesIt() {
        val state = SidebarState(isVisible = true)
        state.select("agent")
        state.clear("agent")
        assertNull(state.activeTabId)
    }

    @Test
    fun aNewlySelectedTabSurvivesThePreviousOneBeingCleared() {
        val state = SidebarState(isVisible = true)
        state.select("agent")
        state.select("nvim")
        state.clear("agent")
        assertEquals("nvim", state.activeTabId)
    }
}
