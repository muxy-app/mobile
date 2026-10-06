package com.muxy.app.networking.server.sdk

import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerConnector
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.ConnectionListener
import uniffi.muxy_mobile.ServerCredential
import uniffi.muxy_mobile.Connection as SdkConnection

class SdkServerConnector : ServerConnector {
    override suspend fun connect(
        credential: ServerCredential,
        events: (ConnectionEvent) -> Unit,
    ): ServerConnection {
        val lanes = SdkLanes()
        val listener = ConnectionEventRelay(events)
        var opened: SdkConnection? = null
        val connection =
            try {
                lanes.request { SdkConnection.connect(credential, listener).also { opened = it } }
            } catch (error: Throwable) {
                opened?.disconnect()
                opened?.close()
                lanes.close()
                throw error
            }
        return SdkServerConnection(connection, lanes)
    }
}

class ConnectionEventRelay(
    private val handler: (ConnectionEvent) -> Unit,
) : ConnectionListener {
    override fun onEvent(event: ConnectionEvent) {
        handler(event)
    }
}
