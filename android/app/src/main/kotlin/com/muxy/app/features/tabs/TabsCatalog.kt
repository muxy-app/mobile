package com.muxy.app.features.tabs

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.features.billing.BillingRepository
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.features.server.ServerController
import com.muxy.app.features.server.ServerDirectory
import com.muxy.app.features.server.ServerPhase
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.persistence.connections.ConnectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class TabsCatalog(
    private val connections: ConnectionStore,
    private val billing: BillingRepository,
    private val directory: ServerDirectory,
    private val makeDeviceManager: () -> ConnectionManager,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val entries = mutableMapOf<UUID, Entry>()
    private val mutableDevices = MutableStateFlow<List<TabsDevice>?>(null)
    val devices = mutableDevices.asStateFlow()

    private var saved: List<Connection>? = null
    private var visible = false
    private var foreground = false
    private var activityGeneration = 0

    init {
        scope.launch { connections.connections.collect(::connectionsChanged) }
        scope.launch {
            billing.state
                .map { it.trialLoaded to billing.enforcement.gates(it.entitlement) }
                .distinctUntilChanged()
                .collect { updateActivity() }
        }
    }

    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        activityGeneration += 1
        if (visible) billing.refreshEntitlement()
        updateActivity()
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        activityGeneration += 1
        billing.refreshEntitlement()
        updateActivity()
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        activityGeneration += 1
        updateActivity()
    }

    fun requiresUnlock(connectionId: UUID): Boolean {
        val connection = connections.connections.value?.firstOrNull { it.id == connectionId } ?: return false
        val entitlement = billing.refreshEntitlement()
        return !isDemo(connection) && billing.enforcement.gates(entitlement)
    }

    fun select(
        key: TabsProjectKey,
        tabId: UUID,
    ): TabsSelection? {
        val entry = entries[key.connectionId]?.takeIf(::canUse) ?: return null
        val project = entry.snapshot.projects.firstOrNull { it.id == key.projectId } ?: return null
        if (project.tabs.none { it.id == tabId }) return null
        entry.server?.let { server ->
            val model = server.projectModel(project.id)
            val tab = model.tabs.value.firstOrNull { it.id == tabId && !it.isRetired } ?: return null
            model.select(tab)
        }
        return TabsSelection(entry.connection, project, tabId)
    }

    suspend fun createTab(key: TabsProjectKey): TabsSelection? {
        val entry = entries[key.connectionId]?.takeIf(::canUse) ?: return null
        val project = entry.snapshot.projects.firstOrNull { it.id == key.projectId } ?: return null
        if (!entry.snapshot.canCreate(project)) return null
        val generation = activityGeneration
        val tabId =
            when {
                entry.device != null -> {
                    entry.device.createTab(project.id)
                }

                entry.server != null -> {
                    if (entry.server.phase.value != ServerPhase.Connected || entry.server.project(project.id) == null) return null
                    entry.server
                        .projectModel(project.id)
                        .createTab()
                        .id
                }

                else -> {
                    null
                }
            } ?: return null
        if (generation != activityGeneration || entries[key.connectionId] !== entry || !canUse(entry)) return null
        return TabsSelection(entry.connection, project, tabId)
    }

    fun retry(connectionId: UUID) {
        val entry = entries[connectionId]?.takeIf(::canUse) ?: return
        entry.device?.retry()
        entry.server?.let { server ->
            if (server.phase.value == ServerPhase.Connected) server.reloadProjects() else server.retryNow()
        }
    }

    private fun connectionsChanged(connections: List<Connection>?) {
        saved = connections
        val current = connections.orEmpty().associateBy { it.id }
        entries.values.toList().filter { current[it.connection.id] != it.connection }.forEach { entry ->
            entries.remove(entry.connection.id)
            entry.observation?.cancel()
            entry.device?.close()
            val serverId = entry.connection.serverRouteId
            if (serverId != null && current.values.none { it.serverRouteId == serverId }) directory.forget(serverId)
        }
        connections.orEmpty().filter { it.id !in entries }.forEach(::add)
        updateActivity()
    }

    private fun add(connection: Connection) {
        val device =
            if (connection.kind == ConnectionKind.DEVICE) DeviceTabsCatalog(connection, makeDeviceManager(), scope) else null
        val server =
            if (connection.kind == ConnectionKind.SERVER) connection.serverRouteId?.let(directory::controller) else null
        val entry = Entry(connection, device, server, initialState(connection))
        entries[connection.id] = entry
        val state =
            when {
                device != null -> device.state
                server != null -> serverTabsCatalog(connection, server)
                else -> return
            }
        entry.observation =
            scope.launch {
                state.collect { snapshot ->
                    if (entries[connection.id] !== entry) return@collect
                    entry.snapshot = snapshot
                    publish()
                }
            }
    }

    private fun updateActivity() {
        val browsing =
            entries.values
                .filter(::canUse)
                .mapNotNull { it.server?.serverId }
                .toSet()
        directory.browse(browsing)
        entries.values.forEach { it.device?.setActive(canUse(it)) }
        publish()
    }

    private fun canUse(entry: Entry): Boolean =
        visible && foreground && hasAccess(entry.connection) &&
            connections.connections.value?.firstOrNull { it.id == entry.connection.id } == entry.connection

    private fun hasAccess(connection: Connection): Boolean =
        isDemo(connection) || (billing.state.value.trialLoaded && !billing.enforcement.gates(billing.state.value.entitlement))

    private fun isDemo(connection: Connection): Boolean = connection.id == DemoConnection.id && connection.kind == ConnectionKind.DEVICE

    private fun publish() {
        mutableDevices.value =
            saved?.map { connection ->
                val snapshot = entries[connection.id]?.snapshot ?: initialState(connection)
                if (hasAccess(connection)) return@map snapshot
                snapshot.copy(
                    status = TabsDeviceStatus.LOCKED,
                    isLoading = false,
                    message = "Unlock Muxy to connect to this device.",
                )
            }
    }

    private fun initialState(connection: Connection): TabsDevice =
        when {
            connection.kind == ConnectionKind.SSH -> {
                TabsDevice(connection, status = TabsDeviceStatus.SSH)
            }

            connection.kind == ConnectionKind.SERVER && connection.serverRouteId == null -> {
                TabsDevice(connection, status = TabsDeviceStatus.OFFLINE, message = "Pair this device again.")
            }

            else -> {
                TabsDevice(connection)
            }
        }

    private class Entry(
        val connection: Connection,
        val device: DeviceTabsCatalog?,
        val server: ServerController?,
        var snapshot: TabsDevice,
        var observation: Job? = null,
    )
}
