package com.muxy.app.networking.ssh

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.models.Connection
import com.muxy.app.persistence.secrets.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SshSession(
    private val connection: Connection,
    private val secrets: SecretStore,
    private val trust: SshHostKeyTrust,
    private val clients: SshClientFactory,
    private val scope: CoroutineScope,
    private val cleanup: CoroutineScope,
    private val onOutput: (ByteArray) -> Unit,
) {
    private val mutableState = MutableStateFlow<SshConnectionState>(SshConnectionState.Idle)
    val state = mutableState.asStateFlow()

    private var current: SshClient? = null
    private var input: Channel<ByteArray>? = null
    private var connectionJob: Job? = null
    private var resizeJob: Job? = null
    private var grid: TerminalGridSize? = null
    private var pendingResize: TerminalGridSize? = null
    private var generation = 0L
    private var closed = false

    fun resize(size: TerminalGridSize) {
        if (closed || !size.isUsable() || grid == size) return
        grid = size
        if (state.value == SshConnectionState.Idle) {
            connect(size)
            return
        }
        resizeJob?.cancel()
        val expected = generation
        resizeJob =
            scope.launch {
                delay(RESIZE_DEBOUNCE_MS)
                if (expected != generation) return@launch
                pendingResize = size
                if (state.value != SshConnectionState.Connected) return@launch
                val client = current ?: return@launch
                attempt { client.resize(size) }.onFailure { disconnect(expected, it) }
            }
    }

    fun retry() {
        if (closed) return
        if (state.value !is SshConnectionState.Failed && state.value != SshConnectionState.Disconnected) return
        grid?.let(::connect)
    }

    fun send(bytes: ByteArray) {
        if (closed || bytes.isEmpty() || state.value != SshConnectionState.Connected) return
        if (input?.trySend(bytes)?.isSuccess == true) return
        disconnect(generation, IllegalStateException("SSH input queue is full"))
    }

    fun close() {
        if (closed) return
        closed = true
        stop()
        mutableState.value = SshConnectionState.Disconnected
    }

    private fun connect(size: TerminalGridSize) {
        stop()
        val expected = generation
        pendingResize = null
        mutableState.value = SshConnectionState.Connecting
        connectionJob = scope.launch { runConnection(size, expected) }
    }

    private suspend fun runConnection(
        size: TerminalGridSize,
        expected: Long,
    ) {
        var client: SshClient? = null
        val queue = Channel<ByteArray>(INPUT_QUEUE_CAPACITY)
        var outcome: SshConnectionState = SshConnectionState.Disconnected
        try {
            val credentials = secrets.sshCredentials(connection)
            val config = connection.sshConfig ?: throw SshException(SshError.MISSING_CREDENTIALS)
            client = clients.create()
            current = client
            input = queue
            client.connect(connection.host, connection.port, trust.verifier(connection.id))
            client.authenticate(config, credentials)
            client.openShell(size)
            pendingResize?.takeIf { it != size }?.let { client.resize(it) }
            mutableState.value = SshConnectionState.Connected
            Log.ssh.debug("SSH shell connected")
            val exited = stream(client, queue)
            outcome = if (exited) SshConnectionState.Exited else SshConnectionState.Disconnected
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.ssh.error("SSH session failed: ${error.javaClass.simpleName}")
            outcome =
                if (state.value == SshConnectionState.Connected) {
                    SshConnectionState.Disconnected
                } else {
                    SshConnectionState.Failed(SshError.classify(error))
                }
        } finally {
            queue.close()
            withContext(NonCancellable) { client?.close() }
            if (expected == generation && !closed) {
                resizeJob?.cancel()
                current = null
                input = null
                mutableState.value = outcome
            }
        }
    }

    private suspend fun stream(
        client: SshClient,
        queue: Channel<ByteArray>,
    ): Boolean =
        coroutineScope {
            val sender = launch { for (bytes in queue) client.send(bytes) }
            try {
                client.output().collect(onOutput)
                client.awaitExit()
            } finally {
                sender.cancelAndJoin()
            }
        }

    private fun disconnect(
        expected: Long,
        error: Throwable,
    ) {
        if (expected != generation || closed) return
        Log.ssh.error("SSH stream disconnected: ${error.javaClass.simpleName}")
        stop()
        mutableState.value = SshConnectionState.Disconnected
    }

    private fun stop() {
        generation += 1
        resizeJob?.cancel()
        connectionJob?.cancel()
        input?.close()
        input = null
        val client = current
        current = null
        if (client != null) cleanup.launch { client.close() }
    }

    private companion object {
        const val RESIZE_DEBOUNCE_MS = 120L
        const val INPUT_QUEUE_CAPACITY = 64
    }
}
