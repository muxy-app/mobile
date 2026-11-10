package com.muxy.app.features.settings

import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.InMemorySettingsStore
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.fakePushCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val push = fakePushCenter(CoroutineScope(SupervisorJob()))

    @Test
    fun startsFromTheLoadedSettings() {
        val viewModel = SettingsViewModel(InMemorySettingsStore(AppSettings(themeName = "Nord")), push)
        assertEquals("Nord", viewModel.settings.value.themeName)
    }

    @Test
    fun selectingAThemeSavesIt() {
        val store = InMemorySettingsStore()
        val viewModel = SettingsViewModel(store, push)
        viewModel.selectTheme("Dracula")
        assertEquals("Dracula", store.settings.value?.themeName)
        assertEquals("Dracula", viewModel.settings.value.themeName)
    }

    @Test
    fun followsChangesMadeElsewhere() {
        val store = InMemorySettingsStore()
        val viewModel = SettingsViewModel(store, push)
        store.load(AppSettings(themeName = "TokyoNight"))
        assertEquals("TokyoNight", viewModel.settings.value.themeName)
    }
}
