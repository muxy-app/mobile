package com.muxy.app.testing

import com.muxy.app.features.push.NotificationPermission
import com.muxy.app.features.push.PushCenter
import com.muxy.app.features.push.PushKey
import com.muxy.app.features.push.PushKeyStore
import com.muxy.app.features.push.PushNotification
import com.muxy.app.features.push.PushNotifications
import com.muxy.app.features.push.PushState
import com.muxy.app.features.push.PushStateStore
import com.muxy.app.features.push.PushTokenSource
import com.muxy.app.features.server.ServerPushContext
import com.muxy.app.persistence.connections.ConnectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uniffi.muxy_mobile.PushRegistration
import uniffi.muxy_mobile.PushStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class InMemoryPushStateStore(
    initial: PushState = PushState(),
) : PushStateStore {
    private val mutableState = MutableStateFlow<PushState?>(initial)

    override val state: StateFlow<PushState?> = mutableState.asStateFlow()

    override suspend fun update(transform: (PushState) -> PushState) {
        mutableState.update { transform(it ?: PushState()) }
    }
}

class InMemoryPushKeyStore : PushKeyStore {
    val keys = mutableMapOf<String, PushKey>()

    override suspend fun key(serverId: String): PushKey? = keys[serverId]

    override suspend fun save(key: PushKey) {
        keys[key.serverId] = key
    }

    override suspend fun delete(serverId: String) {
        keys.remove(serverId)
    }
}

class FakeNotificationPermission(
    override var isGranted: Boolean = true,
    override var needsRuntimeRequest: Boolean = false,
) : NotificationPermission

class FakePushTokenSource(
    var current: String? = "fcm-token",
) : PushTokenSource {
    override suspend fun token(): String? = current

    override suspend fun replace(): String? = current
}

class RecordingPushNotifications : PushNotifications {
    val posted = mutableListOf<PushNotification>()
    val cancelled = mutableListOf<String>()

    override fun post(notification: PushNotification) {
        posted += notification
    }

    override fun cancel(tag: String) {
        cancelled += tag
    }
}

fun fakePushCenter(
    scope: CoroutineScope,
    connections: ConnectionStore = InMemoryConnectionStore(),
    store: PushStateStore = InMemoryPushStateStore(),
    keys: PushKeyStore = InMemoryPushKeyStore(),
    permission: NotificationPermission = FakeNotificationPermission(),
    notifications: PushNotifications = RecordingPushNotifications(),
    main: CoroutineContext = EmptyCoroutineContext,
): PushCenter = PushCenter(store, keys, connections, permission, FakePushTokenSource(), notifications, scope, main)

class FakePushContext(
    override var deviceToken: String? = "fcm-token",
    var wants: Boolean = true,
) : ServerPushContext {
    val keys = mutableMapOf<String, ByteArray>()
    val statuses = mutableMapOf<String, PushStatus>()
    val unsupported = mutableSetOf<String>()
    val removedSessions = mutableListOf<ULong>()
    var freshTokenRequests = 0

    override fun wantsNotifications(serverId: String): Boolean = wants

    override suspend fun hasKey(serverId: String): Boolean = serverId in keys

    override suspend fun record(
        registration: PushRegistration,
        serverId: String,
    ) {
        statuses[serverId] = registration.status
        val key = registration.key
        if (registration.status == PushStatus.DISABLED || key == null) keys.remove(serverId) else keys[serverId] = key
    }

    override suspend fun recordUnregistered(serverId: String) {
        keys.remove(serverId)
    }

    override suspend fun recordUnsupported(serverId: String) {
        unsupported += serverId
    }

    override fun requestFreshToken() {
        freshTokenRequests += 1
    }

    override suspend fun removeDeliveredNotifications(
        sessionId: ULong,
        serverId: String,
    ) {
        removedSessions += sessionId
    }

    override suspend fun forget(serverId: String) {
        keys.remove(serverId)
    }
}
