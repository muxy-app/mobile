package com.muxy.app.networking.ssh

import java.io.InputStream

fun interface SshExecClientFactory {
    fun create(): SshExecClient
}

interface SshExecClient : SshClient {
    suspend fun execute(command: String): SshCommand
}

interface SshCommand : AutoCloseable {
    val output: InputStream
    val errors: InputStream

    fun write(bytes: ByteArray)

    fun awaitExit(): Int?
}
