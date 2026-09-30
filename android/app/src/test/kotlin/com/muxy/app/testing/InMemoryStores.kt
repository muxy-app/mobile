package com.muxy.app.testing

import com.muxy.app.models.Connection
import com.muxy.app.networking.muxy1.discovery.DiscoveredService
import com.muxy.app.networking.muxy1.discovery.ServiceDiscovery
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.connections.withConnection
import com.muxy.app.persistence.secrets.SecretStore
import com.muxy.app.persistence.workspaces.WorkspaceSelectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryConnectionStore(
    connections: List<Connection> = emptyList(),
) : ConnectionStore {
    private val state = MutableStateFlow<List<Connection>?>(connections)

    override val connections: StateFlow<List<Connection>?> = state

    override suspend fun load(): List<Connection> = state.value.orEmpty()

    override suspend fun upsert(connection: Connection) {
        state.update { it.orEmpty().withConnection(connection) }
    }

    override suspend fun delete(id: UUID) {
        state.update { connections -> connections.orEmpty().filterNot { it.id == id } }
    }
}

class InMemorySecretStore : SecretStore {
    val values: MutableMap<String, String> = ConcurrentHashMap()

    override suspend fun read(name: String): String? = values[name]

    override suspend fun write(
        name: String,
        value: String,
    ) {
        values[name] = value
    }

    override suspend fun delete(name: String) {
        values.remove(name)
    }
}

class InMemoryWorkspaceSelectionStore : WorkspaceSelectionStore {
    val selections: MutableMap<UUID, UUID> = ConcurrentHashMap()

    override suspend fun load(connectionId: UUID): UUID? = selections[connectionId]

    override suspend fun save(
        workspaceId: UUID?,
        connectionId: UUID,
    ) {
        if (workspaceId == null) {
            selections.remove(connectionId)
            return
        }
        selections[connectionId] = workspaceId
    }
}

class FakeServiceDiscovery(
    services: List<DiscoveredService> = emptyList(),
) : ServiceDiscovery {
    private val state = MutableStateFlow(services)

    var isBrowsing = false
        private set

    override val services: StateFlow<List<DiscoveredService>> = state

    override fun start() {
        isBrowsing = true
    }

    override fun stop() {
        isBrowsing = false
    }
}
