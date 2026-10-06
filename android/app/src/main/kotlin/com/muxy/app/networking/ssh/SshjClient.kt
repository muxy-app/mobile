package com.muxy.app.networking.ssh

import com.muxy.app.core.logging.Log
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.models.SshAuthMethod
import com.muxy.app.models.SshConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.InputStream
import java.security.PublicKey

class SshjClient(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SshExecClient {
    private val lifecycle = Mutex()

    @Volatile
    private var client: SSHClient? = null

    @Volatile
    private var closed = false

    private var shell: Session.Shell? = null

    override suspend fun connect(
        host: String,
        port: Int,
        verifier: SshHostKeyVerifier,
    ) {
        lifecycle.withLock {
            runInterruptible(io) {
                check(!closed)
                SshCrypto.initialize()
                val current = SSHClient()
                client = current
                check(!closed)
                current.connectTimeout = CONNECT_TIMEOUT_MS
                current.timeout = REQUEST_TIMEOUT_MS
                current.addHostKeyVerifier(HostVerifier(verifier))
                current.connect(host, port)
                check(!closed)
            }
        }
    }

    override suspend fun authenticate(
        config: SshConfig,
        credentials: SshCredentials,
    ) {
        lifecycle.withLock {
            runInterruptible(io) {
                val current = requireClient()
                when (config.authMethod) {
                    SshAuthMethod.PASSWORD -> {
                        current.authPassword(config.username, credentials.secret)
                    }

                    SshAuthMethod.PRIVATE_KEY -> {
                        current.authPublickey(
                            config.username,
                            SshAuthenticationFactory.privateKey(current, credentials),
                        )
                    }
                }
            }
        }
    }

    override suspend fun openShell(size: TerminalGridSize) {
        lifecycle.withLock {
            runInterruptible(io) {
                val session = requireClient().startSession()
                session.allocatePTY("xterm-256color", size.columns, size.rows, 0, 0, emptyMap())
                shell = session.startShell()
            }
        }
    }

    override suspend fun execute(command: String): SshCommand =
        lifecycle.withLock {
            runInterruptible(io) {
                val session = requireClient().startSession()
                try {
                    ExecCommand(session, session.exec(command))
                } catch (error: Throwable) {
                    session.close()
                    throw error
                }
            }
        }

    override fun output(): Flow<ByteArray> =
        channelFlow {
            val current = checkNotNull(shell)
            launch { pump(current.errorStream) }
            pump(current.inputStream)
        }

    override suspend fun awaitExit(): Boolean =
        runInterruptible(io) {
            val current = checkNotNull(shell)
            current.join()
            val command = current as? Session.Command
            command?.exitStatus != null || command?.exitSignal != null || client?.isConnected == true
        }

    override suspend fun send(bytes: ByteArray) {
        runInterruptible(io) {
            check(!closed)
            checkNotNull(shell).outputStream.apply {
                write(bytes)
                flush()
            }
        }
    }

    override suspend fun resize(size: TerminalGridSize) {
        runInterruptible(io) {
            check(!closed)
            checkNotNull(shell).changeWindowDimensions(size.columns, size.rows, 0, 0)
        }
    }

    override suspend fun close() {
        closed = true
        withContext(io) {
            runCatching { client?.socket?.close() }
            lifecycle.withLock {
                runCatching { client?.close() }
                    .onFailure { Log.ssh.debug("SSH close finished with ${it.javaClass.simpleName}") }
                shell = null
                client = null
            }
        }
    }

    private fun requireClient(): SSHClient {
        check(!closed)
        return checkNotNull(client)
    }

    private suspend fun ProducerScope<ByteArray>.pump(stream: InputStream) {
        val buffer = ByteArray(OUTPUT_CHUNK_SIZE)
        while (true) {
            val count = runInterruptible(io) { stream.read(buffer) }
            if (count < 0) return
            if (count > 0) send(buffer.copyOf(count))
        }
    }

    private class HostVerifier(
        private val verifier: SshHostKeyVerifier,
    ) : HostKeyVerifier {
        override fun verify(
            hostname: String,
            port: Int,
            key: PublicKey,
        ): Boolean {
            val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
            runBlocking { verifier.verify(blob) }
            return true
        }

        override fun findExistingAlgorithms(
            hostname: String,
            port: Int,
        ): List<String> = emptyList()
    }

    private class ExecCommand(
        private val session: Session,
        private val command: Session.Command,
    ) : SshCommand {
        override val output: InputStream
            get() = command.inputStream

        override val errors: InputStream
            get() = command.errorStream

        override fun write(bytes: ByteArray) {
            command.outputStream.write(bytes)
            command.outputStream.flush()
        }

        override fun awaitExit(): Int? {
            command.join()
            return command.exitStatus
        }

        override fun close() {
            try {
                command.close()
            } finally {
                session.close()
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val REQUEST_TIMEOUT_MS = 15_000
        const val OUTPUT_CHUNK_SIZE = 16_384
    }
}
