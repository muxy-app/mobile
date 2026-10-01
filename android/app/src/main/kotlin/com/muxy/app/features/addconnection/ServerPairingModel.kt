package com.muxy.app.features.addconnection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.DiscoverySource
import com.muxy.app.models.PairingState
import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.networking.server.ServerPairingService
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.credentials.CredentialStore
import uniffi.muxy_mobile.PairingLink
import uniffi.muxy_mobile.ServerCredential
import java.util.UUID

data class PairingTarget(
    val link: String,
    val address: String,
    val source: DiscoverySource,
)

sealed interface ServerPairingStep {
    data object Entry : ServerPairingStep

    data class Confirm(
        val target: PairingTarget,
    ) : ServerPairingStep

    data class Pairing(
        val target: PairingTarget,
    ) : ServerPairingStep
}

class ServerPairingModel(
    private val pairing: ServerPairingService,
    private val credentials: CredentialStore,
    private val store: ConnectionStore,
    deviceName: String,
) {
    var deviceName by mutableStateOf(deviceName)

    var step by mutableStateOf<ServerPairingStep>(ServerPairingStep.Entry)
        private set

    var failure by mutableStateOf<String?>(null)
        private set

    val target: PairingTarget?
        get() =
            when (val current = step) {
                ServerPairingStep.Entry -> null
                is ServerPairingStep.Confirm -> current.target
                is ServerPairingStep.Pairing -> current.target
            }

    val canPair: Boolean
        get() = step is ServerPairingStep.Confirm && deviceName.isNotBlank()

    val isPairing: Boolean
        get() = step is ServerPairingStep.Pairing

    fun accepts(link: String): Boolean = parse(link.trim()).isSuccess

    fun receive(
        link: String,
        source: DiscoverySource,
    ) {
        if (isPairing) return
        val code = link.trim()
        parse(code)
            .onSuccess { parsed ->
                step = ServerPairingStep.Confirm(PairingTarget(code, address(parsed), source))
                failure = null
            }.onFailure { error ->
                step = ServerPairingStep.Entry
                failure = ServerFailure.from(error).message(ServerFailure.Context.PAIRING, UNKNOWN_COMPUTER)
            }
    }

    suspend fun pair(): Connection? {
        val current = step as? ServerPairingStep.Confirm ?: return null
        if (!canPair) return null
        val target = current.target
        step = ServerPairingStep.Pairing(target)
        failure = null
        val credential =
            attempt { pairing.pair(target.link, deviceName.trim()) }
                .getOrElse { error ->
                    val reason = ServerFailure.from(error)
                    Log.pairing.error("Pairing failed: $reason")
                    return fail(target, reason.message(ServerFailure.Context.PAIRING, target.address))
                }
        attempt { credentials.save(credential) }
            .onFailure { error ->
                Log.pairing.error("Saving the pairing failed: ${error.javaClass.simpleName}")
                return fail(target, SAVE_FAILED)
            }
        Log.pairing.info("Paired with a Muxy server")
        return saveConnection(credential, target.source)
    }

    private fun parse(link: String): Result<PairingLink> = runCatching { pairing.parse(link) }

    private fun fail(
        target: PairingTarget,
        message: String,
    ): Connection? {
        step = ServerPairingStep.Confirm(target)
        failure = message
        return null
    }

    private suspend fun saveConnection(
        credential: ServerCredential,
        source: DiscoverySource,
    ): Connection {
        val existing = store.load().firstOrNull { it.kind == ConnectionKind.SERVER && it.serverId == credential.serverId }
        val connection =
            Connection(
                id = existing?.id ?: UUID.randomUUID(),
                name = credential.serverName,
                host = credential.hosts.firstOrNull().orEmpty(),
                port = credential.port.toInt(),
                kind = ConnectionKind.SERVER,
                pairingState = PairingState.PAIRED,
                discoverySource = source,
                serverId = credential.serverId,
            )
        store.upsert(connection)
        return connection
    }

    private fun address(link: PairingLink): String = "${link.hosts.firstOrNull() ?: "unknown host"}:${link.port}"

    companion object {
        const val SAVE_FAILED = "Paired, but the pairing couldn't be saved securely. Try again with a new code."
        private const val UNKNOWN_COMPUTER = "the computer"
    }
}
