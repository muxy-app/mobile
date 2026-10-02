package com.muxy.app.features.legacyimport

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.billing.TrialStore
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.secrets.DeviceCredential
import com.muxy.app.persistence.secrets.TokenStore
import com.muxy.app.persistence.settings.SettingsStore
import com.muxy.app.persistence.workspaces.WorkspaceSelectionStore
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class LegacyImporter(
    private val storage: LegacyStorage,
    private val connections: ConnectionStore,
    private val tokens: TokenStore,
    private val settings: SettingsStore,
    private val workspaces: WorkspaceSelectionStore,
    private val trials: TrialStore,
) {
    private val mutex = Mutex()

    suspend fun run() {
        mutex.withLock {
            importing("startup") {
                if (storage.stage == LegacyImportStage.COMPLETE) return@importing
                if (storage.stage == LegacyImportStage.PENDING) {
                    importRecords()
                    storage.stage = LegacyImportStage.IMPORTED
                    Log.persistence.info("Legacy import finished")
                }
                if (storage.cleanup()) storage.stage = LegacyImportStage.COMPLETE
            }
        }
    }

    private suspend fun importRecords() {
        val devices = importing("devices") { LegacyRecords.state(storage.value("muxy.devices.v1")) }
        val installId = LegacyRecords.installDeviceId(devices)
        val token = importing("token") { storage.secret("muxy.installToken")?.takeIf(String::isNotBlank) }
        val credential = if (installId != null && token != null) DeviceCredential(installId, token) else null
        LegacyRecords.devices(devices).forEach { device ->
            importing("device") {
                val connection = device.connection() ?: return@importing
                if (connections.load().any { it.id == connection.id }) return@importing
                if (credential != null && !device.needsRepair) tokens.setCredential(credential, connection.id)
                connections.upsert(connection)
            }
        }
        importing("trial") {
            val startedAt = storage.secret("muxy.trial.startedAt")?.toLongOrNull()?.takeIf { it > 0 } ?: return@importing
            trials.startIfAbsent(startedAt)
        }
        importing("settings") {
            val state = LegacyRecords.state(storage.value("muxy.settings.v1")) ?: return@importing
            val imported = LegacyRecords.settings(state)
            checkNotNull(
                withTimeoutOrNull(SETTINGS_TIMEOUT_MILLIS) {
                    val expected = imported.applyingTo(settings.settings.filterNotNull().first())
                    settings.update(imported::applyingTo)
                    settings.settings.first { it == expected }
                },
            )
        }
        importing("workspace selections") {
            val state = LegacyRecords.state(storage.value("muxy.projects.v1"))
            val saved = connections.load().map { it.id }.toSet()
            LegacyRecords.workspaces(state).forEach { (connectionId, workspaceId) ->
                importing("workspace selection") {
                    if (connectionId in saved && workspaces.load(connectionId) == null) workspaces.save(workspaceId, connectionId)
                }
            }
        }
    }

    private suspend fun <T> importing(
        record: String,
        block: suspend () -> T,
    ): T? =
        attempt { block() }
            .onFailure { Log.persistence.error("Legacy $record import failed: ${it.javaClass.simpleName}") }
            .getOrNull()

    private companion object {
        const val SETTINGS_TIMEOUT_MILLIS = 5_000L
    }
}
