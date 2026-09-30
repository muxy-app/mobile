package com.muxy.app.features.addconnection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.core.Endpoint
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.core.security.TokenGenerating
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.core.validation.ConnectionInputValidator
import com.muxy.app.core.validation.InputValidation
import com.muxy.app.core.validation.ValidatedConnectionInput
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.DiscoverySource
import com.muxy.app.models.PairingState
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.PairingUri
import com.muxy.app.networking.muxy1.PairingUriParse
import com.muxy.app.networking.muxy1.discovery.DiscoveredService
import com.muxy.app.networking.muxy1.discovery.ServiceDiscovery
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.secrets.DeviceCredential
import com.muxy.app.persistence.secrets.TokenStore
import com.muxy.app.services.pairing.PairingError
import com.muxy.app.services.pairing.PairingStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface AddConnectionStatus {
    data object Idle : AddConnectionStatus

    data object Connecting : AddConnectionStatus

    data object Authenticating : AddConnectionStatus

    data object AwaitingApproval : AddConnectionStatus

    data object Succeeded : AddConnectionStatus

    data class Failed(
        val message: String,
    ) : AddConnectionStatus
}

enum class AddConnectionAlert(
    val title: String,
    val message: String,
) {
    INVALID_CODE("Invalid QR Code", "That code isn't a Muxy pairing code."),
    SCANNER_UNAVAILABLE(
        "Scanner Unavailable",
        "The QR code scanner couldn't be installed on this phone. Enter your Mac's name, host and port instead.",
    ),
}

class AddConnectionViewModel(
    private val store: ConnectionStore,
    private val tokens: TokenStore,
    private val manager: ConnectionManager,
    private val validator: ConnectionInputValidator,
    private val tokenGenerator: TokenGenerating,
    private val discovery: ServiceDiscovery,
    private val inbox: PairingCodeInbox,
) : ViewModel() {
    var name by mutableStateOf("")
    var host by mutableStateOf("")
    var portText by mutableStateOf(Endpoint.DEFAULT_PORT.toString())

    var status by mutableStateOf<AddConnectionStatus>(AddConnectionStatus.Idle)
        private set

    var discoverySource by mutableStateOf(DiscoverySource.MANUAL)
        private set

    var alert by mutableStateOf<AddConnectionAlert?>(null)
        private set

    var addedConnection by mutableStateOf<Connection?>(null)
        private set

    private var serviceName: String? = null

    val discoveredServices: StateFlow<List<DiscoveredService>> = discovery.services

    val isWorking: Boolean
        get() =
            when (status) {
                AddConnectionStatus.Connecting, AddConnectionStatus.Authenticating, AddConnectionStatus.AwaitingApproval -> true
                else -> false
            }

    val canSubmit: Boolean
        get() = !isWorking && validatedInput() != null

    init {
        discovery.start()
        viewModelScope.launch {
            inbox.code.filterNotNull().collect {
                val code = inbox.take() ?: return@collect
                if (isWorking) return@collect
                applyPairingCode(code)
            }
        }
    }

    fun applyPairingCode(code: String): Boolean {
        val parsed = PairingUri.parse(code)
        if (parsed !is PairingUriParse.Parsed) {
            alert = AddConnectionAlert.INVALID_CODE
            return false
        }
        applyScan(parsed.uri)
        return true
    }

    fun applyScan(uri: PairingUri) {
        host = uri.host
        portText = uri.port.toString()
        uri.label?.let { name = it }
        serviceName = uri.serviceName
        discoverySource = DiscoverySource.QR
    }

    fun applyDiscovered(service: DiscoveredService) {
        name = service.name
        host = service.host
        portText = service.port.toString()
        serviceName = service.name
        discoverySource = DiscoverySource.BONJOUR
    }

    fun onScanResult(result: QrScanResult) {
        when (result) {
            is QrScanResult.Scanned -> applyPairingCode(result.text)
            QrScanResult.Cancelled -> Unit
            QrScanResult.Unavailable -> alert = AddConnectionAlert.SCANNER_UNAVAILABLE
        }
    }

    fun dismissAlert() {
        alert = null
    }

    fun submit() {
        if (isWorking) return
        val input = validatedInput() ?: return
        status = AddConnectionStatus.Connecting
        viewModelScope.launch { pair(input) }
    }

    override fun onCleared() {
        discovery.stop()
    }

    private suspend fun pair(input: ValidatedConnectionInput) {
        val saved = store.load().savedMac(serviceName, input.host, input.port)
        val connection =
            Connection(
                id = saved?.id ?: UUID.randomUUID(),
                name = input.name,
                host = input.host,
                port = input.port,
                kind = ConnectionKind.DEVICE,
                pairingState = PairingState.NOT_PAIRED,
                serviceName = serviceName ?: saved?.serviceName,
                discoverySource = discoverySource,
            )
        val existing = saved?.let { tokens.credential(it.id) }
        val credential = existing ?: generateCredential(connection.id, isNewConnection = saved == null) ?: return
        var isPaired = false
        try {
            val result =
                attempt { manager.beginPairing(connection, credential) { update -> viewModelScope.launch { applyPairing(update) } } }
                    .getOrElse { error ->
                        Log.pairing.error("Pairing failed", error)
                        PairingStatus.Failed(PairingError.ConnectionFailed).also(::applyPairing)
                    }
            if (result !is PairingStatus.Paired) return
            val paired = connection.copy(pairingState = PairingState.PAIRED)
            store.upsert(paired)
            isPaired = true
            addedConnection = paired
        } finally {
            if (!isPaired && existing == null) withContext(NonCancellable) { deleteGeneratedCredential(connection.id) }
        }
    }

    private suspend fun generateCredential(
        connectionId: UUID,
        isNewConnection: Boolean,
    ): DeviceCredential? {
        val deviceId = if (isNewConnection) connectionId else UUID.randomUUID()
        return attempt {
            DeviceCredential(deviceId.uuidString, tokenGenerator.generate()).also { tokens.setCredential(it, connectionId) }
        }.onFailure { error ->
            Log.pairing.error("Failed to prepare pairing token", error)
            status = AddConnectionStatus.Failed("Couldn't prepare the pairing token.")
        }.getOrNull()
    }

    private suspend fun deleteGeneratedCredential(connectionId: UUID) {
        attempt { tokens.deleteSecrets(connectionId) }
            .onFailure { Log.pairing.error("Failed to delete the unused pairing token", it) }
    }

    private fun applyPairing(update: PairingStatus) {
        status =
            when (update) {
                PairingStatus.Idle -> AddConnectionStatus.Idle
                PairingStatus.Connecting -> AddConnectionStatus.Connecting
                PairingStatus.Authenticating -> AddConnectionStatus.Authenticating
                PairingStatus.AwaitingApproval -> AddConnectionStatus.AwaitingApproval
                is PairingStatus.Paired -> AddConnectionStatus.Succeeded
                is PairingStatus.Failed -> AddConnectionStatus.Failed(message(update.error))
            }
    }

    private fun validatedInput(): ValidatedConnectionInput? = (validator.validate(name, host, portText) as? InputValidation.Valid)?.value

    private fun message(error: PairingError): String =
        when (error) {
            PairingError.ConnectionFailed -> "Couldn't connect. Check the host and port."
            PairingError.ApprovalDenied -> "The Mac denied this device."
            PairingError.ApprovalTimedOut -> "Approval timed out. Try again."
            PairingError.WrongToken -> "This device's credentials are invalid. Remove it and add it again."
            PairingError.InvalidResponse -> "The Mac sent an unexpected response."
            is PairingError.Server -> "Server error ${error.code}: ${error.message}"
        }
}
