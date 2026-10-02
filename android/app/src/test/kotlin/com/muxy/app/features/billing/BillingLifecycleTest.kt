@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.billing

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.settings.AppSettings
import com.muxy.app.persistence.settings.InMemorySettingsStore
import com.muxy.app.testing.InMemoryConnectionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingLifecycleTest {
    private val owner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = error("Not used")
        }

    @Test
    fun startupWaitsForBothStoresThenStartsWithoutOnboardingOrConnections() =
        runTest {
            val settings = InMemorySettingsStore(initial = null)
            val allowConnections = CompletableDeferred<Unit>()
            val memory = InMemoryConnectionStore()
            val connections =
                object : ConnectionStore by memory {
                    override suspend fun load() = memory.load().also { allowConnections.await() }
                }
            val play = FakePlayBilling()
            val trials = FakeTrialStore()
            val repository = BillingRepository(play, trials, BillingEnforcement(isDebug = false), backgroundScope) { 1000 }
            val lifecycle = BillingLifecycle(repository, settings, connections, backgroundScope)
            lifecycle.onStart(owner)
            runCurrent()
            assertFalse(repository.state.value.trialLoaded)
            assertTrue(play.calls.isEmpty())
            settings.load(AppSettings())
            runCurrent()
            assertFalse(repository.state.value.trialLoaded)
            allowConnections.complete(Unit)
            runCurrent()
            assertTrue(repository.state.value.trialLoaded)
            assertEquals(1000L, trials.startedAt)
            assertFalse(settings.settings.value!!.hasCompletedOnboarding)
            assertTrue(connections.load().isEmpty())
        }

    @Test
    fun eachForegroundReturnQueriesPurchasesAndNeverRelocks() =
        runTest {
            val play = FakePlayBilling()
            val repository = BillingRepository(play, FakeTrialStore(), BillingEnforcement(isDebug = false), backgroundScope) { 1000 }
            val lifecycle = BillingLifecycle(repository, InMemorySettingsStore(), InMemoryConnectionStore(), backgroundScope)
            lifecycle.onStart(owner)
            runCurrent()
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), repository.state.value.entitlement)
            play.purchases = listOf(unlockPurchase())
            lifecycle.onStop(owner)
            lifecycle.onStart(owner)
            runCurrent()
            assertEquals(Entitlement.Unlocked, repository.state.value.entitlement)
            play.purchases = emptyList()
            lifecycle.onStop(owner)
            lifecycle.onStart(owner)
            runCurrent()
            assertEquals(3, play.calls.count { it == "query" })
            assertEquals(Entitlement.Unlocked, repository.state.value.entitlement)
        }
}
