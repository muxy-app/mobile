package com.muxy.app.features.projectdetail.terminal

import com.muxy.app.core.serialization.uuidString
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.features.terminal.TerminalMode
import com.muxy.app.networking.muxy1.ConnectionError
import com.muxy.app.networking.muxy1.ConnectionIdentity
import com.muxy.app.networking.muxy1.ConnectionState
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.PaneOwner
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.ReleasePaneParams
import com.muxy.app.networking.muxy1.protocol.SetClientThemeParams
import com.muxy.app.networking.muxy1.protocol.TakeOverPaneParams
import com.muxy.app.networking.muxy1.protocol.TerminalInputParams
import com.muxy.app.networking.muxy1.protocol.TerminalResizeParams
import com.muxy.app.networking.muxy1.protocol.TerminalScrollParams
import com.muxy.app.networking.muxy1.transport.TransportException
import com.muxy.app.networking.muxy1.transport.TransportFailure
import com.muxy.app.testing.MockTerminalChannel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class TerminalSessionTest {
    private val paneId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()
    private val channel = MockTerminalChannel(ConnectionIdentity(clientId, deviceId))
    private val clock = TestTimeSource()
    private val grid = TerminalGridSize(80, 24)
    private val copied = mutableListOf<String>()

    private fun TestScope.session() = TerminalSession(paneId, channel, backgroundScope, backgroundScope, { copied += it }, clock)

    private fun TestScope.ownedSession(): TerminalSession =
        session().apply {
            activate(ConnectionState.Connected, 1)
            source.resize(grid)
        }

    private fun <T> MockTerminalChannel.Call.decoded(deserializer: DeserializationStrategy<T>): T =
        ProtocolJson.decodeFromJsonElement(deserializer, checkNotNull(params))

    private fun test(body: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { body() }

    @Test
    fun takeoverSendsThePaneAndTheLockedGrid() =
        test {
            val session = ownedSession()
            val takeover = channel.requests(Method.TAKE_OVER_PANE).single().decoded(TakeOverPaneParams.serializer())
            assertEquals(TakeOverPaneParams(paneId.uuidString, 80, 24), takeover)
            assertEquals(TerminalOwnership.Owned, session.ownership.value)
        }

    @Test
    fun takeoverWaitsForTheGrid() =
        test {
            val session = session()
            session.activate(ConnectionState.Connected, 1)
            assertTrue(channel.requests(Method.TAKE_OVER_PANE).isEmpty())
            assertEquals(TerminalOwnership.TakingOver, session.ownership.value)
            session.source.resize(grid)
            assertEquals(1, channel.requests(Method.TAKE_OVER_PANE).size)
        }

    @Test
    fun activatingUsesAGridLockedBeforehand() =
        test {
            val session = session()
            session.source.resize(grid)
            session.activate(ConnectionState.Connected, 1)
            assertEquals(1, channel.requests(Method.TAKE_OVER_PANE).size)
        }

    @Test
    fun activateSendsTheClientTheme() =
        test {
            ownedSession()
            val theme = channel.requests(Method.SET_CLIENT_THEME).single().decoded(SetClientThemeParams.serializer())
            assertEquals(ThemeCatalog.muxy.clientTerminalTheme(), theme.theme)
        }

    @Test
    fun aMissingThemeMethodDoesNotBlockTheTakeover() =
        test {
            channel.requestErrors[Method.SET_CLIENT_THEME] = ProtocolException(ErrorCode.NOT_FOUND.body("Unknown method"))
            val session = ownedSession()
            assertEquals(TerminalOwnership.Owned, session.ownership.value)
        }

    @Test
    fun aNewThemeIsSentOnlyWhileActive() =
        test {
            val session = session()
            val nord = ThemeCatalog.named("Nord").clientTerminalTheme()
            session.useClientTheme(nord)
            assertTrue(channel.requests(Method.SET_CLIENT_THEME).isEmpty())
            session.activate(ConnectionState.Connected, 1)
            session.useClientTheme(ThemeCatalog.named("Dracula").clientTerminalTheme())
            assertEquals(2, channel.requests(Method.SET_CLIENT_THEME).size)
        }

    @Test
    fun theTakeoverHappensOncePerConnection() =
        test {
            val session = ownedSession()
            session.source.resize(grid)
            assertEquals(1, channel.requests(Method.TAKE_OVER_PANE).size)
        }

    @Test
    fun aNewConnectionResendsTheThemeAndTakesOverAgain() =
        test {
            val session = ownedSession()
            session.connectionChanged(ConnectionState.Connecting, null)
            assertEquals(TerminalOwnership.Owned, session.ownership.value)
            session.connectionChanged(ConnectionState.Connected, 2)
            assertEquals(2, channel.requests(Method.SET_CLIENT_THEME).size)
            assertEquals(2, channel.requests(Method.TAKE_OVER_PANE).size)
        }

    @Test
    fun aDroppedConnectionShowsDisconnectedUntilTheNextOne() =
        test {
            val session = ownedSession()
            session.connectionChanged(ConnectionState.Disconnected, null)
            assertEquals(TerminalOwnership.Disconnected, session.ownership.value)
            session.controller.sendText("x")
            assertTrue(channel.notifications(Method.TERMINAL_INPUT).isEmpty())
            session.connectionChanged(ConnectionState.Connected, 2)
            assertEquals(TerminalOwnership.Owned, session.ownership.value)
        }

    @Test
    fun activatingWhileDisconnectedShowsDisconnectedAndSendsNothing() =
        test {
            val session = session()
            session.source.resize(grid)
            session.activate(ConnectionState.Failed(ConnectionError.CONNECTION_FAILED), null)
            assertEquals(TerminalOwnership.Disconnected, session.ownership.value)
            session.controller.sendText("x")
            assertTrue(channel.requests.isEmpty())
            assertTrue(channel.notifications.isEmpty())
        }

    @Test
    fun aFailedTakeoverOffersToTryAgain() =
        test {
            channel.requestErrors[Method.TAKE_OVER_PANE] = TransportException(TransportFailure.TIMED_OUT)
            val session = ownedSession()
            assertEquals(TerminalOwnership.TakeoverFailed(TakeoverFailure.TIMED_OUT), session.ownership.value)
            session.source.resize(TerminalGridSize(90, 30))
            assertEquals(1, channel.requests(Method.TAKE_OVER_PANE).size)
            channel.requestErrors.clear()
            session.takeControl()
            assertEquals(2, channel.requests(Method.TAKE_OVER_PANE).size)
            assertEquals(TerminalOwnership.Owned, session.ownership.value)
        }

    @Test
    fun deactivatingReleasesThePane() =
        test {
            val session = ownedSession()
            session.deactivate()
            val release = channel.notifications(Method.RELEASE_PANE).single().decoded(ReleasePaneParams.serializer())
            assertEquals(paneId.uuidString, release.paneId)
            assertEquals(TerminalOwnership.Idle, session.ownership.value)
            session.deactivate()
            assertEquals(1, channel.notifications(Method.RELEASE_PANE).size)
        }

    @Test
    fun theStickyControlKeyIsAppliedOnce() =
        test {
            val session = ownedSession()
            session.controller.toggleModifier()
            session.controller.sendText("c")
            session.controller.sendText("c")
            val inputs = channel.notifications(Method.TERMINAL_INPUT).map { it.decoded(TerminalInputParams.serializer()).bytes.toList() }
            assertEquals(listOf(listOf<Byte>(0x03), listOf<Byte>(0x63)), inputs)
            assertFalse(session.controller.sticky.value.armed)
        }

    @Test
    fun scrollIsForwardedAsPreciseDesktopScrollWhileOwned() =
        test {
            val session = session()
            assertFalse(session.source.forwardScroll(-18.0))
            session.activate(ConnectionState.Connected, 1)
            session.source.resize(grid)
            assertTrue(session.source.forwardScroll(-18.0))
            val scroll = channel.notifications(Method.TERMINAL_SCROLL).single().decoded(TerminalScrollParams.serializer())
            assertEquals(TerminalScrollParams(paneId.uuidString, 0.0, -18.0, true), scroll)
        }

    @Test
    fun resizesAreDebouncedAndDeduplicated() =
        test {
            val session = ownedSession()
            session.source.resize(TerminalGridSize(70, 20))
            session.source.resize(TerminalGridSize(60, 20))
            advanceTimeBy(121)
            session.source.resize(TerminalGridSize(60, 20))
            advanceTimeBy(121)
            val resizes = channel.requests(Method.TERMINAL_RESIZE).map { it.decoded(TerminalResizeParams.serializer()) }
            assertEquals(listOf(TerminalResizeParams(paneId.uuidString, 60, 20)), resizes)
        }

    @Test
    fun anOwnerMatchingOurClientIdIsUs() =
        test {
            val session = session()
            session.activate(ConnectionState.Connected, 1)
            channel.emitOwnership(paneId, PaneOwner.Remote(clientId, "Me"))
            assertEquals(TerminalOwnership.Owned, session.ownership.value)
        }

    @Test
    fun anOwnerMatchingOurDeviceIdIsUs() =
        test {
            val session = session()
            session.activate(ConnectionState.Connected, 1)
            channel.emitOwnership(paneId, PaneOwner.Remote(deviceId, "Me"))
            assertEquals(TerminalOwnership.Owned, session.ownership.value)
        }

    @Test
    fun anotherOwnerTakesControlElsewhere() =
        test {
            val session = session()
            session.activate(ConnectionState.Connected, 1)
            channel.emitOwnership(paneId, PaneOwner.Remote(UUID.randomUUID(), "iPad"))
            assertEquals(TerminalOwnership.ControlledElsewhere("iPad"), session.ownership.value)
            session.controller.sendText("x")
            assertTrue(channel.notifications(Method.TERMINAL_INPUT).isEmpty())
        }

    @Test
    fun ownershipEventsAreIgnoredForTwoSecondsAfterOurTakeover() =
        test {
            val session = ownedSession()
            channel.emitOwnership(paneId, PaneOwner.Mac("MacBook"))
            assertEquals(TerminalOwnership.Owned, session.ownership.value)
            clock += 3.seconds
            channel.emitOwnership(paneId, PaneOwner.Mac("MacBook"))
            assertEquals(TerminalOwnership.ControlledElsewhere("MacBook"), session.ownership.value)
        }

    @Test
    fun ownershipOfAnotherPaneIsIgnored() =
        test {
            val session = session()
            session.activate(ConnectionState.Connected, 1)
            channel.emitOwnership(UUID.randomUUID(), PaneOwner.Mac("Other"))
            assertEquals(TerminalOwnership.TakingOver, session.ownership.value)
        }

    @Test
    fun outputIsFedAndASnapshotReturnsToLive() =
        test {
            val session = ownedSession()
            channel.emitOutput(paneId, "hello")
            channel.emitOutput(UUID.randomUUID(), "other")
            val firstRow = {
                session.source
                    .frame()
                    ?.lines
                    ?.first()
                    ?.spans
                    ?.first()
                    ?.text
                    .orEmpty()
            }
            assertTrue(firstRow().startsWith("hello"))
            session.controller.setFollowing(false)
            channel.emitOutput(paneId, "fresh", snapshot = true)
            assertTrue(firstRow().startsWith("fresh"))
            assertTrue(session.controller.isFollowing.value)
            assertEquals(TerminalMode.LIVE, session.controller.mode.value)
        }

    @Test
    fun typedTextReachesTheMac() =
        test {
            val session = ownedSession()
            session.controller.sendText("ls\n")
            val input = channel.notifications(Method.TERMINAL_INPUT).single().decoded(TerminalInputParams.serializer())
            assertArrayEquals("ls\r".toByteArray(), input.bytes)
        }
}
