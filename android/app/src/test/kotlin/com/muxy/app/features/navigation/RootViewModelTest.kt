package com.muxy.app.features.navigation

import com.muxy.app.features.addconnection.PairingCodeInbox
import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.InMemorySettingsStore
import com.muxy.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RootViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun settingsStayUnloadedUntilTheStoreLoads() {
        val store = InMemorySettingsStore(initial = null)
        val viewModel = RootViewModel(store, PairingCodeInbox())
        assertNull(viewModel.settings.value)
        store.load(AppSettings(themeName = "Nord"))
        assertEquals(AppSettings(themeName = "Nord"), viewModel.settings.value)
    }

    @Test
    fun completingOnboardingIsSaved() {
        val store = InMemorySettingsStore()
        RootViewModel(store, PairingCodeInbox()).completeOnboarding()
        assertEquals(true, store.settings.value?.hasCompletedOnboarding)
    }

    @Test
    fun aPairingLinkCompletesOnboardingAndReachesAddConnection() {
        val store = InMemorySettingsStore()
        val inbox = PairingCodeInbox()
        RootViewModel(store, inbox).openPairingLink("muxy://pair?host=studio.local")
        assertEquals(true, store.settings.value?.hasCompletedOnboarding)
        assertEquals("muxy://pair?host=studio.local", inbox.code.value)
    }

    @Test
    fun otherLinksAreIgnored() {
        val store = InMemorySettingsStore()
        val inbox = PairingCodeInbox()
        RootViewModel(store, inbox).openPairingLink("muxy://open?project=1")
        assertEquals(false, store.settings.value?.hasCompletedOnboarding)
        assertNull(inbox.code.value)
    }
}
