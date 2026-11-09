package com.muxy.app.features.server

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerFailure
import kotlinx.coroutines.CoroutineScope
import uniffi.muxy_mobile.PushRegistration
import uniffi.muxy_mobile.PushStatus

enum class PushIntent {
    UNDECIDED,
    WANTED,
    UNWANTED,
}

interface ServerPushContext {
    val deviceToken: String?

    fun pushIntent(serverId: String): PushIntent

    suspend fun isRegistered(serverId: String): Boolean

    suspend fun record(
        registration: PushRegistration,
        serverId: String,
    )

    suspend fun recordUnregistered(serverId: String)

    suspend fun recordUnsupported(serverId: String)

    fun requestFreshToken()

    suspend fun removeDeliveredNotifications(
        sessionId: ULong,
        serverId: String,
    )

    suspend fun forget(serverId: String)
}

class PushRegistrar(
    private val server: ServerController,
    private val context: ServerPushContext?,
    scope: CoroutineScope,
) {
    private val refresh = CoalescedRefresh(scope)
    private var registeredToken: String? = null

    fun connectionDidOpen() {
        registeredToken = null
        sync()
    }

    fun connectionDidClose() {
        refresh.cancel()
        registeredToken = null
    }

    fun sync() {
        if (server.connection == null || context == null) return
        refresh.request(::performSync)
    }

    suspend fun unregister() {
        val current = server.connection ?: return
        val context = context ?: return
        if (!current.supportsPushNotifications || !context.isRegistered(server.serverId)) return
        unregister(current, context)
    }

    private suspend fun performSync() {
        val current = server.connection ?: return
        val context = context ?: return
        val serverId = server.serverId
        if (!current.supportsPushNotifications) {
            context.recordUnsupported(serverId)
            return
        }
        when (context.pushIntent(serverId)) {
            PushIntent.UNDECIDED -> {
                Unit
            }

            PushIntent.UNWANTED -> {
                if (context.isRegistered(serverId)) unregister(current, context)
            }

            PushIntent.WANTED -> {
                val token = context.deviceToken ?: return
                if (token != registeredToken) register(token, current, context)
            }
        }
    }

    private suspend fun register(
        token: String,
        current: ServerConnection,
        context: ServerPushContext,
    ) {
        attempt { current.registerPush(token) }
            .onSuccess { registration ->
                if (current !== server.connection) return@onSuccess
                registeredToken = token
                context.record(registration, server.serverId)
                Log.push.info("Registered for notifications: ${registration.status}")
                if (registration.status == PushStatus.REFUSED) context.requestFreshToken()
            }.onFailure { error ->
                val failure = ServerFailure.from(error)
                Log.push.error("Registering for notifications failed: $failure")
                if (failure == ServerFailure.Unsupported) context.recordUnsupported(server.serverId)
            }
    }

    private suspend fun unregister(
        current: ServerConnection,
        context: ServerPushContext,
    ) {
        attempt { current.unregisterPush() }
            .onSuccess {
                registeredToken = null
                context.recordUnregistered(server.serverId)
                Log.push.info("Unregistered from notifications")
            }.onFailure { Log.push.error("Unregistering from notifications failed: ${ServerFailure.from(it)}") }
    }
}
