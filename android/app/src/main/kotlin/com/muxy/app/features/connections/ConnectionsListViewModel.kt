package com.muxy.app.features.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.server.ServerDirectory
import com.muxy.app.models.Connection
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.credentials.CredentialStore
import com.muxy.app.persistence.secrets.TokenStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConnectionsListViewModel(
    private val store: ConnectionStore,
    private val tokens: TokenStore,
    private val credentials: CredentialStore,
    private val directory: ServerDirectory,
) : ViewModel() {
    val connections: StateFlow<List<Connection>?> = store.connections

    fun delete(connection: Connection) {
        connection.serverRouteId?.let(directory::forget)
        viewModelScope.launch {
            store.delete(connection.id)
            attempt { tokens.deleteSecrets(connection.id) }
                .onFailure { Log.persistence.error("Failed to delete secrets", it) }
            forgetServer(connection)
        }
    }

    private suspend fun forgetServer(connection: Connection) {
        if (connection.isRemoteServer) return
        val serverId = connection.serverId ?: return
        attempt { credentials.delete(serverId) }
            .onFailure { Log.persistence.error("Failed to delete server credential", it) }
    }
}
