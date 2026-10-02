package com.muxy.app.features.navigation

import androidx.lifecycle.ViewModelStore
import com.muxy.app.features.addconnection.AddConnectionInbox
import com.muxy.app.features.addconnection.AddConnectionRequest
import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.InMemorySettingsStore
import com.muxy.app.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RootViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val startupReady = MutableStateFlow(true)

    @Test
    fun settingsStayUnloadedUntilTheStoreLoads() {
        val store = InMemorySettingsStore(initial = null)
        val viewModel = RootViewModel(store, AddConnectionInbox(), startupReady)
        assertNull(viewModel.settings.value)
        store.load(AppSettings(themeName = "Nord"))
        assertEquals(AppSettings(themeName = "Nord"), viewModel.settings.value)
    }

    @Test
    fun completingOnboardingIsSaved() {
        val store = InMemorySettingsStore()
        RootViewModel(store, AddConnectionInbox(), startupReady).completeOnboarding()
        assertEquals(true, store.settings.value?.hasCompletedOnboarding)
    }

    @Test
    fun aPairingLinkCompletesOnboardingAndReachesAddConnection() {
        val store = InMemorySettingsStore()
        val inbox = AddConnectionInbox()
        RootViewModel(store, inbox, startupReady).openPairingLink("muxy://pair?host=studio.local")
        assertEquals(true, store.settings.value?.hasCompletedOnboarding)
        assertEquals(AddConnectionRequest.PairingCode("muxy://pair?host=studio.local"), inbox.request.value)
    }

    @Test
    fun otherLinksAreIgnored() {
        val store = InMemorySettingsStore()
        val inbox = AddConnectionInbox()
        RootViewModel(store, inbox, startupReady).openPairingLink("muxy://open?project=1")
        assertEquals(false, store.settings.value?.hasCompletedOnboarding)
        assertNull(inbox.request.value)
    }

    @Test
    fun aPairingLinkDoesNotReadOrChangeOnboardingBeforeImportFinishes() {
        startupReady.value = false
        val store = InMemorySettingsStore(initial = null)
        val inbox = AddConnectionInbox()
        val viewModel = RootViewModel(store, inbox, startupReady)
        viewModel.openPairingLink("muxy://pair?host=studio.local")
        assertNull(store.settings.value)
        assertNull(inbox.request.value)
        store.load(AppSettings(themeName = "Nord"))
        assertEquals(false, store.settings.value?.hasCompletedOnboarding)
        startupReady.value = true
        assertEquals(AppSettings(hasCompletedOnboarding = true, themeName = "Nord"), store.settings.value)
        assertEquals(AddConnectionRequest.PairingCode("muxy://pair?host=studio.local"), inbox.request.value)
    }

    @Test
    fun aPendingLinkSurvivesCancellationOfTheCallingActivityScope() {
        startupReady.value = false
        val store = InMemorySettingsStore()
        val inbox = AddConnectionInbox()
        val viewModel = RootViewModel(store, inbox, startupReady)
        val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        activityScope.launch { viewModel.openPairingLink("muxy://pair?host=studio.local") }
        assertNull(inbox.request.value)
        activityScope.cancel()
        startupReady.value = true
        assertEquals(true, store.settings.value?.hasCompletedOnboarding)
        assertEquals(AddConnectionRequest.PairingCode("muxy://pair?host=studio.local"), inbox.take())
        assertNull(inbox.request.value)
    }

    @Test
    fun readinessChangesDoNotReplayAConsumedLink() {
        startupReady.value = false
        val store = InMemorySettingsStore()
        val inbox = AddConnectionInbox()
        RootViewModel(store, inbox, startupReady).openPairingLink("muxy://pair?host=studio.local")
        startupReady.value = true
        assertEquals(AddConnectionRequest.PairingCode("muxy://pair?host=studio.local"), inbox.take())
        startupReady.value = false
        startupReady.value = true
        assertNull(inbox.request.value)
    }

    @Test
    fun clearingTheViewModelCancelsTheDeferredLink() {
        startupReady.value = false
        val store = InMemorySettingsStore()
        val inbox = AddConnectionInbox()
        val viewModel = RootViewModel(store, inbox, startupReady)
        val owner = ViewModelStore().apply { put("root", viewModel) }
        viewModel.openPairingLink("muxy://pair?host=studio.local")
        owner.clear()
        startupReady.value = true
        assertEquals(false, store.settings.value?.hasCompletedOnboarding)
        assertNull(inbox.request.value)
    }
}
