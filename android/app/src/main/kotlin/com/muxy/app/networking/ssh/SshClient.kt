package com.muxy.app.networking.ssh

import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.models.SshConfig
import kotlinx.coroutines.flow.Flow

fun interface SshClientFactory {
    fun create(): SshClient
}

fun interface SshHostKeyVerifier {
    suspend fun verify(blob: ByteArray)
}

interface SshClient {
    suspend fun connect(
        host: String,
        port: Int,
        verifier: SshHostKeyVerifier,
    )

    suspend fun authenticate(
        config: SshConfig,
        credentials: SshCredentials,
    )

    suspend fun openShell(size: TerminalGridSize)

    fun output(): Flow<ByteArray>

    suspend fun awaitExit(): Boolean

    suspend fun send(bytes: ByteArray)

    suspend fun resize(size: TerminalGridSize)

    suspend fun close()
}
