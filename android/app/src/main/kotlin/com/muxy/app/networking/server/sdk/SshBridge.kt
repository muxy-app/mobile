package com.muxy.app.networking.server.sdk

import com.muxy.app.core.logging.Log
import com.muxy.app.networking.ssh.SshClient
import com.muxy.app.networking.ssh.SshCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import uniffi.muxy_mobile.BridgeChannel
import uniffi.muxy_mobile.ChannelWriter
import uniffi.muxy_mobile.ConnectionListener
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException
import uniffi.muxy_mobile.Connection as SdkConnection

internal class SshBridge(
    private val client: SshClient,
    private val command: SshCommand,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean()
    private val channel = BridgeChannel(Writer(client, command))
    private val output = scope.launch { receive() }

    suspend fun connect(
        host: String,
        listener: ConnectionListener,
    ): SdkConnection =
        suspendCancellableCoroutine { continuation ->
            scope.launch {
                try {
                    val opened = SdkConnection.connectChannel(channel, host, listener)
                    continuation.resume(opened) { _, connection, _ ->
                        connection.disconnect()
                        connection.close()
                    }
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation { close() }
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        channel.finish(null)
        scope.launch {
            withContext(NonCancellable) {
                client.close()
                output.cancelAndJoin()
                channel.close()
                scope.cancel()
            }
        }
    }

    private suspend fun receive() {
        var exitStatus: Int? = null
        try {
            supervisorScope {
                val errors = async { pump(command.errors, channel::receiveError) }
                try {
                    pump(command.output, channel::receive)
                } catch (error: Exception) {
                    withContext(NonCancellable) { client.close() }
                    throw error
                } finally {
                    errors.await()
                }
                exitStatus = runInterruptible { command.awaitExit() }
            }
        } catch (error: Exception) {
            if (!closed.get()) Log.ssh.debug("SSH bridge output ended: ${error.javaClass.simpleName}")
        } finally {
            channel.finish(exitStatus)
        }
    }

    private suspend fun pump(
        stream: InputStream,
        deliver: (ByteArray) -> Unit,
    ) {
        val bytes = ByteArray(64 * 1024)
        while (true) {
            val count = runInterruptible { stream.read(bytes) }
            if (count < 0) return
            if (count > 0) deliver(bytes.copyOf(count))
        }
    }

    private class Writer(
        private val client: SshClient,
        private val command: SshCommand,
    ) : ChannelWriter {
        override fun write(bytes: ByteArray) {
            command.write(bytes)
        }

        override fun close() {
            runBlocking { client.close() }
            command.close()
        }
    }
}
