package com.muxy.app.persistence.worktrees

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Worktree
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.persistence.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class DataStoreWorktreeCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private val connection = UUID.fromString("AAAAAAAA-0000-4000-8000-000000000000")
    private val project = UUID.fromString("BBBBBBBB-0000-4000-8000-000000000000")
    private val worktree = Worktree(UUID.randomUUID(), "Main", "/work/muxy", "main", true, false, "2026-10-01T00:00:00Z")
    private val key = stringPreferencesKey("muxy.worktrees.${connection.uuidString}.${project.uuidString}")

    private fun TestScope.dataStore() =
        preferencesDataStore(
            "Worktrees",
            CoroutineScope(StandardTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext[Job])),
        ) { File(folder.newFolder(), "worktrees.preferences_pb") }

    @Test
    fun cacheUsesExactConnectionAndProjectKeyAndSurvivesStoreRecreation() =
        runTest {
            val dataStore = dataStore()
            DataStoreWorktreeCache(dataStore).save(listOf(worktree), connection, project)
            val store = DataStoreWorktreeCache(dataStore)
            assertEquals(listOf(worktree), store.load(connection, project))
            assertEquals(listOf(worktree), ProtocolJson.decodeFromString<List<Worktree>>(dataStore.data.first()[key]!!))
            assertNull(store.load(UUID.randomUUID(), project))
            assertNull(store.load(connection, UUID.randomUUID()))
            assertEquals(
                setOf(key),
                dataStore.data
                    .first()
                    .asMap()
                    .keys,
            )
        }

    @Test
    fun replacementCanCacheAnEmptyListWithoutAffectingOtherProjects() =
        runTest {
            val store = DataStoreWorktreeCache(dataStore())
            val other = UUID.randomUUID()
            store.save(listOf(worktree), connection, project)
            store.save(listOf(worktree.copy(name = "Other")), connection, other)
            store.save(emptyList(), connection, project)
            assertEquals(emptyList<Worktree>(), store.load(connection, project))
            assertEquals("Other", store.load(connection, other)?.single()?.name)
        }

    @Test
    fun malformedCacheIsIgnoredAndCanBeReplaced() =
        runTest {
            val dataStore = dataStore()
            val store = DataStoreWorktreeCache(dataStore)
            for (invalid in listOf("not json", "{}", "[{\"id\":\"invalid\"}]")) {
                dataStore.edit { it[key] = invalid }
                assertNull(store.load(connection, project))
            }
            store.save(listOf(worktree), connection, project)
            assertEquals(listOf(worktree), store.load(connection, project))
        }
}
