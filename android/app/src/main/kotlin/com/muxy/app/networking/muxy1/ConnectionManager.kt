package com.muxy.app.networking.muxy1

import com.muxy.app.core.device.PhoneName
import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.parseUuid
import com.muxy.app.features.demo.DemoBackend
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.models.Connection
import com.muxy.app.networking.muxy1.protocol.AuthParams
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.PairingResult
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.RawTagged
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.transport.Transport
import com.muxy.app.persistence.secrets.DeviceCredential
import com.muxy.app.persistence.secrets.TokenStore
import com.muxy.app.services.pairing.LivePairingService
import com.muxy.app.services.pairing.PairingError
import com.muxy.app.services.pairing.PairingService
import com.muxy.app.services.pairing.PairingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.IOException
import java.util.UUID

class ConnectionManager(
    private val makeTransport: (String) -> Transport,
    private val tokenStore: TokenStore,
    private val phoneName: PhoneName,
    private val pairingService: PairingService = LivePairingService(),
    private val demoBackend: DemoBackend = DemoBackend(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + dispatcher.limitedParallelism(1))
    private val clientScope = CoroutineScope(job + dispatcher)
    private val operations = Mutex()
    private val intentLock = Any()
    private val runningPairings = MutableStateFlow(0)
    private val mutableStatus = MutableStateFlow(ConnectionStatus.idle)
    private val eventFlow = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = EVENT_BUFFER)

    private var inFlightConnect: InFlightConnect? = null
    private var client: MuxyClient? = null
    private var eventPump: Job? = null
    private var sessionCounter = 0L

    @Volatile
    private var active: Active? = null

    val status: StateFlow<ConnectionStatus> = mutableStatus.asStateFlow()

    val isPairing: Flow<Boolean> = runningPairings.map { it > 0 }.distinctUntilChanged()

    fun events(connectionId: UUID): Flow<EventEnvelope> =
        eventFlow
            .filter { it.connectionId == connectionId && it.session == active?.session }
            .map { it.envelope }

    suspend fun ensureConnected(connection: Connection) {
        connectIntent(connection, force = false).join()
    }

    suspend fun reconnect(connection: Connection) {
        connectIntent(connection, force = true).join()
    }

    suspend fun disconnect() {
        cancelInFlightConnect()
        scope.launch { operations.withLock { disconnectLocked() } }.join()
    }

    suspend fun beginPairing(
        connection: Connection,
        credential: DeviceCredential,
        onStatus: (PairingStatus) -> Unit,
    ): PairingStatus {
        cancelInFlightConnect()
        runningPairings.update { it + 1 }
        return scope
            .async {
                try {
                    operations.withLock { pairLocked(connection, credential, onStatus) }
                } finally {
                    runningPairings.update { it - 1 }
                }
            }.await()
    }

    suspend fun request(
        connectionId: UUID,
        method: Method,
        params: JsonElement? = null,
    ): RawTagged {
        val current = active?.takeIf { it.connectionId == connectionId } ?: throw ConnectionException(ConnectionError.NOT_CONNECTED)
        return when (val link = current.link) {
            is Link.Socket -> link.client.request(method, params)
            Link.Demo -> demoRequest(current, method, params)
        }
    }

    suspend inline fun <reified P> request(
        connectionId: UUID,
        method: Method,
        params: P,
    ): RawTagged = request(connectionId, method, ProtocolJson.encodeToJsonElement(params))

    suspend fun notify(
        connectionId: UUID,
        method: Method,
        params: JsonElement? = null,
    ) {
        val current = active?.takeIf { it.connectionId == connectionId } ?: throw ConnectionException(ConnectionError.NOT_CONNECTED)
        when (val link = current.link) {
            is Link.Socket -> link.client.notify(method, params)
            Link.Demo -> demoRequest(current, method, params)
        }
    }

    fun identity(connectionId: UUID): ConnectionIdentity? = active?.takeIf { it.connectionId == connectionId }?.identity

    private fun connectIntent(
        connection: Connection,
        force: Boolean,
    ): Job =
        synchronized(intentLock) {
            val current = inFlightConnect
            if (current != null && current.connectionId == connection.id && current.job.isActive) return current.job
            current?.job?.cancel()
            val job = scope.launch { operations.withLock { connectLocked(connection, force) } }
            inFlightConnect = InFlightConnect(connection.id, job)
            job.invokeOnCompletion { clearInFlightConnect(job) }
            job
        }

    private fun clearInFlightConnect(job: Job) {
        synchronized(intentLock) {
            if (inFlightConnect?.job === job) inFlightConnect = null
        }
    }

    private fun cancelInFlightConnect() {
        synchronized(intentLock) {
            inFlightConnect?.job?.cancel()
            inFlightConnect = null
        }
    }

    private suspend fun connectLocked(
        connection: Connection,
        force: Boolean,
    ) {
        if (!force && isConnected(connection.id)) return
        try {
            establishLocked(connection)
        } catch (error: CancellationException) {
            withContext(NonCancellable) { teardownLocked() }
            throw error
        }
    }

    private suspend fun establishLocked(connection: Connection) {
        teardownLocked()
        publish(connection.id, ConnectionState.Connecting)
        val credential =
            tokenStore.credential(connection.id) ?: return publish(connection.id, ConnectionState.Failed(ConnectionError.MISSING_TOKEN))
        if (connection.id == DemoConnection.id) {
            return activateLocked(connection.id, Link.Demo, ConnectionIdentity(demoBackend.clientId, parseUuid(credential.deviceId)))
        }
        val url =
            connection.endpoint.webSocketUrl ?: return publish(connection.id, ConnectionState.Failed(ConnectionError.INVALID_ENDPOINT))
        val client = openClientLocked(url) ?: return publish(connection.id, ConnectionState.Failed(ConnectionError.CONNECTION_FAILED))
        publish(connection.id, ConnectionState.Authenticating)
        try {
            val result = client.request(Method.AUTHENTICATE_DEVICE, authParams(credential))
            activateLocked(connection.id, Link.Socket(client), ConnectionIdentity(clientId(result), parseUuid(credential.deviceId)))
        } catch (error: ProtocolException) {
            failAuthenticationLocked(connection.id, error)
        } catch (error: IOException) {
            failAuthenticationLocked(connection.id, error)
        }
    }

    private suspend fun failAuthenticationLocked(
        connectionId: UUID,
        error: Exception,
    ) {
        Log.connection.error("Authentication failed", error)
        teardownLocked()
        publish(connectionId, ConnectionState.Failed(ConnectionError.AUTHENTICATION_FAILED))
    }

    private suspend fun pairLocked(
        connection: Connection,
        credential: DeviceCredential,
        onStatus: (PairingStatus) -> Unit,
    ): PairingStatus {
        teardownLocked()
        publish(connection.id, ConnectionState.Connecting)
        onStatus(PairingStatus.Connecting)
        val url = connection.endpoint.webSocketUrl
        val client = url?.let { openClientLocked(it) }
        if (client == null) {
            val error = if (url == null) ConnectionError.INVALID_ENDPOINT else ConnectionError.CONNECTION_FAILED
            publish(connection.id, ConnectionState.Failed(error))
            val failed = PairingStatus.Failed(PairingError.ConnectionFailed)
            onStatus(failed)
            return failed
        }
        val status = pairingService.pair(client, authParamsValue(credential), onStatus)
        if (status !is PairingStatus.Paired) {
            teardownLocked()
            publish(connection.id, ConnectionState.Failed(ConnectionError.AUTHENTICATION_FAILED))
            return status
        }
        val identity = ConnectionIdentity(parseUuid(status.pairing.clientId), parseUuid(credential.deviceId))
        activateLocked(connection.id, Link.Socket(client), identity)
        return status
    }

    private suspend fun openClientLocked(url: String): MuxyClient? {
        val client = MuxyClient(makeTransport(url), clientScope)
        this.client = client
        startEventPump(client)
        try {
            client.connect()
        } catch (error: IOException) {
            Log.connection.error("Connecting failed", error)
            teardownLocked()
            return null
        }
        client.start()
        return client
    }

    private suspend fun disconnectLocked() {
        if (client == null && active == null && mutableStatus.value.connectionId == null) return
        teardownLocked()
        publish(null, ConnectionState.Disconnected)
    }

    private suspend fun clientDidEndLocked(ended: MuxyClient) {
        if (client !== ended) return
        val lost = active
        teardownLocked()
        if (lost != null) publish(lost.connectionId, ConnectionState.Disconnected)
    }

    private suspend fun teardownLocked() {
        active = null
        eventPump?.cancel()
        eventPump = null
        val current = client ?: return
        client = null
        current.stop()
    }

    private fun activateLocked(
        connectionId: UUID,
        link: Link,
        identity: ConnectionIdentity,
    ) {
        sessionCounter += 1
        active = Active(connectionId, link, sessionCounter, identity)
        mutableStatus.value = ConnectionStatus(connectionId, ConnectionState.Connected, sessionCounter)
    }

    private fun clientId(result: RawTagged): UUID? {
        if (result.type != ResultType.PAIRING) return null
        val pairing = runCatching { result.decode(PairingResult.serializer()) }.getOrNull() ?: return null
        return parseUuid(pairing.clientId)
    }

    private fun publish(
        connectionId: UUID?,
        state: ConnectionState,
    ) {
        mutableStatus.value = ConnectionStatus(connectionId, state, sessionCounter)
    }

    private fun isConnected(connectionId: UUID): Boolean = active?.connectionId == connectionId

    private fun startEventPump(client: MuxyClient) {
        eventPump =
            scope.launch {
                client.events.collect { envelope ->
                    val current = active ?: return@collect
                    val link = current.link
                    if (link !is Link.Socket || link.client !== client) return@collect
                    eventFlow.emit(ConnectionEvent(current.connectionId, current.session, envelope))
                }
                scope.launch { operations.withLock { clientDidEndLocked(client) } }
            }
    }

    private suspend fun demoRequest(
        current: Active,
        method: Method,
        params: JsonElement?,
    ): RawTagged {
        val reply = demoBackend.handle(method, params)
        reply.events.forEach { eventFlow.emit(ConnectionEvent(current.connectionId, current.session, it)) }
        return reply.result
    }

    private fun authParamsValue(credential: DeviceCredential) = AuthParams(credential.deviceId, phoneName.current(), credential.token)

    private fun authParams(credential: DeviceCredential) =
        ProtocolJson.encodeToJsonElement(AuthParams.serializer(), authParamsValue(credential))

    private sealed interface Link {
        class Socket(
            val client: MuxyClient,
        ) : Link

        data object Demo : Link
    }

    private class Active(
        val connectionId: UUID,
        val link: Link,
        val session: Long,
        val identity: ConnectionIdentity,
    )

    private class InFlightConnect(
        val connectionId: UUID,
        val job: Job,
    )

    private class ConnectionEvent(
        val connectionId: UUID,
        val session: Long,
        val envelope: EventEnvelope,
    )

    private companion object {
        const val EVENT_BUFFER = 64
    }
}
