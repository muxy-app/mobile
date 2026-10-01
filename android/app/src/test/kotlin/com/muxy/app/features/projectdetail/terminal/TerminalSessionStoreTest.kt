package com.muxy.app.features.projectdetail.terminal

import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.models.Tab
import com.muxy.app.models.TabKind
import com.muxy.app.networking.muxy1.ConnectionState
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.testing.MockTerminalChannel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.UUID

class TerminalSessionStoreTest {
    private val channel = MockTerminalChannel()
    private val first = Tab(UUID.randomUUID(), TabKind.Terminal, "zsh", false, UUID.randomUUID())
    private val second = Tab(UUID.randomUUID(), TabKind.Terminal, "dev", false, UUID.randomUUID())
    private val git = Tab(UUID.randomUUID(), TabKind.Vcs, "Git", false, UUID.randomUUID())
    private val tabs = listOf(first, second, git)

    private fun TestScope.store() =
        TerminalSessionStore(channel, backgroundScope, backgroundScope) {}.apply {
            connectionChanged(ConnectionState.Connected, 1)
            listOf(first, second).forEach { checkNotNull(session(it)).source.resize(TerminalGridSize(80, 24)) }
        }

    private fun test(body: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { body() }

    @Test
    fun onlyTerminalTabsWithAPaneHaveASession() =
        test {
            val store = store()
            assertSame(store.session(first), store.session(first))
            assertNull(store.session(git))
            assertNull(store.session(first.copy(paneId = null)))
        }

    @Test
    fun theSelectedTabTakesOverAndSwitchingReleasesIt() =
        test {
            val store = store()
            store.selectionChanged(first.id, tabs)
            store.selectionChanged(second.id, tabs)
            assertEquals(2, channel.requests(Method.TAKE_OVER_PANE).size)
            assertEquals(1, channel.notifications(Method.RELEASE_PANE).size)
            assertEquals(TerminalOwnership.Idle, store.session(first)?.ownership?.value)
            assertEquals(TerminalOwnership.Owned, store.session(second)?.ownership?.value)
        }

    @Test
    fun selectingANonTerminalTabReleasesTheActivePane() =
        test {
            val store = store()
            store.selectionChanged(first.id, tabs)
            store.selectionChanged(git.id, tabs)
            assertEquals(1, channel.notifications(Method.RELEASE_PANE).size)
        }

    @Test
    fun closedTabsAreReleasedAndForgotten() =
        test {
            val store = store()
            store.selectionChanged(first.id, tabs)
            val session = store.session(first)
            store.tabsChanged(listOf(second))
            assertEquals(1, channel.notifications(Method.RELEASE_PANE).size)
            store.tabsChanged(tabs)
            assertEquals(false, session === store.session(first))
        }

    @Test
    fun connectionChangesReachTheActiveSession() =
        test {
            val store = store()
            store.selectionChanged(first.id, tabs)
            store.connectionChanged(ConnectionState.Disconnected, null)
            assertEquals(TerminalOwnership.Disconnected, store.session(first)?.ownership?.value)
            assertEquals(TerminalOwnership.Idle, store.session(second)?.ownership?.value)
        }

    @Test
    fun teardownReleasesOnlyTheActivePane() =
        test {
            val store = store()
            store.selectionChanged(first.id, tabs)
            store.teardown()
            assertEquals(1, channel.notifications(Method.RELEASE_PANE).size)
        }
}
