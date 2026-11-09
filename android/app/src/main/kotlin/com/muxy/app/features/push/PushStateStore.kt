package com.muxy.app.features.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException

data class PushState(
    val token: String? = null,
    val disabledServers: Set<String> = emptySet(),
    val statuses: Map<String, PushServerStatus> = emptyMap(),
    val permissionRequested: Boolean = false,
    val promptDismissed: Boolean = false,
) {
    fun isEnabled(serverId: String): Boolean = serverId !in disabledServers
}

interface PushStateStore {
    val state: StateFlow<PushState?>

    suspend fun update(transform: (PushState) -> PushState)
}

class DataStorePushStateStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : PushStateStore {
    private val mutableState = MutableStateFlow<PushState?>(null)

    override val state: StateFlow<PushState?> = mutableState.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .catch { error ->
                    if (error !is IOException) throw error
                    Log.push.error("Reading notification settings failed", error)
                    mutableState.compareAndSet(null, PushState())
                }.collect { mutableState.value = it.toPushState() }
        }
    }

    override suspend fun update(transform: (PushState) -> PushState) {
        try {
            dataStore.edit { preferences -> preferences.write(transform(preferences.toPushState())) }
        } catch (error: IOException) {
            Log.push.error("Saving notification settings failed", error)
        }
    }
}

private object Keys {
    val token = stringPreferencesKey("push.token")
    val disabledServers = stringSetPreferencesKey("push.disabledServers")
    val statuses = stringSetPreferencesKey("push.statuses")
    val permissionRequested = booleanPreferencesKey("push.permissionRequested")
    val promptDismissed = booleanPreferencesKey("push.promptDismissed")
}

private const val STATUS_SEPARATOR = '\t'

private fun Preferences.toPushState(): PushState =
    PushState(
        token = this[Keys.token],
        disabledServers = this[Keys.disabledServers].orEmpty(),
        statuses =
            this[Keys.statuses]
                .orEmpty()
                .mapNotNull { entry ->
                    val serverId = entry.substringBefore(STATUS_SEPARATOR)
                    val status = PushServerStatus.entries.firstOrNull { it.name == entry.substringAfter(STATUS_SEPARATOR) }
                    status?.let { serverId to it }
                }.toMap(),
        permissionRequested = this[Keys.permissionRequested] ?: false,
        promptDismissed = this[Keys.promptDismissed] ?: false,
    )

private fun MutablePreferences.write(state: PushState) {
    if (state.token == null) remove(Keys.token) else this[Keys.token] = state.token
    this[Keys.disabledServers] = state.disabledServers
    this[Keys.statuses] = state.statuses.map { (serverId, status) -> "$serverId$STATUS_SEPARATOR${status.name}" }.toSet()
    this[Keys.permissionRequested] = state.permissionRequested
    this[Keys.promptDismissed] = state.promptDismissed
}
