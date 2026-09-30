package com.muxy.app.persistence.workspaces

import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.persistence.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class DataStoreWorkspaceSelectionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun savesLoadsAndClearsASelectionPerConnection() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext[Job]))
            val file = File(folder.newFolder(), "workspaces.preferences_pb")
            val dataStore = preferencesDataStore("Workspace selections", scope) { file }
            val store = DataStoreWorkspaceSelectionStore(dataStore)
            val connection = UUID.randomUUID()
            val other = UUID.randomUUID()
            val workspace = UUID.randomUUID()
            store.save(workspace, connection)
            assertEquals(workspace, store.load(connection))
            assertNull(store.load(other))
            assertEquals(
                workspace.uuidString,
                dataStore.data.first()[stringPreferencesKey("muxy.selectedWorkspace.${connection.uuidString}")],
            )
            store.save(null, connection)
            assertNull(store.load(connection))
        }
}
