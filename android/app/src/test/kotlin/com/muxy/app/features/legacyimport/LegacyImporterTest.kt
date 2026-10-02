@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.legacyimport

import com.muxy.app.features.billing.SecretTrialStore
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.PairingState
import com.muxy.app.persistence.secrets.DeviceCredential
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.InMemorySettingsStore
import com.muxy.app.persistence.settings.SettingsStore
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.InMemorySecretStore
import com.muxy.app.testing.InMemoryWorkspaceSelectionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.UUID

class LegacyImporterTest {
    private val connectionId = UUID.fromString("b1234567-89ab-4cde-8fab-0123456789ab")
    private val repairId = UUID.fromString("c1234567-89ab-4cde-8fab-0123456789ab")
    private val workspaceId = UUID.fromString("e1234567-89ab-4cde-8fab-0123456789ab")
    private val storage = MemoryLegacyStorage()
    private val connections = InMemoryConnectionStore()
    private val secrets = InMemorySecretStore()
    private val tokens = SecretTokenStore(secrets)
    private val settings = InMemorySettingsStore(AppSettings(themeName = "Dracula"))
    private val workspaces = InMemoryWorkspaceSelectionStore()
    private val trials = SecretTrialStore(secrets)

    @Test
    fun fixtureMapsOnlyValidUniqueNonDemoDevices() {
        val state = LegacyRecords.state(fixture("devices"))
        val devices = LegacyRecords.devices(state)
        assertEquals(2, devices.size)
        val connection = requireNotNull(devices.first().connection())
        assertEquals(connectionId, connection.id)
        assertEquals("Work Mac", connection.name)
        assertEquals("192.168.1.10", connection.host)
        assertEquals(4865, connection.port)
        assertEquals("Work Mac._muxy._tcp.", connection.serviceName)
        assertEquals(ConnectionKind.DEVICE, connection.kind)
        assertEquals(PairingState.PAIRED, connection.pairingState)
        assertEquals("a1234567-89ab-4cde-8fab-0123456789ab", LegacyRecords.installDeviceId(state))
    }

    @Test
    fun missingRecordsAreEmpty() {
        assertNull(LegacyRecords.state(null))
        assertNull(LegacyRecords.installDeviceId(null))
        assertTrue(LegacyRecords.devices(null).isEmpty())
        assertTrue(LegacyRecords.workspaces(null).isEmpty())
    }

    @Test
    fun invalidEnvelopesAreRejected() {
        for (encoded in listOf("broken", "[]", "{}", "{\"version\":1,\"state\":{}}", "{\"version\":0,\"state\":[]}")) {
            assertThrows(Exception::class.java) { LegacyRecords.state(encoded) }
        }
    }

    @Test
    fun malformedInstallIdentityIsNotReplacedWithANewIdentity() {
        val state = LegacyRecords.state("{\"version\":0,\"state\":{\"installDeviceID\":\"1-1-1-1-1\"}}")
        assertNull(LegacyRecords.installDeviceId(state))
    }

    @Test
    fun workspaceFixtureRejectsInvalidIdsAndNullSelections() {
        val records = LegacyRecords.workspaces(LegacyRecords.state(fixture("projects")))
        assertEquals(2, records.size)
        assertEquals(workspaceId, records[connectionId])
        assertFalse(records.containsKey(repairId))
    }

    @Test
    fun absentSettingsPreserveNativeValues() {
        val initial = AppSettings(true, "Dracula", false, true, true)
        val state = requireNotNull(LegacyRecords.state("{\"version\":0,\"state\":{}}"))
        assertEquals(initial, LegacyRecords.settings(state).applyingTo(initial))
    }

    @Test
    fun importsCredentialsTrialSettingsAndKnownWorkspaceBeforeCleanup() =
        runTest {
            storage.beforeCleanup = {
                assertEquals(2, connections.load().size)
                assertEquals(
                    DeviceCredential("a1234567-89ab-4cde-8fab-0123456789ab", "legacy-token"),
                    tokens.credential(connectionId),
                )
                assertNull(tokens.credential(repairId))
                assertEquals("1700000000000", secrets.values[SecretTrialStore.STARTED_AT_KEY])
                assertEquals(AppSettings(true, "Dracula", false, true, true), settings.settings.value)
                assertEquals(mapOf(connectionId to workspaceId), workspaces.selections)
                assertEquals(LegacyImportStage.IMPORTED, storage.stage)
            }
            importer().run()
            assertEquals(LegacyImportStage.COMPLETE, storage.stage)
            assertEquals(1, storage.cleanups)
        }

    @Test
    fun existingNativeConnectionCredentialTrialAndWorkspaceArePreserved() =
        runTest {
            val existing =
                requireNotNull(
                    LegacyRecords.devices(LegacyRecords.state(fixture("devices"))).first().connection(),
                ).copy(name = "Native")
            val credential = DeviceCredential("native-id", "native-token")
            val workspace = UUID.randomUUID()
            connections.upsert(existing)
            tokens.setCredential(credential, connectionId)
            workspaces.save(workspace, connectionId)
            trials.startIfAbsent(1000)
            importer().run()
            assertEquals(existing, connections.load().first { it.id == connectionId })
            assertEquals(credential, tokens.credential(connectionId))
            assertEquals(workspace, workspaces.load(connectionId))
            assertEquals(1000L, trials.startIfAbsent(2000))
        }

    @Test
    fun missingTokenKeepsConnectionsForRePairing() =
        runTest {
            storage.secrets.remove("muxy.installToken")
            importer().run()
            assertEquals(2, connections.load().size)
            assertNull(tokens.credential(connectionId))
            assertEquals(LegacyImportStage.COMPLETE, storage.stage)
        }

    @Test
    fun unreadableSecretsDoNotBlockSettingsOrConnections() =
        runTest {
            storage.secretFailure = IOException("unavailable")
            importer().run()
            assertEquals(2, connections.load().size)
            assertNull(tokens.credential(connectionId))
            assertTrue(requireNotNull(settings.settings.value).hasCompletedOnboarding)
            assertEquals(LegacyImportStage.COMPLETE, storage.stage)
        }

    @Test
    fun malformedDevicesDoNotBlockOtherRecords() =
        runTest {
            storage.values["muxy.devices.v1"] = "broken"
            importer().run()
            assertTrue(connections.load().isEmpty())
            assertTrue(requireNotNull(settings.settings.value).hasCompletedOnboarding)
            assertEquals(1700000000000L, trials.startIfAbsent(2000))
            assertEquals(LegacyImportStage.COMPLETE, storage.stage)
        }

    @Test
    fun invalidTrialTimestampsDoNotStartATrial() =
        runTest {
            for (value in listOf("", "0", "-1", "1e20", "99999999999999999999")) {
                storage.stage = LegacyImportStage.PENDING
                storage.secrets["muxy.trial.startedAt"] = value
                importer().run()
                assertNull(secrets.values[SecretTrialStore.STARTED_AT_KEY])
            }
        }

    @Test
    fun completedImportDoesNotReadOrCleanAgainEvenWithANewImporter() =
        runTest {
            importer().run()
            val reads = storage.reads
            settings.update { AppSettings() }
            connections.delete(connectionId)
            importer().run()
            assertEquals(reads, storage.reads)
            assertEquals(1, storage.cleanups)
            assertEquals(AppSettings(), settings.settings.value)
            assertFalse(connections.load().any { it.id == connectionId })
        }

    @Test
    fun failedCleanupRetriesWithoutReimporting() =
        runTest {
            storage.cleanupResult = false
            importer().run()
            assertEquals(LegacyImportStage.IMPORTED, storage.stage)
            val reads = storage.reads
            connections.delete(connectionId)
            storage.cleanupResult = true
            importer().run()
            assertEquals(reads, storage.reads)
            assertEquals(2, storage.cleanups)
            assertEquals(LegacyImportStage.COMPLETE, storage.stage)
            assertFalse(connections.load().any { it.id == connectionId })
        }

    @Test
    fun failedMarkerWriteDoesNotDeleteTheSource() =
        runTest {
            storage.markerFailure = true
            importer().run()
            assertEquals(0, storage.cleanups)
            assertEquals(LegacyImportStage.PENDING, storage.stage)
            storage.markerFailure = false
            importer().run()
            assertEquals(2, connections.load().size)
            assertEquals(LegacyImportStage.COMPLETE, storage.stage)
        }

    @Test
    fun cancellationDoesNotMarkOrCleanTheImport() =
        runTest {
            storage.secretFailure = CancellationException("cancelled")
            val result = runCatching { importer().run() }
            assertTrue(result.exceptionOrNull() is CancellationException)
            assertEquals(0, storage.cleanups)
            assertEquals(LegacyImportStage.PENDING, storage.stage)
        }

    @Test
    fun importWaitsForSettingsToLoad() =
        runTest {
            val delayed = InMemorySettingsStore(null)
            val result = async { importer(delayed).run() }
            runCurrent()
            assertFalse(result.isCompleted)
            assertEquals(0, storage.cleanups)
            delayed.load(AppSettings())
            result.await()
            assertTrue(requireNotNull(delayed.settings.value).hasCompletedOnboarding)
            assertEquals(LegacyImportStage.COMPLETE, storage.stage)
        }

    @Test
    fun unavailableSettingsDoNotHangStartupForever() =
        runTest {
            val unavailable =
                object : SettingsStore {
                    override val settings = MutableStateFlow<AppSettings?>(null)

                    override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
                }
            importer(unavailable).run()
            assertEquals(5_000, testScheduler.currentTime)
            assertEquals(LegacyImportStage.COMPLETE, storage.stage)
        }

    @Test
    fun concurrentRunsImportOnce() =
        runTest {
            val importer = importer()
            val first = async { importer.run() }
            val second = async { importer.run() }
            first.await()
            second.await()
            assertEquals(1, storage.cleanups)
            assertEquals(2, connections.load().size)
        }

    private fun importer(settingsStore: SettingsStore = settings) =
        LegacyImporter(storage, connections, tokens, settingsStore, workspaces, trials)

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/legacy/$name.json")).readText()

    private inner class MemoryLegacyStorage : LegacyStorage {
        private var currentStage = LegacyImportStage.PENDING
        var markerFailure = false
        var secretFailure: Exception? = null
        var cleanupResult = true
        var reads = 0
        var cleanups = 0
        var beforeCleanup: suspend () -> Unit = {}
        val values =
            mutableMapOf(
                "muxy.devices.v1" to fixture("devices"),
                "muxy.settings.v1" to fixture("settings"),
                "muxy.projects.v1" to fixture("projects"),
            )
        val secrets = mutableMapOf("muxy.installToken" to "legacy-token", "muxy.trial.startedAt" to "1700000000000")

        override var stage: LegacyImportStage
            get() = currentStage
            set(value) {
                if (markerFailure) throw IOException("unavailable")
                currentStage = value
            }

        override fun value(key: String): String? {
            reads += 1
            return values[key]
        }

        override fun secret(key: String): String? {
            secretFailure?.let { throw it }
            return secrets[key]
        }

        override fun cleanup(): Boolean {
            kotlinx.coroutines.runBlocking { beforeCleanup() }
            cleanups += 1
            return cleanupResult
        }
    }
}
