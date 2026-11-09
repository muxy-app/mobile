package com.muxy.app.features.push

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.server.PushIntent
import com.muxy.app.features.server.ServerPushContext
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.persistence.connections.ConnectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.muxy_mobile.PushRegistration
import kotlin.coroutines.CoroutineContext

data class PushServerRow(
    val serverId: String,
    val name: String,
    val isEnabled: Boolean,
    val summary: String,
)

class PushCenter(
    private val store: PushStateStore,
    private val keys: PushKeyStore,
    private val connections: ConnectionStore,
    private val permission: NotificationPermission,
    private val tokens: PushTokenSource,
    private val notifications: PushNotifications,
    private val scope: CoroutineScope,
    private val main: CoroutineContext = Dispatchers.Main.immediate,
) : ServerPushContext {
    private val mutableAuthorization = MutableStateFlow(PushAuthorization.NOT_DETERMINED)
    private val mutableOpenRequest = MutableStateFlow<PushOpenRequest?>(null)
    private val mutablePermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var hasReplacedRefusedToken = false

    @Volatile
    private var hasResolvedAuthorization = false

    @Volatile
    private var currentToken: String? = null

    val authorization: StateFlow<PushAuthorization> = mutableAuthorization.asStateFlow()
    val openRequest: StateFlow<PushOpenRequest?> = mutableOpenRequest.asStateFlow()
    val permissionRequests: SharedFlow<Unit> = mutablePermissionRequests.asSharedFlow()
    val state: StateFlow<PushState?> = store.state

    var contextDidChange: () -> Unit = {}
    var isConnectedInForeground: (String) -> Boolean = { false }

    val servers: Flow<List<PushServerRow>> =
        combine(connections.connections.filterNotNull(), store.state.filterNotNull(), authorization) { saved, state, authorization ->
            saved.pairedServers().map { (serverId, name) ->
                PushServerRow(
                    serverId = serverId,
                    name = name,
                    isEnabled = state.isEnabled(serverId),
                    summary = PushServerSummary.text(authorization, state.isEnabled(serverId), state.statuses[serverId], name),
                )
            }
        }

    val showsPrompt: Flow<Boolean> =
        combine(store.state.filterNotNull(), authorization) { state, authorization ->
            authorization == PushAuthorization.NOT_DETERMINED && !state.promptDismissed
        }

    fun start() {
        scope.launch {
            store.state.filterNotNull().first()
            refreshAuthorization()
        }
    }

    fun refreshAuthorization() {
        scope.launch {
            val state = store.state.filterNotNull().first()
            val current =
                when {
                    permission.isGranted -> PushAuthorization.ALLOWED
                    permission.needsRuntimeRequest && !state.permissionRequested -> PushAuthorization.NOT_DETERMINED
                    else -> PushAuthorization.DENIED
                }
            val changed = !hasResolvedAuthorization || mutableAuthorization.value != current
            mutableAuthorization.value = current
            hasResolvedAuthorization = true
            if (changed) contextDidChange()
            ensureToken()
        }
    }

    fun requestPermission() {
        if (!permission.needsRuntimeRequest) {
            refreshAuthorization()
            return
        }
        mutablePermissionRequests.tryEmit(Unit)
    }

    fun requestPermissionIfUndetermined() {
        if (mutableAuthorization.value != PushAuthorization.NOT_DETERMINED) {
            refreshAuthorization()
            return
        }
        requestPermission()
    }

    fun onPermissionResult() {
        scope.launch {
            store.update { it.copy(permissionRequested = true) }
            refreshAuthorization()
        }
    }

    fun setEnabled(
        serverId: String,
        enabled: Boolean,
    ) {
        scope.launch {
            store.update { state ->
                state.copy(disabledServers = if (enabled) state.disabledServers - serverId else state.disabledServers + serverId)
            }
            if (enabled) requestPermissionIfUndetermined()
            contextDidChange()
            ensureToken()
        }
    }

    fun dismissPrompt() {
        scope.launch { store.update { it.copy(promptDismissed = true) } }
    }

    fun onNewToken(token: String) {
        scope.launch { storeToken(token) }
    }

    fun open(request: PushOpenRequest) {
        mutableOpenRequest.value = request
    }

    fun take(request: PushOpenRequest) {
        mutableOpenRequest.getAndUpdate { if (it?.id == request.id) null else it }
    }

    suspend fun deliver(data: Map<String, String>) {
        val saved = connections.load().pairedServers()
        val keys = activeKeys(saved.keys)
        val notification = PushNotification.from(data, keys, saved) ?: return
        val serverId = notification.target?.serverId
        if (serverId != null && withContext(main) { isConnectedInForeground(serverId) }) return
        notifications.post(notification)
    }

    override val deviceToken: String?
        get() = currentToken ?: store.state.value?.token

    override fun pushIntent(serverId: String): PushIntent {
        if (store.state.value?.isEnabled(serverId) == false) return PushIntent.UNWANTED
        if (!hasResolvedAuthorization) return PushIntent.UNDECIDED
        return when (mutableAuthorization.value) {
            PushAuthorization.ALLOWED -> PushIntent.WANTED
            PushAuthorization.DENIED -> PushIntent.UNWANTED
            PushAuthorization.NOT_DETERMINED -> PushIntent.UNDECIDED
        }
    }

    override suspend fun isRegistered(serverId: String): Boolean {
        val status =
            store.state
                .filterNotNull()
                .first()
                .statuses[serverId] ?: return false
        return status != PushServerStatus.UNSUPPORTED
    }

    suspend fun hasKey(serverId: String): Boolean = keys.key(serverId) != null

    override suspend fun record(
        registration: PushRegistration,
        serverId: String,
    ) {
        updateStatus(serverId, PushServerStatus.from(registration.status))
        val key = registration.key?.let { PushKey.of(serverId, it) } ?: return
        keys.save(key)
    }

    override suspend fun recordUnregistered(serverId: String) {
        keys.delete(serverId)
        updateStatus(serverId, null)
    }

    override suspend fun recordUnsupported(serverId: String) {
        if (store.state.value
                ?.statuses
                ?.get(serverId) == PushServerStatus.UNSUPPORTED
        ) {
            return
        }
        updateStatus(serverId, PushServerStatus.UNSUPPORTED)
    }

    override fun requestFreshToken() {
        if (hasReplacedRefusedToken) return
        hasReplacedRefusedToken = true
        scope.launch {
            attempt { tokens.replace() }
                .onSuccess { token -> token?.let { storeToken(it) } }
                .onFailure { Log.push.error("Replacing the FCM token failed", it) }
        }
    }

    override suspend fun removeDeliveredNotifications(
        sessionId: ULong,
        serverId: String,
    ) {
        val key = keys.key(serverId) ?: return
        notifications.cancel(key.collapseId(sessionId))
    }

    override suspend fun forget(serverId: String) {
        keys.delete(serverId)
        store.update { state ->
            state.copy(disabledServers = state.disabledServers - serverId, statuses = state.statuses - serverId)
        }
    }

    private suspend fun storeToken(token: String) {
        if (deviceToken == token) return
        currentToken = token
        store.update { it.copy(token = token) }
        withContext(main) { contextDidChange() }
    }

    private suspend fun ensureToken() {
        if (mutableAuthorization.value != PushAuthorization.ALLOWED) return
        val state = store.state.filterNotNull().first()
        if (deviceToken != null) return
        val servers = connections.load().pairedServers().keys
        if (servers.none(state::isEnabled)) return
        attempt { tokens.token() }
            .onSuccess { token -> token?.let { storeToken(it) } }
            .onFailure { Log.push.error("Getting the FCM token failed", it) }
    }

    private suspend fun activeKeys(serverIds: Set<String>): List<PushKey> {
        val state = store.state.filterNotNull().first()
        return serverIds.filter(state::isEnabled).mapNotNull { serverId ->
            attempt { keys.key(serverId) }
                .onFailure { Log.push.error("Reading a push key failed", it) }
                .getOrNull()
        }
    }

    private suspend fun updateStatus(
        serverId: String,
        status: PushServerStatus?,
    ) {
        store.update { state ->
            state.copy(statuses = if (status == null) state.statuses - serverId else state.statuses + (serverId to status))
        }
    }
}

internal fun List<Connection>.pairedServers(): Map<String, String> =
    filter { it.kind == ConnectionKind.SERVER && !it.isRemoteServer }
        .mapNotNull { connection -> connection.serverId?.let { it to connection.name } }
        .toMap()
