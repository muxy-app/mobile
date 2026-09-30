package com.muxy.app.persistence.connections

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.models.DiscoverySource
import com.muxy.app.persistence.preferencesDataStore
import com.muxy.app.testing.device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreConnectionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun isEmptyInitially() =
        runTest {
            assertTrue(store().load().isEmpty())
        }

    @Test
    fun upsertAddsAConnection() =
        runTest {
            val store = store()
            val studio = device(serviceName = "Studio").copy(discoverySource = DiscoverySource.BONJOUR)
            store.upsert(studio)
            assertEquals(listOf(studio), store.load())
        }

    @Test
    fun upsertReplacesById() =
        runTest {
            val store = store()
            val studio = device(name = "Old")
            store.upsert(studio)
            store.upsert(studio.copy(name = "New"))
            assertEquals(listOf("New"), store.load().map { it.name })
        }

    @Test
    fun deleteRemovesById() =
        runTest {
            val store = store()
            val first = device(name = "First")
            val second = device(name = "Second")
            store.upsert(first)
            store.upsert(second)
            store.delete(first.id)
            assertEquals(listOf(second), store.load())
        }

    @Test
    fun theFlowFollowsChanges() =
        runTest {
            val store = store()
            val studio = device()
            store.upsert(studio)
            assertEquals(listOf(studio), store.connections.first { it == listOf(studio) })
        }

    @Test
    fun keepsTheListUnderTheIosKey() =
        runTest {
            val scope = scopeFor(this)
            val file = file()
            val dataStore = preferencesDataStore("Connections", scope) { file }
            val studio = device()
            DataStoreConnectionStore(dataStore, scope).upsert(studio)
            val stored = dataStore.data.first()[stringPreferencesKey("muxy.devices")]
            assertTrue(stored!!.contains(studio.name))
        }

    @Test
    fun anUndecodableListReadsAsEmpty() =
        runTest {
            val scope = scopeFor(this)
            val file = file()
            val dataStore = preferencesDataStore("Connections", scope) { file }
            dataStore.edit { it[stringPreferencesKey("muxy.devices")] = "not json" }
            assertTrue(DataStoreConnectionStore(dataStore, scope).load().isEmpty())
        }

    private fun TestScope.store(): DataStoreConnectionStore {
        val scope = scopeFor(this)
        val file = file()
        return DataStoreConnectionStore(preferencesDataStore("Connections", scope) { file }, scope)
    }

    private fun file(): File = File(folder.newFolder(), "connections.preferences_pb")

    private fun scopeFor(test: TestScope): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(test.testScheduler) + Job(test.backgroundScope.coroutineContext[Job]))
}
