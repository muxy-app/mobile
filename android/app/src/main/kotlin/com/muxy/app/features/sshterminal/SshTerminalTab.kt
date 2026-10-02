package com.muxy.app.features.sshterminal

import com.muxy.app.features.terminal.TerminalClipboard
import com.muxy.app.features.terminal.TerminalController
import com.muxy.app.features.terminal.emulator.EmulatorTerminalSource
import com.muxy.app.models.Connection
import com.muxy.app.networking.ssh.SshClientFactory
import com.muxy.app.networking.ssh.SshConnectionState
import com.muxy.app.networking.ssh.SshHostKeyTrust
import com.muxy.app.networking.ssh.SshSession
import com.muxy.app.persistence.secrets.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class SshTerminalTab(
    val id: UUID,
    connection: Connection,
    secrets: SecretStore,
    trust: SshHostKeyTrust,
    clients: SshClientFactory,
    parent: CoroutineScope,
    cleanup: CoroutineScope,
    clipboard: TerminalClipboard,
    onExit: (UUID) -> Unit,
) {
    private val scope = CoroutineScope(parent.coroutineContext + Job(parent.coroutineContext[Job]))

    val source: EmulatorTerminalSource =
        EmulatorTerminalSource(
            sink = { session.send(it) },
            clipboard = { if (session.state.value == SshConnectionState.Connected) clipboard.copy(it) },
            onResize = { session.resize(it) },
        )

    val controller = TerminalController(source, scope)

    private val session = SshSession(connection, secrets, trust, clients, scope, cleanup, source::feed)
    val state = session.state

    init {
        scope.launch {
            state.collect { if (it == SshConnectionState.Exited) onExit(id) }
        }
    }

    fun retry() {
        if (state.value !is SshConnectionState.Failed && state.value != SshConnectionState.Disconnected) return
        source.restart(byteArrayOf())
        controller.returnToLive()
        session.retry()
    }

    fun close() {
        session.close()
        scope.cancel()
    }
}
