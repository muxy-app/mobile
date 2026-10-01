package com.muxy.app.persistence.worktrees

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Worktree
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.util.UUID

interface WorktreeCache {
    suspend fun load(
        connectionId: UUID,
        projectId: UUID,
    ): List<Worktree>?

    suspend fun save(
        worktrees: List<Worktree>,
        connectionId: UUID,
        projectId: UUID,
    )
}

class DataStoreWorktreeCache(
    private val dataStore: DataStore<Preferences>,
) : WorktreeCache {
    override suspend fun load(
        connectionId: UUID,
        projectId: UUID,
    ): List<Worktree>? {
        try {
            val value = dataStore.data.first()[key(connectionId, projectId)] ?: return null
            return ProtocolJson.decodeFromString<List<Worktree>>(value)
        } catch (error: IOException) {
            Log.persistence.error("Reading the worktree cache failed", error)
        } catch (error: SerializationException) {
            Log.persistence.error("Invalid worktree cache: ${error.javaClass.simpleName}")
        }
        return null
    }

    override suspend fun save(
        worktrees: List<Worktree>,
        connectionId: UUID,
        projectId: UUID,
    ) {
        try {
            dataStore.edit { it[key(connectionId, projectId)] = ProtocolJson.encodeToString(worktrees) }
        } catch (error: IOException) {
            Log.persistence.error("Saving the worktree cache failed", error)
        }
    }

    private fun key(
        connectionId: UUID,
        projectId: UUID,
    ) = stringPreferencesKey("muxy.worktrees.${connectionId.uuidString}.${projectId.uuidString}")
}
