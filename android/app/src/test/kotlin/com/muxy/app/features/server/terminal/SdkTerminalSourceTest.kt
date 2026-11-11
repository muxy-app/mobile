package com.muxy.app.features.server.terminal

import com.muxy.app.features.terminal.TerminalCellPosition
import com.muxy.app.features.terminal.TerminalController
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.features.terminal.TerminalMode
import com.muxy.app.features.terminal.TerminalScrollDirection
import com.muxy.app.testing.FakeTerminalChannel
import com.muxy.app.testing.StubScrollbackSnapshot
import com.muxy.app.testing.sdkLine
import com.muxy.app.testing.sdkScreen
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class SdkTerminalSourceTest {
    private val size = TerminalGridSize(columns = 50, rows = 20)
    private var viewportChanges = 0

    private val phoneShown = TerminalGridSize(columns = 80, rows = 24)

    private fun TestScope.attached(
        channel: FakeTerminalChannel = FakeTerminalChannel(1u),
        askedFor: TerminalGridSize = phoneShown,
        viewport: TerminalGridSize? = null,
    ): Pair<SdkTerminalSource, TerminalController> {
        val source = SdkTerminalSource(backgroundScope, serverName = { "Studio" }) { viewportChanges += 1 }
        val controller = TerminalController(source, backgroundScope)
        viewport?.let(controller::viewportDidChange)
        source.attach(channel, askedFor)
        controller.currentFrame()
        return source to controller
    }

    private fun desktopSized() = FakeTerminalChannel(1u, sdkScreen(columns = 120, rows = 40))

    private fun history(snapshot: StubScrollbackSnapshot) =
        FakeTerminalChannel(1u, sdkScreen(historyRows = 3, rows = 1, lines = listOf(sdkLine("s1"))), ArrayDeque(listOf(snapshot)))

    @Test
    fun theFrameComesFromTheLatestScreen() =
        runTest {
            val channel = FakeTerminalChannel(1u)
            val (source) = attached(channel)
            channel.currentScreen = sdkScreen(title = "htop", columns = 120)
            assertEquals(120, source.frame()?.columns)
        }

    @Test
    fun aReleasedTerminalKeepsShowingItsLastScreen() =
        runTest {
            val channel = FakeTerminalChannel(1u, sdkScreen(title = "vim"))
            val (source) = attached(channel)
            assertEquals(channel, source.release())
            channel.currentScreen = sdkScreen(title = "gone")
            assertEquals("vim", source.frame()?.title)
        }

    @Test
    fun aFittedTerminalFollowsANewViewportAfterADebounce() =
        runTest {
            val channel = FakeTerminalChannel(1u)
            val (_, controller) = attached(channel)
            controller.viewportDidChange(size)
            controller.viewportDidChange(TerminalGridSize(60, 20))
            advanceTimeBy(100.milliseconds)
            assertTrue(channel.resizes.isEmpty())
            advanceTimeBy(50.milliseconds)
            assertEquals(listOf(60 to 20), channel.resizes)
            assertEquals(2, viewportChanges)
        }

    @Test
    fun aViewportThatMatchesTheScreenIsNotResized() =
        runTest {
            val channel = FakeTerminalChannel(1u, sdkScreen(columns = 50, rows = 20))
            val (_, controller) = attached(channel, askedFor = size)
            controller.viewportDidChange(size)
            advanceTimeBy(200.milliseconds)
            assertTrue(channel.resizes.isEmpty())
        }

    @Test
    fun aFittedSizeIsNotResizedAgain() =
        runTest {
            val channel = FakeTerminalChannel(1u)
            val (_, controller) = attached(channel)
            controller.viewportDidChange(size)
            advanceTimeBy(200.milliseconds)
            controller.viewportDidChange(TerminalGridSize(60, 20))
            controller.viewportDidChange(size)
            advanceTimeBy(200.milliseconds)
            assertEquals(listOf(50 to 20), channel.resizes)
        }

    @Test
    fun aFailedResizeIsRetriedOnTheNextChange() =
        runTest {
            val channel = FakeTerminalChannel(1u).apply { failingResizes = 1 }
            val (_, controller) = attached(channel)
            controller.viewportDidChange(size)
            advanceTimeBy(200.milliseconds)
            controller.viewportDidChange(TerminalGridSize(60, 20))
            controller.viewportDidChange(size)
            advanceTimeBy(200.milliseconds)
            assertEquals(listOf(50 to 20, 50 to 20), channel.resizes)
        }

    @Test
    fun attachingToADesktopSizedSessionKeepsItsSize() =
        runTest {
            val channel = desktopSized()
            val (source, controller) = attached(channel, askedFor = size, viewport = size)
            controller.viewportDidChange(TerminalGridSize(60, 20))
            advanceTimeBy(200.milliseconds)
            assertTrue(channel.resizes.isEmpty())
            assertFalse(source.isFitted)
            assertTrue(controller.canFitToPhone.value)
        }

    @Test
    fun attachingAtThePhoneSizeCountsAsFitted() =
        runTest {
            val channel = FakeTerminalChannel(1u, sdkScreen(columns = 50, rows = 20))
            val (source, controller) = attached(channel, askedFor = size, viewport = size)
            assertTrue(source.isFitted)
            assertFalse(controller.canFitToPhone.value)
        }

    @Test
    fun fittingToThePhoneResizesAndFollowsTheViewport() =
        runTest {
            val channel = desktopSized()
            val (source, controller) = attached(channel, askedFor = size, viewport = size)
            controller.fitToPhone()
            runCurrent()
            assertEquals(listOf(50 to 20), channel.resizes)
            assertTrue(source.isFitted)
            assertFalse(controller.canFitToPhone.value)
            controller.viewportDidChange(TerminalGridSize(60, 20))
            advanceTimeBy(200.milliseconds)
            assertEquals(listOf(50 to 20, 60 to 20), channel.resizes)
        }

    @Test
    fun aFailedFitShowsANoticeAndCanBeTriedAgain() =
        runTest {
            val channel = desktopSized().apply { failingResizes = 1 }
            val (source, controller) = attached(channel, askedFor = size, viewport = size)
            controller.fitToPhone()
            runCurrent()
            assertEquals(TerminalController.FIT_FAILED, controller.notice.value)
            assertFalse(source.isFitted)
            assertTrue(controller.canFitToPhone.value)
            controller.fitToPhone()
            runCurrent()
            assertEquals(listOf(50 to 20, 50 to 20), channel.resizes)
            assertTrue(source.isFitted)
        }

    @Test
    fun theDesktopTakingTheSizeBackKeepsTheFitButtonHidden() =
        runTest {
            val channel = desktopSized()
            val (source, controller) = attached(channel, askedFor = size, viewport = size)
            controller.fitToPhone()
            runCurrent()
            channel.currentScreen = sdkScreen(columns = 120, rows = 40)
            source.frame()
            assertFalse(controller.canFitToPhone.value)
            assertEquals(listOf(50 to 20), channel.resizes)
        }

    @Test
    fun aFittedTerminalIsFittedAgainWhenReattached() =
        runTest {
            val first = desktopSized()
            val (source, controller) = attached(first, askedFor = size, viewport = size)
            controller.fitToPhone()
            runCurrent()
            source.release()
            val second = desktopSized()
            source.attach(second, size)
            runCurrent()
            assertEquals(listOf(50 to 20), second.resizes)
            assertFalse(controller.canFitToPhone.value)
        }

    @Test
    fun aReleasedTerminalCannotBeFitted() =
        runTest {
            val (source, controller) = attached(desktopSized(), askedFor = size, viewport = size)
            source.release()
            assertFalse(controller.canFitToPhone.value)
        }

    @Test
    fun theViewportIsKnownBeforeAttaching() =
        runTest {
            val source = SdkTerminalSource(backgroundScope, serverName = { "Studio" }) { viewportChanges += 1 }
            TerminalController(source, backgroundScope).viewportDidChange(size)
            assertEquals(size, source.viewportSize)
            assertEquals(1, viewportChanges)
        }

    @Test
    fun tapsAndScrollingReachTheProgramAtTheCell() =
        runTest {
            val channel = FakeTerminalChannel(1u)
            val (_, controller) = attached(channel)
            controller.click(TerminalCellPosition(row = 3, column = 10))
            controller.scroll(TerminalScrollDirection.UP, TerminalCellPosition(row = 4, column = 2))
            assertEquals(listOf("LEFT@3,10"), channel.clicks)
            assertEquals(listOf("UP@4,2"), channel.scrolls)
            assertFalse(controller.forwardScroll(12.0))
        }

    @Test
    fun aStaleHistoryPrefetchIsDiscardedAndClosed() =
        runTest {
            val snapshot = StubScrollbackSnapshot(listOf(sdkLine("h1"), sdkLine("s1")), historyRows = 3)
            val (source, controller) = attached(history(snapshot))
            controller.prefetchHistory()
            runCurrent()
            source.screenDidChange()
            controller.discardOutdatedHistoryPrefetch()
            assertTrue(snapshot.isClosed)
            assertNull(controller.enterPrefetchedHistory())
        }

    @Test
    fun aFreshHistoryPrefetchOpensAtOnce() =
        runTest {
            val snapshot = StubScrollbackSnapshot(listOf(sdkLine("h1"), sdkLine("s1")), historyRows = 3)
            val (_, controller) = attached(history(snapshot))
            controller.prefetchHistory()
            runCurrent()
            controller.discardOutdatedHistoryPrefetch()
            val document = controller.enterPrefetchedHistory()
            assertNotNull(document)
            assertEquals(1, document!!.historyRowCount)
            assertFalse(snapshot.isClosed)
        }

    @Test
    fun leavingHistoryClosesItsSnapshot() =
        runTest {
            val snapshot = StubScrollbackSnapshot(listOf(sdkLine("h1"), sdkLine("s1")), historyRows = 3)
            val (_, controller) = attached(history(snapshot))
            assertNotNull(controller.enterHistory())
            controller.returnToLive()
            assertTrue(snapshot.isClosed)
        }

    @Test
    fun aSnapshotThatArrivesAfterLeavingIsClosed() =
        runTest {
            val snapshot = StubScrollbackSnapshot(listOf(sdkLine("h1"), sdkLine("s1")), historyRows = 3)
            val (_, controller) = attached(history(snapshot))
            controller.prefetchHistory()
            controller.returnToLive()
            runCurrent()
            assertTrue(snapshot.isClosed)
        }

    @Test
    fun aProgramThatTakesTheMouseDropsOpenHistory() =
        runTest {
            val snapshot = StubScrollbackSnapshot(listOf(sdkLine("h1"), sdkLine("s1")), historyRows = 3)
            val channel = history(snapshot)
            val (source, controller) = attached(channel)
            controller.enterHistory()
            assertEquals(TerminalMode.HISTORY, controller.mode.value)
            source.metadataDidChange()
            assertEquals(TerminalMode.HISTORY, controller.mode.value)
            channel.currentScreen = channel.currentScreen.copy(alternateScroll = true)
            source.metadataDidChange()
            assertEquals(TerminalMode.LIVE, controller.mode.value)
            assertTrue(snapshot.isClosed)
        }

    @Test
    fun olderHistoryLinesAreMapped() =
        runTest {
            val snapshot =
                StubScrollbackSnapshot(
                    listOf(sdkLine("h2"), sdkLine("s1")),
                    historyRows = 3,
                    olderPages = listOf(Result.success(listOf(sdkLine("h1")))),
                )
            val (_, controller) = attached(history(snapshot))
            val document = controller.enterHistory()!!
            assertEquals(1, controller.loadOlderHistory())
            assertEquals(3, document.lines.size)
            assertEquals(
                "h1",
                document.lines
                    .first()
                    .spans
                    .single()
                    .text,
            )
        }
}
