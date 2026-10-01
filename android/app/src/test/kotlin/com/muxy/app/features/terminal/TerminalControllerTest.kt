package com.muxy.app.features.terminal

import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyModifiers
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import com.muxy.app.features.terminalkit.TerminalModifier
import com.muxy.app.testing.FakeTerminalSource
import com.muxy.app.testing.RecordingDisplay
import com.muxy.app.testing.RecordingDisplay.Companion.PREPARE
import com.muxy.app.testing.RecordingDisplay.Companion.REFRESH
import com.muxy.app.testing.StubScrollback
import com.muxy.app.testing.lines
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalControllerTest {
    private val source = FakeTerminalSource()
    private val display = RecordingDisplay()

    private fun TestScope.controller(): TerminalController =
        TerminalController(source, backgroundScope).also {
            it.currentFrame()
            it.display = display
            it.setFollowing(false)
        }

    private fun TestScope.expectViewportPreparation(action: (TerminalController) -> Unit) {
        val controller = controller()
        action(controller)
        assertEquals(listOf(PREPARE, REFRESH), display.events)
        assertTrue(controller.isFollowing.value)
    }

    @Test
    fun returningToLivePreparesTheViewportBeforeRefreshing() = runTest { expectViewportPreparation { it.returnToLive() } }

    @Test
    fun typingPreparesTheViewportBeforeRefreshing() = runTest { expectViewportPreparation { it.sendText("hello") } }

    @Test
    fun pressingAKeyPreparesTheViewportBeforeRefreshing() =
        runTest { expectViewportPreparation { it.send(TerminalKeyStroke(TerminalKey.Enter)) } }

    @Test
    fun pastingPreparesTheViewportBeforeRefreshing() = runTest { expectViewportPreparation { it.paste("hello") } }

    @Test
    fun ordinaryOutputDoesNotRearmViewportFollowing() =
        runTest {
            val controller = controller()
            source.listener?.screenDidChange()
            assertEquals(listOf(REFRESH), display.events)
            assertFalse(controller.isFollowing.value)
        }

    @Test
    fun theStickyModifierAppliesToTheNextKeyOnly() =
        runTest {
            val controller = controller()
            controller.toggleModifier()
            controller.send(TerminalKeyStroke(TerminalKey.Escape))
            controller.send(TerminalKeyStroke(TerminalKey.Escape))
            assertEquals(
                listOf(TerminalKeyStroke(TerminalKey.Escape, TerminalKeyModifiers.CONTROL), TerminalKeyStroke(TerminalKey.Escape)),
                source.strokes,
            )
            assertFalse(controller.sticky.value.armed)
        }

    @Test
    fun theStickyModifierTurnsOnlyASingleCharacterIntoAKey() =
        runTest {
            val controller = controller()
            controller.selectModifier(TerminalModifier.ALT)
            controller.toggleModifier()
            controller.sendText("x")
            controller.toggleModifier()
            controller.sendText("ls")
            assertEquals(listOf(TerminalKeyStroke(TerminalKey.Character("x"), TerminalKeyModifiers.ALT)), source.strokes)
            assertEquals(listOf("ls"), source.texts)
            assertTrue(controller.sticky.value.armed)
        }

    @Test
    fun aStickyModifierTurnsAKeyboardNewlineIntoEnter() =
        runTest {
            val controller = controller()
            controller.toggleModifier()
            controller.sendText("\n")
            assertEquals(listOf(TerminalKeyStroke(TerminalKey.Enter, TerminalKeyModifiers.CONTROL)), source.strokes)
        }

    @Test
    fun stickyShiftUppercasesTheCharacter() =
        runTest {
            val controller = controller()
            controller.selectModifier(TerminalModifier.SHIFT)
            controller.toggleModifier()
            controller.sendText("q")
            assertEquals(listOf(TerminalKeyStroke(TerminalKey.Character("Q"))), source.strokes)
        }

    @Test
    fun choosingAModifierDisarmsIt() =
        runTest {
            val controller = controller()
            controller.toggleModifier()
            controller.selectModifier(TerminalModifier.SHIFT)
            assertEquals(TerminalModifier.SHIFT, controller.sticky.value.active)
            assertFalse(controller.sticky.value.armed)
        }

    @Test
    fun anOversizedPasteShowsANoticeInstead() =
        runTest {
            val controller = controller()
            controller.paste("x".repeat(TerminalController.MAXIMUM_PASTE_BYTES + 1))
            assertTrue(source.pastes.isEmpty())
            assertEquals(TerminalController.PASTE_TOO_LARGE, controller.notice.value)
            advanceTimeBy(4_001)
            assertNull(controller.notice.value)
        }

    @Test
    fun viewportChangesResizeOnlyUsableNewGrids() =
        runTest {
            val controller = controller()
            controller.viewportDidChange(TerminalGridSize(19, 30))
            controller.viewportDidChange(TerminalGridSize(80, 24))
            controller.viewportDidChange(TerminalGridSize(80, 24))
            assertEquals(listOf(TerminalGridSize(80, 24)), source.sizes)
        }

    @Test
    fun aPrefetchedHistoryOpensWithTheScreenRowsSeparated() =
        runTest(UnconfinedTestDispatcher()) {
            source.scrollback =
                StubScrollback(lines("h1", "h2", "s1", "s2"), historyRows = 2, olderPages = listOf(Result.success(emptyList())))
            val controller = controller()
            controller.prefetchHistory()
            val history = checkNotNull(controller.enterPrefetchedHistory())
            assertEquals(2, history.historyRowCount)
            assertEquals(TerminalMode.HISTORY, controller.mode.value)
            assertFalse(controller.isFollowing.value)
        }

    @Test
    fun reachingTheStartOfHistoryAtTheTopShowsTheNotice() =
        runTest(UnconfinedTestDispatcher()) {
            source.scrollback = StubScrollback(lines("h1", "s1", "s2"), historyRows = 1, olderPages = listOf(Result.success(emptyList())))
            val controller = controller()
            assertNotNull(controller.enterHistory())
            controller.setAtHistoryTop(true)
            assertFalse(controller.showsHistoryStart.value)
            assertEquals(0, controller.loadOlderHistory())
            assertTrue(controller.showsHistoryStart.value)
            controller.returnToLive()
            assertFalse(controller.showsHistoryStart.value)
            assertEquals(TerminalMode.LIVE, controller.mode.value)
        }

    @Test
    fun anOutdatedPrefetchIsDiscarded() =
        runTest(UnconfinedTestDispatcher()) {
            source.scrollback = StubScrollback(lines("h1", "s1"), historyRows = 1, olderPages = emptyList())
            val controller = controller()
            controller.prefetchHistory()
            source.listener?.screenDidChange()
            controller.discardOutdatedHistoryPrefetch()
            assertNull(controller.enterPrefetchedHistory())
        }
}
