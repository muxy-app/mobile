package com.muxy.app.persistence.connections

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.core.logging.Log
import com.muxy.app.models.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

class DataStoreConnectionStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : ConnectionStore {
    private val preferences =
        dataStore.data.catch { error ->
            if (error !is IOException) throw error
            Log.persistence.error("Reading connections failed", error)
            emit(emptyPreferences())
        }

    override val connections: StateFlow<List<Connection>?> =
        preferences
            .map { it.connections() }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun load(): List<Connection> = preferences.first().connections()

    override suspend fun upsert(connection: Connection) {
        edit { it.withConnection(connection) }
    }

    override suspend fun delete(id: UUID) {
        edit { connections -> connections.filterNot { it.id == id } }
    }

    private suspend fun edit(transform: (List<Connection>) -> List<Connection>) {
        try {
            dataStore.edit { it.writeConnections(transform(it.connections())) }
        } catch (error: IOException) {
            Log.persistence.error("Saving connections failed", error)
        }
    }

    private fun Preferences.connections(): List<Connection> {
        val encoded = this[DEVICES_KEY] ?: return emptyList()
        return try {
            ConnectionJson.decodeFromString(serializer, encoded)
        } catch (error: SerializationException) {
            Log.persistence.error("Failed to decode connections", error)
            emptyList()
        } catch (error: IllegalArgumentException) {
            Log.persistence.error("Failed to decode connections", error)
            emptyList()
        }
    }

    private fun MutablePreferences.writeConnections(connections: List<Connection>) {
        this[DEVICES_KEY] = ConnectionJson.encodeToString(serializer, connections)
    }

    private companion object {
        val DEVICES_KEY = stringPreferencesKey("muxy.devices")
        val serializer = ListSerializer(Connection.serializer())
    }
}

internal val ConnectionJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
