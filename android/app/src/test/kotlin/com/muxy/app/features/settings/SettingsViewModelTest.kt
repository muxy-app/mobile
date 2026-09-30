package com.muxy.app.features.settings

import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.InMemorySettingsStore
import com.muxy.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun startsFromTheLoadedSettings() {
        val viewModel = SettingsViewModel(InMemorySettingsStore(AppSettings(themeName = "Nord")))
        assertEquals("Nord", viewModel.settings.value.themeName)
    }

    @Test
    fun selectingAThemeSavesIt() {
        val store = InMemorySettingsStore()
        val viewModel = SettingsViewModel(store)
        viewModel.selectTheme("Dracula")
        assertEquals("Dracula", store.settings.value?.themeName)
        assertEquals("Dracula", viewModel.settings.value.themeName)
    }

    @Test
    fun followsChangesMadeElsewhere() {
        val store = InMemorySettingsStore()
        val viewModel = SettingsViewModel(store)
        store.load(AppSettings(themeName = "TokyoNight"))
        assertEquals("TokyoNight", viewModel.settings.value.themeName)
    }
}
