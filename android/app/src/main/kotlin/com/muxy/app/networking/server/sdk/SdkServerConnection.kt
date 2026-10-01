package com.muxy.app.networking.server.sdk

import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerProject
import com.muxy.app.networking.server.ServerTerminalChannel
import uniffi.muxy_mobile.Session
import uniffi.muxy_mobile.Connection as SdkConnection

class SdkServerConnection(
    private val connection: SdkConnection,
    private val lanes: SdkLanes,
) : ServerConnection {
    override val serverVersion: String
        get() = connection.serverVersion()

    override suspend fun projects(): List<ServerProject> = lanes.request { connection.projects() }

    override suspend fun sessions(projectId: String): List<Session> = lanes.request { connection.sessions(projectId) }

    override suspend fun createSession(
        projectId: String,
        columns: Int,
        rows: Int,
    ): Session = lanes.request { connection.createSession(projectId, columns.toCells(), rows.toCells()) }

    override suspend fun endSession(sessionId: ULong) {
        lanes.request { connection.endSession(sessionId) }
    }

    override suspend fun attach(
        sessionId: ULong,
        columns: Int,
        rows: Int,
    ): ServerTerminalChannel {
        val terminal = lanes.request { connection.attach(sessionId, columns.toCells(), rows.toCells()) }
        return SdkTerminalChannel(terminal, lanes)
    }

    override fun files(projectId: String) = SdkProjectFiles(connection.files(projectId), lanes)

    override fun git(projectId: String) = SdkGitRepository(connection.git(projectId), lanes)

    override fun disconnect() {
        connection.disconnect()
        lanes.close()
        connection.close()
    }
}

internal fun Int.toCells(): UShort = coerceIn(0, UShort.MAX_VALUE.toInt()).toUShort()
