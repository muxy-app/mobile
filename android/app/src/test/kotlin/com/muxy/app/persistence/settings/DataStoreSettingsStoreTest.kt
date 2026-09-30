package com.muxy.app.persistence.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.persistence.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DataStoreSettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val changed =
        AppSettings(
            hasCompletedOnboarding = true,
            themeName = "Nord",
            useNerdFont = false,
            autoFocusTerminal = true,
            demoMode = true,
        )

    @Test
    fun startsWithTheSpecifiedDefaults() =
        runTest {
            val settings = store(scopeFor(this), settingsFile()).settings.filterNotNull().first()
            assertEquals(false, settings.hasCompletedOnboarding)
            assertEquals("Muxy", settings.themeName)
            assertEquals(true, settings.useNerdFont)
            assertEquals(false, settings.autoFocusTerminal)
            assertEquals(false, settings.demoMode)
        }

    @Test
    fun readsTheIosKeyNames() =
        runTest {
            val scope = scopeFor(this)
            val dataStore = preferencesDataStore("Settings", scope) { settingsFile() }
            dataStore.edit {
                it[booleanPreferencesKey("muxy.hasCompletedOnboarding")] = true
                it[stringPreferencesKey("muxy.settings.theme")] = "Nord"
                it[booleanPreferencesKey("muxy.settings.useNerdFont")] = false
                it[booleanPreferencesKey("muxy.settings.autoFocusTerminal")] = true
                it[booleanPreferencesKey("muxy.settings.demoMode")] = true
            }
            assertEquals(changed, DataStoreSettingsStore(dataStore, scope).settings.filterNotNull().first())
        }

    @Test
    fun writesTheIosKeyNames() =
        runTest {
            val scope = scopeFor(this)
            val dataStore = preferencesDataStore("Settings", scope) { settingsFile() }
            DataStoreSettingsStore(dataStore, scope).update { changed }
            val saved = dataStore.data.first()
            assertEquals(true, saved[booleanPreferencesKey("muxy.hasCompletedOnboarding")])
            assertEquals("Nord", saved[stringPreferencesKey("muxy.settings.theme")])
            assertEquals(false, saved[booleanPreferencesKey("muxy.settings.useNerdFont")])
            assertEquals(true, saved[booleanPreferencesKey("muxy.settings.autoFocusTerminal")])
            assertEquals(true, saved[booleanPreferencesKey("muxy.settings.demoMode")])
        }

    @Test
    fun updatesAreVisibleRightAway() =
        runTest {
            val store = store(scopeFor(this), settingsFile())
            store.update { changed }
            assertEquals(changed, store.settings.first { it == changed })
        }

    @Test
    fun updatesSurviveANewStoreOnTheSameFile() =
        runTest {
            val file = settingsFile()
            val firstScope = scopeFor(this)
            store(firstScope, file).update { changed }
            firstScope.cancel()
            advanceUntilIdle()
            assertEquals(changed, store(scopeFor(this), file).settings.filterNotNull().first())
        }

    @Test
    fun anUnreadableFileResetsToTheDefaults() =
        runTest {
            val file = settingsFile().apply { writeText("not a preferences file") }
            val store = store(scopeFor(this), file)
            assertEquals(AppSettings(), store.settings.filterNotNull().first())
            store.update { it.copy(themeName = "Dracula") }
            assertEquals("Dracula", store.settings.first { it?.themeName == "Dracula" }?.themeName)
        }

    @Test
    fun aFailedReadShowsTheDefaultsAndRecovers() =
        runTest {
            val scope = scopeFor(this)
            val dataStore = preferencesDataStore("Settings", scope) { settingsFile() }
            dataStore.edit { it[stringPreferencesKey("muxy.settings.theme")] = "Nord" }
            val store = DataStoreSettingsStore(FailingFirstRead(dataStore), scope)
            assertEquals(AppSettings(), store.settings.filterNotNull().first())
            assertEquals("Nord", store.settings.first { it?.themeName == "Nord" }?.themeName)
        }

    private fun settingsFile(): File = File(folder.newFolder(), "settings.preferences_pb")

    private fun scopeFor(test: TestScope): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(test.testScheduler) + Job(test.backgroundScope.coroutineContext[Job]))

    private fun store(
        scope: CoroutineScope,
        file: File,
    ): DataStoreSettingsStore = DataStoreSettingsStore(preferencesDataStore("Settings", scope) { file }, scope)

    private class FailingFirstRead(
        private val delegate: DataStore<Preferences>,
    ) : DataStore<Preferences> {
        private var hasFailed = false

        override val data: Flow<Preferences> =
            flow {
                if (!hasFailed) {
                    hasFailed = true
                    throw IOException("The disk is unavailable")
                }
                emitAll(delegate.data)
            }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = delegate.updateData(transform)
    }
}
