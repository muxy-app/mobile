package com.muxy.app.features.server

import com.muxy.app.features.server.terminal.SdkTerminalSource
import com.muxy.app.features.terminal.TerminalController
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.networking.server.ServerTerminalChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed interface ServerTerminalPhase {
    data object Waiting : ServerTerminalPhase

    data object Creating : ServerTerminalPhase

    data object Attaching : ServerTerminalPhase

    data object Live : ServerTerminalPhase

    data class Failed(
        val message: String,
    ) : ServerTerminalPhase

    data object Ended : ServerTerminalPhase
}

class ServerTerminal(
    val projectId: String,
    sessionId: ULong?,
    private val server: ServerController,
    scope: CoroutineScope,
) {
    val id: UUID = UUID.randomUUID()

    var sessionId: ULong? = sessionId
        private set

    var isRetired = false
        private set

    private val mutablePhase = MutableStateFlow<ServerTerminalPhase>(ServerTerminalPhase.Waiting)
    private val mutableTitle = MutableStateFlow(defaultTitle(sessionId))
    private val mutableHasScreen = MutableStateFlow(false)

    val phase: StateFlow<ServerTerminalPhase> = mutablePhase.asStateFlow()
    val title: StateFlow<String> = mutableTitle.asStateFlow()
    val hasScreen: StateFlow<Boolean> = mutableHasScreen.asStateFlow()

    val source = SdkTerminalSource(scope) { server.viewportDidChange(this) }
    val controller = TerminalController(source, scope)

    val viewportSize: TerminalGridSize?
        get() = source.viewportSize

    val canAttach: Boolean
        get() = mutablePhase.value == ServerTerminalPhase.Waiting

    fun screenDidChange() {
        source.screenDidChange()
    }

    fun metadataDidChange() {
        source.metadataDidChange()
        updateTitle()
    }

    fun assign(sessionId: ULong) {
        this.sessionId = sessionId
        mutableTitle.value = defaultTitle(sessionId)
    }

    fun markCreating() {
        mutablePhase.value = ServerTerminalPhase.Creating
    }

    fun markAttaching() {
        mutablePhase.value = ServerTerminalPhase.Attaching
    }

    fun didAttach(channel: ServerTerminalChannel) {
        mutablePhase.value = ServerTerminalPhase.Live
        source.attach(channel)
        mutableHasScreen.value = true
        updateTitle()
    }

    fun releaseChannel(): ServerTerminalChannel? {
        val released = dropChannel()
        val phase = mutablePhase.value
        if (phase == ServerTerminalPhase.Live || phase == ServerTerminalPhase.Attaching) {
            mutablePhase.value = ServerTerminalPhase.Waiting
        }
        return released
    }

    fun connectionDidClose() {
        dropChannel()?.close()
        val phase = mutablePhase.value
        if (phase == ServerTerminalPhase.Ended || phase is ServerTerminalPhase.Failed) return
        mutablePhase.value = ServerTerminalPhase.Waiting
    }

    fun attachWasInterrupted() {
        val phase = mutablePhase.value
        if (phase != ServerTerminalPhase.Creating && phase != ServerTerminalPhase.Attaching) return
        mutablePhase.value = ServerTerminalPhase.Waiting
    }

    fun markRetired() {
        isRetired = true
    }

    fun attachDidFail(failure: ServerFailure) {
        dropChannel()?.close()
        mutablePhase.value = ServerTerminalPhase.Failed(failure.message(ServerFailure.Context.REQUEST, server.serverName))
    }

    fun markEnded() {
        dropChannel()?.close()
        mutablePhase.value = ServerTerminalPhase.Ended
    }

    fun retry() {
        if (mutablePhase.value !is ServerTerminalPhase.Failed) return
        mutablePhase.value = ServerTerminalPhase.Waiting
        server.updateVisibleTerminal()
    }

    private fun dropChannel(): ServerTerminalChannel? {
        val released = source.release() ?: return null
        controller.returnToLive()
        return released
    }

    private fun updateTitle() {
        val screen = source.screen ?: return
        val programTitle = screen.title.trim()
        if (programTitle.isNotEmpty()) {
            mutableTitle.value = programTitle
            return
        }
        val folder = screen.directory.trimEnd('/').substringAfterLast('/')
        mutableTitle.value = folder.ifEmpty { defaultTitle(sessionId) }
    }

    private companion object {
        fun defaultTitle(sessionId: ULong?): String = sessionId?.let { "Terminal $it" } ?: "New Terminal"
    }
}
