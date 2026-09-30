package com.muxy.app.persistence.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
    fun startsWithTheDefaults() =
        runTest {
            val store = store(scopeFor(this), settingsFile())
            assertEquals(AppSettings(), store.settings.filterNotNull().first())
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

    private fun settingsFile(): File = File(folder.newFolder(), "settings.preferences_pb")

    private fun scopeFor(test: TestScope): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(test.testScheduler) + Job(test.backgroundScope.coroutineContext[Job]))

    private fun store(
        scope: CoroutineScope,
        file: File,
    ): DataStoreSettingsStore = DataStoreSettingsStore(DataStoreSettingsStore.preferences(scope) { file }, scope)
}
