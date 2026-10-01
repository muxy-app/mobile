package com.muxy.app.features.projectdetail.terminal

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.features.terminal.TerminalClipboard
import com.muxy.app.features.terminal.TerminalController
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.features.terminal.emulator.EmulatorTerminalSource
import com.muxy.app.networking.muxy1.ConnectionState
import com.muxy.app.networking.muxy1.protocol.ClientTerminalTheme
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.PaneOwner
import com.muxy.app.networking.muxy1.protocol.PaneOwnershipEvent
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.ReleasePaneParams
import com.muxy.app.networking.muxy1.protocol.SetClientThemeParams
import com.muxy.app.networking.muxy1.protocol.TakeOverPaneParams
import com.muxy.app.networking.muxy1.protocol.TerminalBytesEvent
import com.muxy.app.networking.muxy1.protocol.TerminalInputParams
import com.muxy.app.networking.muxy1.protocol.TerminalResizeParams
import com.muxy.app.networking.muxy1.protocol.TerminalScrollParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

sealed interface TerminalOwnership {
    data object Idle : TerminalOwnership

    data object TakingOver : TerminalOwnership

    data object Owned : TerminalOwnership

    data class ControlledElsewhere(
        val deviceName: String,
    ) : TerminalOwnership

    data class TakeoverFailed(
        val message: String,
    ) : TerminalOwnership

    data object Disconnected : TerminalOwnership
}

class TerminalSession(
    val paneId: UUID,
    private val channel: TerminalChannel,
    private val scope: CoroutineScope,
    private val outbound: CoroutineScope,
    clipboard: TerminalClipboard,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val mutableOwnership = MutableStateFlow<TerminalOwnership>(TerminalOwnership.Idle)

    val ownership: StateFlow<TerminalOwnership> = mutableOwnership.asStateFlow()

    val source =
        EmulatorTerminalSource(
            sink = ::transmit,
            clipboard = { text -> if (mutableOwnership.value == TerminalOwnership.Owned) clipboard.copy(text) },
            scrollForwarder = ::forwardTerminalScroll,
            onResize = ::gridDidChange,
        )

    val controller = TerminalController(source, scope)

    private val pane = paneId.uuidString
    private var isActive = false
    private var connection: Long? = null
    private var takenOverOn: Long? = null
    private var takeoverGeneration = 0
    private var lastTakeOver: TimeMark? = null
    private var lastReportedSize: TerminalGridSize? = null
    private var eventsJob: Job? = null
    private var resizeJob: Job? = null
    private var clientTheme: ClientTerminalTheme = ThemeCatalog.muxy.clientTerminalTheme()
    private var lastSentClientTheme: ClientTerminalTheme? = null

    private val canSend: Boolean
        get() = mutableOwnership.value == TerminalOwnership.Owned || mutableOwnership.value == TerminalOwnership.TakingOver

    fun activate(
        state: ConnectionState,
        connection: Long?,
    ) {
        isActive = true
        this.connection = connection
        takenOverOn = null
        lastReportedSize = null
        if (eventsJob == null) {
            eventsJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { channel.events.collect(::handle) }
        }
        if (connection == null && state.isLost) {
            mutableOwnership.value = TerminalOwnership.Disconnected
            return
        }
        mutableOwnership.value = TerminalOwnership.TakingOver
        sendClientThemeIfNeeded(force = true)
        takeOverIfReady()
    }

    fun deactivate() {
        if (!isActive) return
        isActive = false
        eventsJob?.cancel()
        eventsJob = null
        resizeJob?.cancel()
        resizeJob = null
        takenOverOn = null
        lastReportedSize = null
        takeoverGeneration += 1
        mutableOwnership.value = TerminalOwnership.Idle
        notify(Method.RELEASE_PANE, encoded(ReleasePaneParams(pane)))
    }

    fun connectionChanged(
        state: ConnectionState,
        session: Long?,
    ) {
        if (session != null) {
            if (session == connection) return
            connection = session
            if (!isActive) return
            takenOverOn = null
            lastReportedSize = null
            mutableOwnership.value = TerminalOwnership.TakingOver
            sendClientThemeIfNeeded(force = true)
            takeOverIfReady()
            return
        }
        connection = null
        if (!isActive || !state.isLost) return
        takenOverOn = null
        lastReportedSize = null
        takeoverGeneration += 1
        resizeJob?.cancel()
        resizeJob = null
        mutableOwnership.value = TerminalOwnership.Disconnected
    }

    fun takeControl() {
        if (!isActive) return
        takenOverOn = null
        lastReportedSize = null
        mutableOwnership.value = TerminalOwnership.TakingOver
        markTakeOver()
        takeOverIfReady()
    }

    fun useClientTheme(theme: ClientTerminalTheme) {
        if (clientTheme == theme) return
        clientTheme = theme
        sendClientThemeIfNeeded(force = false)
    }

    private fun gridDidChange(grid: TerminalGridSize) {
        takeOverIfReady()
        reportResize(grid)
    }

    private fun takeOverIfReady() {
        if (!isActive || mutableOwnership.value is TerminalOwnership.TakeoverFailed) return
        val current = connection ?: return
        if (takenOverOn == current) return
        val grid = source.grid?.takeIf { it.isUsable() } ?: return
        takeOver(current, grid)
    }

    private fun takeOver(
        current: Long,
        grid: TerminalGridSize,
    ) {
        takenOverOn = current
        lastReportedSize = grid
        markTakeOver()
        mutableOwnership.value = TerminalOwnership.TakingOver
        takeoverGeneration += 1
        val generation = takeoverGeneration
        Log.terminal.debug("Taking over a pane at ${grid.columns}x${grid.rows}")
        val params = encoded(TakeOverPaneParams(pane, grid.columns, grid.rows))
        outbound.launch(start = CoroutineStart.UNDISPATCHED) {
            attempt { channel.request(Method.TAKE_OVER_PANE, params) }
                .onSuccess { confirmTakeOver(generation) }
                .onFailure { failTakeOver(generation, it) }
        }
    }

    private fun confirmTakeOver(generation: Int) {
        if (!isActive || generation != takeoverGeneration) return
        markTakeOver()
        mutableOwnership.value = TerminalOwnership.Owned
    }

    private fun failTakeOver(
        generation: Int,
        error: Throwable,
    ) {
        if (!isActive || generation != takeoverGeneration) return
        Log.terminal.error("Taking over the pane failed", error)
        takenOverOn = null
        mutableOwnership.value = TerminalOwnership.TakeoverFailed(TakeoverFailure.message(error))
    }

    private fun reportResize(grid: TerminalGridSize) {
        val current = connection ?: return
        if (!isActive || takenOverOn != current || !grid.isUsable() || grid == lastReportedSize) return
        resizeJob?.cancel()
        resizeJob =
            scope.launch {
                delay(RESIZE_DEBOUNCE)
                val params = encoded(TerminalResizeParams(pane, grid.columns, grid.rows))
                outbound.launch(start = CoroutineStart.UNDISPATCHED) {
                    attempt { channel.request(Method.TERMINAL_RESIZE, params) }
                        .onSuccess { if (takenOverOn == current) lastReportedSize = grid }
                        .onFailure { Log.terminal.error("Resizing the pane failed", it) }
                }
            }
    }

    private fun transmit(bytes: ByteArray) {
        if (bytes.isEmpty() || !canSend) return
        notify(Method.TERMINAL_INPUT, encoded(TerminalInputParams(pane, bytes)))
    }

    private fun forwardTerminalScroll(deltaY: Double): Boolean {
        if (!canSend) return false
        notify(Method.TERMINAL_SCROLL, encoded(TerminalScrollParams(pane, 0.0, deltaY, true)))
        return true
    }

    private fun notify(
        method: Method,
        params: JsonElement,
    ) {
        outbound.launch(start = CoroutineStart.UNDISPATCHED) {
            attempt { channel.notify(method, params) }
                .onFailure { Log.terminal.error("Sending ${method.wireName} failed", it) }
        }
    }

    private fun sendClientThemeIfNeeded(force: Boolean) {
        if (!isActive || connection == null) return
        if (!force && lastSentClientTheme == clientTheme) return
        lastSentClientTheme = clientTheme
        val params = encoded(SetClientThemeParams(clientTheme))
        outbound.launch(start = CoroutineStart.UNDISPATCHED) {
            attempt { channel.request(Method.SET_CLIENT_THEME, params) }
                .onFailure { error ->
                    if ((error as? ProtocolException)?.code == ErrorCode.NOT_FOUND) {
                        Log.terminal.debug("setClientTheme is unsupported")
                        return@onFailure
                    }
                    Log.terminal.error("setClientTheme failed", error)
                }
        }
    }

    private fun handle(event: EventEnvelope) {
        when (event.event) {
            EventName.TERMINAL_OUTPUT -> bytes(event, EventType.TERMINAL_OUTPUT)?.let(source::feed)
            EventName.TERMINAL_SNAPSHOT -> bytes(event, EventType.TERMINAL_SNAPSHOT)?.let(::applySnapshot)
            EventName.PANE_OWNERSHIP_CHANGED -> ownershipChanged(event)
        }
    }

    private fun applySnapshot(bytes: ByteArray) {
        source.restart(bytes)
        controller.returnToLive()
    }

    private fun bytes(
        event: EventEnvelope,
        type: String,
    ): ByteArray? {
        val data = event.data?.takeIf { it.type == type } ?: return null
        if (TerminalBytesEvent.paneId(data) != paneId) return null
        return runCatching { data.decode(TerminalBytesEvent.serializer()).bytes }
            .onFailure { Log.terminal.error("Decoding terminal bytes failed: ${it.javaClass.simpleName}") }
            .getOrNull()
    }

    private fun ownershipChanged(event: EventEnvelope) {
        val data = event.data?.takeIf { it.type == EventType.PANE_OWNERSHIP } ?: return
        val payload = runCatching { data.decode(PaneOwnershipEvent.serializer()) }.getOrNull() ?: return
        if (payload.paneId != paneId) return
        if (isOurs(payload.owner)) {
            mutableOwnership.value = TerminalOwnership.Owned
            return
        }
        if (isWithinTakeOverGrace()) return
        val current = mutableOwnership.value
        if (current != TerminalOwnership.Owned && current != TerminalOwnership.TakingOver) return
        mutableOwnership.value = TerminalOwnership.ControlledElsewhere(payload.owner.deviceName)
    }

    private fun isOurs(owner: PaneOwner): Boolean {
        if (owner !is PaneOwner.Remote) return false
        return channel.identity()?.matches(owner.deviceId) == true
    }

    private fun markTakeOver() {
        lastTakeOver = timeSource.markNow()
    }

    private fun isWithinTakeOverGrace(): Boolean = lastTakeOver?.let { it.elapsedNow() < TAKE_OVER_GRACE } == true

    private inline fun <reified P> encoded(params: P): JsonElement = ProtocolJson.encodeToJsonElement(params)

    private val ConnectionState.isLost: Boolean
        get() = this is ConnectionState.Disconnected || this is ConnectionState.Failed

    private companion object {
        val RESIZE_DEBOUNCE = 120.milliseconds
        val TAKE_OVER_GRACE = 2.seconds
    }
}
