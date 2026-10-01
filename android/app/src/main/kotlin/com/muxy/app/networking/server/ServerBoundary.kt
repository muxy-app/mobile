package com.muxy.app.networking.server

import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.Key
import uniffi.muxy_mobile.Line
import uniffi.muxy_mobile.Modifiers
import uniffi.muxy_mobile.MouseButton
import uniffi.muxy_mobile.PairingLink
import uniffi.muxy_mobile.Screen
import uniffi.muxy_mobile.ScrollDirection
import uniffi.muxy_mobile.ServerCredential
import uniffi.muxy_mobile.Session

typealias ServerProject = uniffi.muxy_mobile.Project

interface ServerConnector {
    suspend fun connect(
        credential: ServerCredential,
        events: (ConnectionEvent) -> Unit,
    ): ServerConnection
}

interface ServerConnection {
    val serverVersion: String

    suspend fun projects(): List<ServerProject>

    suspend fun sessions(projectId: String): List<Session>

    suspend fun createSession(
        projectId: String,
        columns: Int,
        rows: Int,
    ): Session

    suspend fun endSession(sessionId: ULong)

    suspend fun attach(
        sessionId: ULong,
        columns: Int,
        rows: Int,
    ): ServerTerminalChannel

    fun disconnect()
}

interface ServerTerminalChannel : AutoCloseable {
    val sessionId: ULong

    fun screen(): Screen

    fun send(text: String)

    fun send(
        key: Key,
        modifiers: Modifiers,
    )

    fun paste(text: String)

    fun click(
        button: MouseButton,
        row: Int,
        column: Int,
        modifiers: Modifiers,
    )

    fun scroll(
        direction: ScrollDirection,
        row: Int,
        column: Int,
    )

    suspend fun resize(
        columns: Int,
        rows: Int,
    )

    suspend fun scrollback(maxRows: Int): ScrollbackSnapshot

    suspend fun detach()
}

interface ScrollbackSnapshot : AutoCloseable {
    val historyRows: Long

    fun lines(): List<Line>

    suspend fun loadOlder(maxRows: Int): List<Line>
}

interface ServerPairingService {
    fun parse(link: String): PairingLink

    suspend fun pair(
        link: String,
        deviceName: String,
    ): ServerCredential
}
