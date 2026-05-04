package com.muxy.app.ui.connect

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.data.BillingRepository
import com.muxy.app.data.DemoModeStore
import com.muxy.app.data.DeviceCredentials
import com.muxy.app.data.DeviceCredentialsStore
import com.muxy.app.data.Entitlement
import com.muxy.app.data.SavedDevice
import com.muxy.app.data.SavedDevicesStore
import com.muxy.app.data.SessionRepository
import com.muxy.app.data.TerminalPreferencesStore
import com.muxy.app.data.TrialStore
import com.muxy.app.demo.DemoBackend
import com.muxy.app.demo.DemoTransport
import com.muxy.app.model.AuthenticateDeviceParams
import com.muxy.app.model.PairDeviceParams
import com.muxy.app.model.TaggedValue
import com.muxy.app.model.MuxyMessage
import com.muxy.app.model.authenticateDeviceRequest
import com.muxy.app.model.decodePairingResult
import com.muxy.app.model.listProjectsRequest
import com.muxy.app.model.pairDeviceRequest
import com.muxy.app.net.MuxyClient
import com.muxy.app.net.Transport
import com.muxy.app.net.TransportEvent
import com.muxy.app.net.newRequestId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Paywall : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class AwaitingApproval(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String, val clientID: String) : ConnectionState()
    data class Error(val message: String, val technicalDetails: String) : ConnectionState()
}

class ConnectionViewModel(app: Application) : AndroidViewModel(app) {
    private val credentialsStore = DeviceCredentialsStore(app)
    private val devicesStore = SavedDevicesStore(app)
    private val demoModeStore = DemoModeStore(app)
    val terminalPreferences = TerminalPreferencesStore(app)
    private val trialStore = TrialStore(app)
    val billing = BillingRepository(app, viewModelScope, trialStore)

    private var client: Transport = buildTransport(demoModeStore.load())
    var session: SessionRepository = SessionRepository(client, viewModelScope)
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _isDemoMode = MutableStateFlow(demoModeStore.load())
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _savedDevices = MutableStateFlow(initialSavedDevices())
    val savedDevices: StateFlow<List<SavedDevice>> = _savedDevices.asStateFlow()

    private val androidDeviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    private var lastDevice: SavedDevice? = null

    @Volatile private var isBackgrounded: Boolean = false

    private val connectionMutex = Mutex()
    @Volatile private var isReconnecting: Boolean = false
    private var transportJob: Job? = null

    init {
        observeTransport()
    }

    private fun observeTransport() {
        transportJob?.cancel()
        transportJob = viewModelScope.launch {
            client.events.collect { evt ->
                when (evt) {
                    is TransportEvent.Closed, is TransportEvent.Failure -> {
                        if (_state.value is ConnectionState.Connected) {
                            lastDevice?.let { reconnectSilently(it) }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun buildTransport(demo: Boolean): Transport =
        if (demo) DemoTransport() else MuxyClient()

    private fun initialSavedDevices(): List<SavedDevice> {
        val demo = demoModeStore.load()
        return if (demo) listOf(SavedDevice(DemoBackend.SEEDED_DEVICE_NAME, DemoBackend.SEEDED_DEVICE_HOST, DemoBackend.SEEDED_DEVICE_PORT))
        else devicesStore.load()
    }

    fun setDemoMode(enabled: Boolean) {
        if (_isDemoMode.value == enabled) return
        session.stopObserving()
        client.disconnect()
        demoModeStore.save(enabled)
        _isDemoMode.value = enabled
        client = buildTransport(enabled)
        session = SessionRepository(client, viewModelScope)
        observeTransport()
        lastDevice = null
        _savedDevices.value = initialSavedDevices()
        _state.value = ConnectionState.Disconnected
    }

    fun addDevice(name: String, host: String, port: Int) {
        val cleanName = name.ifBlank { "Mac" }
        val updated = (listOf(SavedDevice(cleanName, host, port)) +
                _savedDevices.value.filterNot { it.host == host && it.port == port })
        _savedDevices.value = updated
        if (!_isDemoMode.value) devicesStore.save(updated)
    }

    fun removeDevice(device: SavedDevice) {
        val updated = _savedDevices.value.filterNot { it.id == device.id }
        _savedDevices.value = updated
        if (!_isDemoMode.value) devicesStore.save(updated)
    }

    fun connect(device: SavedDevice) {
        lastDevice = device
        if (billing.entitlement.value is Entitlement.Expired) {
            _state.value = ConnectionState.Paywall
            return
        }
        viewModelScope.launch {
            connectionMutex.withLock { runConnection(device) }
        }
    }

    fun dismissPaywall() {
        _state.value = ConnectionState.Disconnected
    }

    fun reconnect() {
        lastDevice?.let { connect(it) }
    }

    fun disconnect() {

        session.stopObserving()
        client.disconnect()
        _state.value = ConnectionState.Disconnected
    }

    private suspend fun runConnection(device: SavedDevice) {
        _state.value = ConnectionState.Connecting(device.name)
        client.connect(device.host, device.port)
        if (!client.requiresAuth) {
            session.setMyClientID(DemoBackend.MY_CLIENT_ID)
            val theme = DemoBackend.THEME
            session.applyInitialTheme(theme.fg, theme.bg, theme.palette)
            _state.value = ConnectionState.Connected(device.name, DemoBackend.MY_CLIENT_ID)
            session.startObserving()
            session.refreshProjects()
            return
        }
        val credentials = credentialsStore.load()
        delay(500)

        if (!authenticateOrPair(credentials, device.name)) return

        session.startObserving()
        session.refreshProjects()
    }

    private suspend fun authenticateOrPair(credentials: DeviceCredentials, deviceLabel: String): Boolean {
        val authResp = try {
            client.send(
                authenticateDeviceRequest(
                    newRequestId(),
                    AuthenticateDeviceParams(
                        deviceID = credentials.deviceID.toString(),
                        deviceName = androidDeviceName,
                        token = credentials.token,
                    ),
                ),
                timeout = 10.seconds,
            )
        } catch (t: Throwable) {
            fail("Could not reach device", "authenticateDevice failed: ${t.message}")
            return false
        }

        val authError = authResp.error
        if (authError == null) return finishPairing(authResp.result, deviceLabel)
        if (authError.code != 401) {
            fail("Authentication failed", "code ${authError.code}: ${authError.message}")
            return false
        }

        _state.value = ConnectionState.AwaitingApproval(deviceLabel)
        val pairResp = try {
            client.send(
                pairDeviceRequest(
                    newRequestId(),
                    PairDeviceParams(
                        deviceID = credentials.deviceID.toString(),
                        deviceName = androidDeviceName,
                        token = credentials.token,
                    ),
                ),
                timeout = 120.seconds,
            )
        } catch (t: Throwable) {
            fail("Could not finish pairing", "pairDevice failed: ${t.message}")
            return false
        }

        val pairError = pairResp.error
        if (pairError != null) {
            val msg = if (pairError.code == 403) "Approval denied on Mac" else "Could not finish pairing"
            fail(msg, "code ${pairError.code}: ${pairError.message}")
            return false
        }
        return finishPairing(pairResp.result, deviceLabel)
    }

    private fun finishPairing(result: TaggedValue?, deviceLabel: String): Boolean {
        val pairing = decodePairingResult(result)
        if (pairing == null) {
            fail("Could not finish pairing", "expected 'pairing' result, got '${result?.type}'")
            return false
        }
        _state.value = ConnectionState.Connected(deviceLabel, pairing.clientID)
        session.setMyClientID(pairing.clientID)
        billing.startTrialIfAbsent()
        if (pairing.themeFg != null && pairing.themeBg != null) {
            session.applyInitialTheme(pairing.themeFg, pairing.themeBg, pairing.themePalette ?: emptyList())
        }
        return true
    }

    private fun fail(userMessage: String, technical: String) {
        if (isBackgrounded) return
        session.stopObserving()
        client.disconnect()
        _state.value = ConnectionState.Error(userMessage, technical)
    }

    fun onForeground() {
        isBackgrounded = false
        val device = lastDevice ?: return

        if (isReconnecting) return
        when (_state.value) {
            is ConnectionState.Error -> connect(device)
            is ConnectionState.Connected -> {
                if (!client.isConnected) reconnectSilently(device)
                else viewModelScope.launch {
                    connectionMutex.withLock { verifyOrReconnect(device) }
                }
            }
            else -> Unit
        }
    }

    fun onBackground() {
        isBackgrounded = true
    }

    private suspend fun verifyOrReconnect(device: SavedDevice) {

        if (!client.requiresAuth) return
        val ok = runCatching {
            withTimeoutOrNull(3.seconds) {
                client.send(listProjectsLikePing(), 3.seconds)
            } != null
        }.getOrDefault(false)
        if (!ok) reconnectSilentlyLocked(device)
    }

    private fun listProjectsLikePing(): MuxyMessage.Request = listProjectsRequest(newRequestId())

    private fun reconnectSilently(device: SavedDevice) {
        if (isReconnecting) return
        if (billing.entitlement.value is Entitlement.Expired) {
            _state.value = ConnectionState.Paywall
            return
        }
        isReconnecting = true
        viewModelScope.launch {
            try {
                connectionMutex.withLock { reconnectSilentlyLocked(device) }
            } finally {
                isReconnecting = false
            }
        }
    }

    private suspend fun reconnectSilentlyLocked(device: SavedDevice) {

        session.clearPaneOwners()
        val activeProject = session.activeProjectID.value
        if (!client.requiresAuth) {
            client.connect(device.host, device.port)
            session.setMyClientID(DemoBackend.MY_CLIENT_ID)
            session.startObserving()
            session.refreshProjects()
            if (activeProject != null) session.refreshWorkspace(activeProject)
            return
        }
        val credentials = credentialsStore.load()

        var attempt = 0
        while (true) {
            if (isBackgrounded) { delay(2.seconds); continue }
            val ok = tryAuthenticateOnce(device, credentials)
            if (ok) break
            attempt++
            val backoffMs = (1000L shl attempt.coerceAtMost(3)).coerceAtMost(8000L)
            delay(backoffMs)
        }
        session.startObserving()
        session.refreshProjects()

        if (activeProject != null) session.refreshWorkspace(activeProject)
    }

    private suspend fun tryAuthenticateOnce(device: SavedDevice, credentials: DeviceCredentials): Boolean {
        return try {
            client.connect(device.host, device.port)
            delay(500)
            val resp = client.send(
                authenticateDeviceRequest(
                    newRequestId(),
                    AuthenticateDeviceParams(
                        deviceID = credentials.deviceID.toString(),
                        deviceName = androidDeviceName,
                        token = credentials.token,
                    ),
                ),
                timeout = 10.seconds,
            )
            if (resp.error != null) return false
            val pairing = decodePairingResult(resp.result) ?: return false
            session.setMyClientID(pairing.clientID)
            if (pairing.themeFg != null && pairing.themeBg != null) {
                session.applyInitialTheme(pairing.themeFg, pairing.themeBg, pairing.themePalette ?: emptyList())
            }

            (_state.value as? ConnectionState.Connected)?.let {
                _state.value = ConnectionState.Connected(it.deviceName, pairing.clientID)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun onCleared() {

        session.stopObserving()
        client.disconnect()
        super.onCleared()
    }
}
