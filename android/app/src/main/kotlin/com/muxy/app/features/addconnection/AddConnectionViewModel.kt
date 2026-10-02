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
import com.muxy.app.core.validation.ValidatedSshInput
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.DiscoverySource
import com.muxy.app.models.PairingState
import com.muxy.app.models.SshAuthMethod
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.PairingUri
import com.muxy.app.networking.muxy1.PairingUriParse
import com.muxy.app.networking.muxy1.discovery.DiscoveredService
import com.muxy.app.networking.muxy1.discovery.ServiceDiscovery
import com.muxy.app.networking.ssh.SshError
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.secrets.DeviceCredential
import com.muxy.app.persistence.secrets.TokenStore
import com.muxy.app.services.pairing.PairingError
import com.muxy.app.services.pairing.PairingStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
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
    private val inbox: AddConnectionInbox,
    val serverPairing: ServerPairingModel,
    private val sshAdding: SshConnectionAdding,
) : ViewModel() {
    private var hostText by mutableStateOf("")
    private var portValue by mutableStateOf(Endpoint.DEFAULT_PORT.toString())

    var name by mutableStateOf("")
    var username by mutableStateOf("")
    var authMethod by mutableStateOf(SshAuthMethod.PASSWORD)
    var password by mutableStateOf("")
    var privateKey by mutableStateOf("")
    var passphrase by mutableStateOf("")
    private var sshDefaultsApplied = false

    var kind by mutableStateOf(ConnectionKind.DEVICE)
        private set

    var host: String
        get() = hostText
        set(value) {
            if (value == hostText) return
            hostText = value
            forgetDiscoveredMac()
        }

    var portText: String
        get() = portValue
        set(value) {
            if (value == portValue) return
            portValue = value
            forgetDiscoveredMac()
        }

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
        get() {
            if (serverPairing.isPairing) return true
            return when (status) {
                AddConnectionStatus.Connecting, AddConnectionStatus.Authenticating, AddConnectionStatus.AwaitingApproval -> true
                else -> false
            }
        }

    val canSubmit: Boolean
        get() {
            if (isWorking) return false
            return when (kind) {
                ConnectionKind.SERVER -> serverPairing.canPair
                ConnectionKind.DEVICE -> validatedInput() != null
                ConnectionKind.SSH -> validatedSshInput() != null
            }
        }

    val displayedStatus: AddConnectionStatus
        get() {
            if (kind != ConnectionKind.SERVER) return status
            if (serverPairing.isPairing) return AddConnectionStatus.Connecting
            return serverPairing.failure?.let(AddConnectionStatus::Failed) ?: AddConnectionStatus.Idle
        }

    init {
        discovery.start()
        viewModelScope.launch {
            inbox.request.filterNotNull().collect {
                val request = inbox.take() ?: return@collect
                if (isWorking) return@collect
                apply(request)
            }
        }
    }

    fun selectKind(kind: ConnectionKind) {
        if (isWorking || this.kind == kind) return
        this.kind = kind
        status = AddConnectionStatus.Idle
        if (kind == ConnectionKind.SSH && !sshDefaultsApplied) {
            portText = "22"
            sshDefaultsApplied = true
        }
    }

    fun applyPairingCode(code: String): Boolean {
        if (serverPairing.accepts(code)) {
            selectKind(ConnectionKind.SERVER)
            serverPairing.receive(code, DiscoverySource.QR)
            return true
        }
        val parsed = PairingUri.parse(code)
        if (parsed !is PairingUriParse.Parsed) {
            alert = AddConnectionAlert.INVALID_CODE
            return false
        }
        selectKind(ConnectionKind.DEVICE)
        applyScan(parsed.uri)
        return true
    }

    fun pasteServerLink(text: String?) {
        serverPairing.receive(text.orEmpty(), DiscoverySource.MANUAL)
    }

    fun applyRepair(connection: Connection) {
        selectKind(ConnectionKind.DEVICE)
        name = connection.name
        host = connection.host
        portText = connection.port.toString()
        serviceName = connection.serviceName
        discoverySource = connection.discoverySource
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
        if (kind == ConnectionKind.SERVER) {
            viewModelScope.launch { serverPairing.pair()?.let { addedConnection = it } }
            return
        }
        if (kind == ConnectionKind.SSH) {
            val input = validatedSshInput() ?: return
            status = AddConnectionStatus.Connecting
            viewModelScope.launch { addSsh(input) }
            return
        }
        val input = validatedInput() ?: return
        status = AddConnectionStatus.Connecting
        viewModelScope.launch { pair(input) }
    }

    override fun onCleared() {
        discovery.stop()
    }

    private suspend fun apply(request: AddConnectionRequest) {
        when (request) {
            is AddConnectionRequest.PairingCode -> applyPairingCode(request.code)
            is AddConnectionRequest.Repair -> store.load().firstOrNull { it.id == request.connectionId }?.let(::applyRepair)
        }
    }

    private fun forgetDiscoveredMac() {
        serviceName = null
        discoverySource = DiscoverySource.MANUAL
    }

    private suspend fun addSsh(input: ValidatedSshInput) {
        attempt { sshAdding.add(input) }
            .onSuccess {
                password = ""
                privateKey = ""
                passphrase = ""
                status = AddConnectionStatus.Succeeded
                addedConnection = it
            }.onFailure {
                Log.ssh.error("Adding SSH failed: ${it.javaClass.simpleName}")
                val message = if (it is SshCredentialStorageException) it.message.orEmpty() else SshError.classify(it).message
                status = AddConnectionStatus.Failed(message)
            }
    }

    private fun validatedSshInput(): ValidatedSshInput? =
        (
            validator.validateSsh(
                name,
                host,
                portText,
                username,
                authMethod,
                if (authMethod == SshAuthMethod.PASSWORD) password else privateKey,
                passphrase,
            ) as? InputValidation.Valid
        )?.value

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
        val reusable = saved?.takeIf { it.isAt(input.host, input.port) }?.let { tokens.credential(it.id) }
        val credential = reusable ?: newCredential(connection.id, isNewConnection = saved == null) ?: return
        val result =
            attempt { manager.beginPairing(connection, credential) { update -> viewModelScope.launch { applyPairing(update) } } }
                .getOrElse { error ->
                    Log.pairing.error("Pairing failed", error)
                    PairingStatus.Failed(PairingError.ConnectionFailed).also(::applyPairing)
                }
        if (result !is PairingStatus.Paired) return
        if (reusable == null && !save(credential, connection.id)) return
        val paired = connection.copy(pairingState = PairingState.PAIRED)
        store.upsert(paired)
        addedConnection = paired
    }

    private fun newCredential(
        connectionId: UUID,
        isNewConnection: Boolean,
    ): DeviceCredential? {
        val deviceId = if (isNewConnection) connectionId else UUID.randomUUID()
        return runCatching { DeviceCredential(deviceId.uuidString, tokenGenerator.generate()) }
            .onFailure { error ->
                Log.pairing.error("Failed to prepare pairing token", error)
                status = AddConnectionStatus.Failed("Couldn't prepare the pairing token.")
            }.getOrNull()
    }

    private suspend fun save(
        credential: DeviceCredential,
        connectionId: UUID,
    ): Boolean =
        attempt { tokens.setCredential(credential, connectionId) }
            .onFailure { error ->
                Log.pairing.error("Failed to save the pairing token", error)
                status = AddConnectionStatus.Failed("Paired, but the pairing couldn't be saved securely. Try again.")
            }.isSuccess

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
