package com.muxy.app.persistence.workspaces

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.parseUuid
import com.muxy.app.core.serialization.uuidString
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.UUID

interface WorkspaceSelectionStore {
    suspend fun load(connectionId: UUID): UUID?

    suspend fun save(
        workspaceId: UUID?,
        connectionId: UUID,
    )
}

class DataStoreWorkspaceSelectionStore(
    private val dataStore: DataStore<Preferences>,
) : WorkspaceSelectionStore {
    override suspend fun load(connectionId: UUID): UUID? =
        try {
            dataStore.data.first()[key(connectionId)]?.let(::parseUuid)
        } catch (error: IOException) {
            Log.persistence.error("Reading the workspace selection failed", error)
            null
        }

    override suspend fun save(
        workspaceId: UUID?,
        connectionId: UUID,
    ) {
        try {
            dataStore.edit { preferences ->
                if (workspaceId == null) {
                    preferences.remove(key(connectionId))
                    return@edit
                }
                preferences[key(connectionId)] = workspaceId.uuidString
            }
        } catch (error: IOException) {
            Log.persistence.error("Saving the workspace selection failed", error)
        }
    }

    private fun key(connectionId: UUID) = stringPreferencesKey("$KEY_PREFIX.${connectionId.uuidString}")

    private companion object {
        const val KEY_PREFIX = "muxy.selectedWorkspace"
    }
}
