package com.muxy.app.features.server

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AttachmentCoordinator(
    private val server: ServerController,
    private val scope: CoroutineScope,
) {
    private var desired: ServerTerminal? = null
    private var attached: ServerTerminal? = null
    private var isBusy = false
    private var isInterrupted = false

    fun show(terminal: ServerTerminal?) {
        desired = terminal
        pump()
    }

    fun connectionDidOpen() {
        pump()
    }

    fun connectionDidClose() {
        attached = null
        isInterrupted = false
    }

    fun viewportDidChange(terminal: ServerTerminal) {
        if (terminal !== desired) return
        pump()
    }

    fun retire(terminal: ServerTerminal) {
        if (desired === terminal) desired = null
        pump()
    }

    private fun pump() {
        if (isBusy || isInterrupted) return
        val connection = server.connection ?: return
        val current = attached
        if (current != null && current !== desired) {
            isBusy = true
            scope.launch {
                detach(current)
                finish()
            }
            return
        }
        val target = desired ?: return
        if (target === current || !target.canAttach) return
        val size = target.viewportSize ?: return
        isBusy = true
        scope.launch {
            attach(target, size, connection)
            finish()
        }
    }

    private fun finish() {
        isBusy = false
        pump()
    }

    private suspend fun detach(terminal: ServerTerminal) {
        attached = null
        val isEnded = terminal.phase.value == ServerTerminalPhase.Ended
        val channel = terminal.releaseChannel() ?: return
        if (!isEnded) {
            attempt { channel.detach() }
                .onFailure { Log.terminal.error("Detach failed: ${ServerFailure.from(it)}") }
        }
        channel.close()
    }

    private suspend fun attach(
        terminal: ServerTerminal,
        size: TerminalGridSize,
        connection: ServerConnection,
    ) {
        attempt {
            val sessionId = sessionId(terminal, size, connection)
            if (connection !== server.connection || terminal.isRetired) {
                terminal.attachWasInterrupted()
                return
            }
            terminal.markAttaching()
            val channel = connection.attach(sessionId, size.columns, size.rows)
            if (connection !== server.connection) {
                channel.close()
                terminal.attachWasInterrupted()
                return
            }
            terminal.didAttach(channel)
            attached = terminal
        }.onFailure { error ->
            if (connection !== server.connection) {
                terminal.attachWasInterrupted()
                return
            }
            handleAttachFailure(ServerFailure.from(error), terminal)
        }
    }

    private fun handleAttachFailure(
        failure: ServerFailure,
        terminal: ServerTerminal,
    ) {
        Log.terminal.error("Attach failed: $failure")
        if (failure == ServerFailure.Disconnected) {
            isInterrupted = true
            terminal.attachWasInterrupted()
            return
        }
        terminal.attachDidFail(failure)
        server.attachDidFail(terminal)
    }

    private suspend fun sessionId(
        terminal: ServerTerminal,
        size: TerminalGridSize,
        connection: ServerConnection,
    ): ULong {
        terminal.sessionId?.let { return it }
        terminal.markCreating()
        val session = connection.createSession(terminal.projectId, size.columns, size.rows)
        if (terminal.isRetired) {
            endAbandoned(session.id, connection)
            return session.id
        }
        server.register(terminal, session.id)
        return session.id
    }

    private suspend fun endAbandoned(
        sessionId: ULong,
        connection: ServerConnection,
    ) {
        attempt { connection.endSession(sessionId) }
            .onFailure { Log.terminal.error("Ending an abandoned session failed: ${ServerFailure.from(it)}") }
    }
}
