@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.billing

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BillingRepositoryTest {
    @Test
    fun startupLoadsTheTrialConnectsFetchesThePriceThenQueriesAndAcknowledges() =
        runTest {
            val fixture = fixture()
            val purchase = unlockPurchase()
            fixture.play.purchases = listOf(purchase)
            fixture.repository.refresh()
            assertEquals(listOf("trial", "connect", "price", "query", "acknowledge"), fixture.play.calls)
            assertEquals(listOf(purchase), fixture.play.acknowledged)
            assertEquals("$5.00", fixture.repository.state.value.productPrice)
            assertTrue(fixture.repository.state.value.ready)
            assertTrue(fixture.repository.state.value.trialLoaded)
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
        }

    @Test
    fun theTrialStartsBeforePlayRespondsAndDoesNotNeedAConnection() =
        runTest {
            val fixture = fixture()
            val connected = CompletableDeferred<Unit>()
            fixture.play.connect = { connected.await() }
            val startup = launch { fixture.repository.refresh() }
            runCurrent()
            assertTrue(fixture.repository.state.value.trialLoaded)
            assertFalse(fixture.repository.state.value.ready)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
            assertEquals(CLOCK, fixture.trials.startedAt)
            connected.complete(Unit)
            startup.join()
            assertTrue(fixture.repository.state.value.ready)
        }

    @Test
    fun foregroundRefreshDoesNotRestartTheTrialOrRefetchAKnownPrice() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.play.calls.clear()
            advanceTimeBy(1000)
            fixture.repository.refresh()
            assertEquals(listOf("connect", "query"), fixture.play.calls)
            assertEquals(CLOCK, fixture.trials.startedAt)
            assertEquals(1, fixture.trials.starts)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS - 1000), fixture.repository.state.value.entitlement)
        }

    @Test
    fun simultaneousRefreshesInitializeTheTrialOnlyOnce() =
        runTest {
            val fixture = fixture()
            val connected = CompletableDeferred<Unit>()
            fixture.play.connect = { connected.await() }
            val first = launch { fixture.repository.refresh() }
            val second = launch { fixture.repository.refresh() }
            runCurrent()
            assertEquals(listOf("trial", "connect"), fixture.play.calls)
            connected.complete(Unit)
            first.join()
            second.join()
            assertEquals(1, fixture.trials.starts)
            assertEquals(1, fixture.play.calls.count { it == "trial" })
            assertEquals(2, fixture.play.calls.count { it == "query" })
        }

    @Test
    fun failedPlaySetupStillStartsTheTrialAndCanRecoverOnForeground() =
        runTest {
            val fixture = fixture()
            fixture.play.connect = { throw billingFailure() }
            fixture.repository.refresh()
            assertTrue(fixture.repository.state.value.ready)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
            assertEquals("Billing failed", fixture.repository.state.value.error)
            fixture.play.connect = {}
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.repository.refresh()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertNull(fixture.repository.state.value.error)
            assertEquals(1, fixture.trials.starts)
        }

    @Test
    fun aPriceFailureDoesNotPreventStartupOrForegroundRestoration() =
        runTest {
            val fixture = fixture()
            fixture.play.fetch = { throw billingFailure() }
            fixture.repository.refresh()
            assertTrue(fixture.repository.state.value.ready)
            assertTrue("query" in fixture.play.calls)
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.repository.refresh()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertEquals(1, fixture.play.acknowledged.size)
            assertNull(fixture.repository.state.value.productPrice)
        }

    @Test
    fun anUnavailableStartupPriceStillAllowsAnExistingPurchaseToUnlock() =
        runTest {
            val fixture = fixture()
            fixture.play.fetch = { throw billingFailure() }
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.repository.refresh()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertNull(fixture.repository.state.value.error)
        }

    @Test
    fun pendingUnknownUnrelatedAndTokenlessPurchasesNeitherUnlockNorAcknowledge() =
        runTest {
            val fixture = fixture()
            fixture.play.purchases =
                listOf(
                    unlockPurchase(PlayPurchaseState.PENDING),
                    unlockPurchase(PlayPurchaseState.UNKNOWN),
                    unlockPurchase().copy(products = listOf("other")),
                    unlockPurchase().copy(token = ""),
                )
            fixture.repository.refresh()
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
            assertTrue(fixture.play.acknowledged.isEmpty())
        }

    @Test
    fun alreadyAcknowledgedPurchasesAreNotAcknowledgedAgain() =
        runTest {
            val fixture = fixture()
            fixture.play.purchases = listOf(unlockPurchase(acknowledged = true))
            fixture.repository.refresh()
            fixture.repository.refresh()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertTrue(fixture.play.acknowledged.isEmpty())
        }

    @Test
    fun buyingIsBusyUntilThePurchaseUpdateAndIgnoresDuplicateTaps() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            fixture.repository.buy(Activity())
            fixture.repository.restore()
            assertTrue(fixture.repository.state.value.purchasing)
            runCurrent()
            assertEquals(1, fixture.play.calls.count { it == "launch" })
            assertTrue(fixture.repository.state.value.purchasing)
            assertFalse(fixture.repository.state.value.restoring)
            fixture.play.emit(PlayPurchaseUpdate.Purchases(listOf(unlockPurchase())))
            runCurrent()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertFalse(fixture.repository.state.value.purchasing)
            assertEquals(1, fixture.play.acknowledged.size)
        }

    @Test
    fun aPendingUpdateStopsTheSpinnerWithoutUnlocking() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            runCurrent()
            fixture.play.emit(PlayPurchaseUpdate.Purchases(listOf(unlockPurchase(PlayPurchaseState.PENDING))))
            runCurrent()
            assertFalse(fixture.repository.state.value.purchasing)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
            assertTrue(fixture.play.acknowledged.isEmpty())
            fixture.play.emit(PlayPurchaseUpdate.Purchases(listOf(unlockPurchase())))
            runCurrent()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
        }

    @Test
    fun aPendingPurchaseWithoutACallbackRecoversOnReturnFromPlay() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            runCurrent()
            fixture.play.purchases = listOf(unlockPurchase(PlayPurchaseState.PENDING))
            fixture.repository.onActivityResumed()
            runCurrent()
            assertFalse(fixture.repository.state.value.purchasing)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
            assertTrue(fixture.play.acknowledged.isEmpty())
            fixture.play.purchases = emptyList()
            fixture.repository.refresh()
            assertFalse(fixture.repository.state.value.purchasing)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
        }

    @Test
    fun anEmptyQueryOnReturnFromPlayEndsThePurchaseWithoutUnlocking() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            runCurrent()
            fixture.repository.onActivityResumed()
            runCurrent()
            assertFalse(fixture.repository.state.value.purchasing)
            assertNull(fixture.repository.state.value.error)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
            fixture.repository.buy(Activity())
            runCurrent()
            assertEquals(2, fixture.play.calls.count { it == "launch" })
            assertTrue(fixture.repository.state.value.purchasing)
        }

    @Test
    fun aCompletedPurchaseWithoutACallbackIsRecoveredAndAcknowledgedOnReturn() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            runCurrent()
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.repository.onActivityResumed()
            runCurrent()
            assertFalse(fixture.repository.state.value.purchasing)
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertEquals(1, fixture.play.acknowledged.size)
        }

    @Test
    fun aFailedRecoveryQueryShowsAnErrorWithoutTrappingTheUser() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            runCurrent()
            fixture.play.query = { throw billingFailure() }
            fixture.repository.onActivityResumed()
            runCurrent()
            assertFalse(fixture.repository.state.value.purchasing)
            assertEquals("Billing failed", fixture.repository.state.value.error)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
            fixture.play.query = {}
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.repository.restore()
            runCurrent()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
        }

    @Test
    fun aRecoveryQueryKeepsBuyingDisabledUntilItFinishes() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            runCurrent()
            val queried = CompletableDeferred<Unit>()
            fixture.play.query = { queried.await() }
            fixture.repository.onActivityResumed()
            runCurrent()
            fixture.repository.onActivityResumed()
            fixture.repository.buy(Activity())
            fixture.repository.restore()
            runCurrent()
            assertTrue(fixture.repository.state.value.purchasing)
            assertEquals(1, fixture.play.calls.count { it == "launch" })
            queried.complete(Unit)
            runCurrent()
            assertFalse(fixture.repository.state.value.busy)
            assertEquals(2, fixture.play.calls.count { it == "query" })
        }

    @Test
    fun aProcessRefreshWhilePlayIsOpenDoesNotEndTheActivePurchase() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            runCurrent()
            fixture.repository.refresh()
            assertTrue(fixture.repository.state.value.purchasing)
            fixture.play.purchases = listOf(unlockPurchase(PlayPurchaseState.PENDING))
            fixture.repository.refresh()
            assertTrue(fixture.repository.state.value.purchasing)
            fixture.repository.onActivityResumed()
            runCurrent()
            assertFalse(fixture.repository.state.value.purchasing)
        }

    @Test
    fun resumingBeforePlayLaunchDoesNotCancelPurchasePreparation() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            val connected = CompletableDeferred<Unit>()
            fixture.play.connect = { connected.await() }
            fixture.repository.buy(Activity())
            runCurrent()
            fixture.repository.onActivityResumed()
            runCurrent()
            assertTrue(fixture.repository.state.value.purchasing)
            connected.complete(Unit)
            runCurrent()
            assertTrue(fixture.repository.state.value.purchasing)
            assertEquals(1, fixture.play.calls.count { it == "launch" })
            assertEquals(1, fixture.play.calls.count { it == "query" })
        }

    @Test
    fun resumingWithoutAnActivePlayFlowDoesNotQueryOrStartTheTrial() =
        runTest {
            val fixture = fixture()
            fixture.repository.onActivityResumed()
            runCurrent()
            assertTrue(fixture.play.calls.isEmpty())
            assertFalse(fixture.repository.state.value.trialLoaded)
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            runCurrent()
            fixture.play.emit(PlayPurchaseUpdate.Purchases(emptyList()))
            runCurrent()
            fixture.play.calls.clear()
            fixture.repository.onActivityResumed()
            runCurrent()
            assertTrue(fixture.play.calls.isEmpty())
            assertFalse(fixture.repository.state.value.purchasing)
        }

    @Test
    fun aCancelledUpdateClearsAnExistingErrorSilently() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.play.emit(PlayPurchaseUpdate.Failed(billingFailure()))
            runCurrent()
            assertEquals("Billing failed", fixture.repository.state.value.error)
            fixture.play.emit(PlayPurchaseUpdate.Failed(billingFailure(PlayBillingFailure.CANCELLED)))
            runCurrent()
            assertNull(fixture.repository.state.value.error)
            assertFalse(fixture.repository.state.value.purchasing)
        }

    @Test
    fun aCancelledLaunchClearsTheSpinnerWithoutAnError() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.play.launch = { throw billingFailure(PlayBillingFailure.CANCELLED) }
            fixture.repository.buy(Activity())
            runCurrent()
            assertNull(fixture.repository.state.value.error)
            assertFalse(fixture.repository.state.value.purchasing)
        }

    @Test
    fun launchFailuresAreShownAndDoNotLeaveThePaywallLocked() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.play.launch = { throw billingFailure() }
            fixture.repository.buy(Activity())
            runCurrent()
            assertEquals("Billing failed", fixture.repository.state.value.error)
            assertFalse(fixture.repository.state.value.purchasing)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
        }

    @Test
    fun unexpectedFailuresDoNotExposeExceptionDetailsToTheUi() =
        runTest {
            val fixture = fixture()
            fixture.play.query = { throw IOException("sensitive debug details") }
            fixture.repository.refresh()
            assertEquals("Couldn't connect to Google Play. Try again.", fixture.repository.state.value.error)
        }

    @Test
    fun restoringIsBusyUntilItsQueryCompletesAndPreventsBuying() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            val queried = CompletableDeferred<Unit>()
            fixture.play.query = { queried.await() }
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.repository.restore()
            fixture.repository.restore()
            fixture.repository.buy(Activity())
            runCurrent()
            assertTrue(fixture.repository.state.value.restoring)
            assertFalse(fixture.repository.state.value.purchasing)
            assertFalse("launch" in fixture.play.calls)
            queried.complete(Unit)
            runCurrent()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertFalse(fixture.repository.state.value.restoring)
            assertEquals(2, fixture.play.calls.count { it == "query" })
        }

    @Test
    fun failedRestoreShowsTheErrorAndEndsItsBusyState() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.play.query = { throw billingFailure() }
            fixture.repository.restore()
            runCurrent()
            assertFalse(fixture.repository.state.value.restoring)
            assertEquals("Billing failed", fixture.repository.state.value.error)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
        }

    @Test
    fun restoreWithNoPurchaseDoesNotUnlock() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.repository.restore()
            runCurrent()
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
            assertFalse(fixture.repository.state.value.restoring)
            assertNull(fixture.repository.state.value.error)
        }

    @Test
    fun missingPendingOrFailedRefreshesNeverRelockAPurchasedApp() =
        runTest {
            val fixture = fixture()
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.repository.refresh()
            fixture.play.purchases = emptyList()
            fixture.repository.refresh()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            fixture.play.purchases = listOf(unlockPurchase(PlayPurchaseState.PENDING))
            fixture.repository.refresh()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            fixture.play.query = { throw billingFailure() }
            fixture.repository.refresh()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertEquals(Entitlement.Unlocked, fixture.repository.refreshEntitlement())
        }

    @Test
    fun anAlreadyOwnedLaunchQueriesAndAcknowledgesThePurchase() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.play.launch = { throw billingFailure(PlayBillingFailure.ALREADY_OWNED) }
            fixture.repository.buy(Activity())
            runCurrent()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertFalse(fixture.repository.state.value.purchasing)
            assertNull(fixture.repository.state.value.error)
            assertEquals(1, fixture.play.acknowledged.size)
        }

    @Test
    fun anAlreadyOwnedUpdateWithoutARestorablePurchaseShowsAnError() =
        runTest {
            val fixture = fixture()
            fixture.repository.refresh()
            fixture.play.emit(PlayPurchaseUpdate.Failed(billingFailure(PlayBillingFailure.ALREADY_OWNED)))
            runCurrent()
            assertEquals("Billing failed", fixture.repository.state.value.error)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
        }

    @Test
    fun acknowledgementFailuresAreRetriedWithoutRelocking() =
        runTest {
            val fixture = fixture()
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.play.acknowledge = { throw billingFailure() }
            fixture.repository.refresh()
            runCurrent()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
            assertTrue(fixture.play.acknowledged.isEmpty())
            fixture.play.acknowledge = {}
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, fixture.play.acknowledged.size)
            assertEquals(2, fixture.play.calls.count { it == "acknowledge" })
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(2, fixture.play.calls.count { it == "acknowledge" })
        }

    @Test
    fun aLaterAcknowledgedQueryRemovesTheRetry() =
        runTest {
            val fixture = fixture()
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.play.acknowledge = { throw billingFailure() }
            fixture.repository.refresh()
            runCurrent()
            fixture.play.purchases = listOf(unlockPurchase(acknowledged = true))
            fixture.repository.refresh()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, fixture.play.calls.count { it == "acknowledge" })
        }

    @Test
    fun theMinuteTickExpiresTheTrialWithoutAnyConnections() =
        runTest {
            val fixture = fixture(BillingEnforcement(isDebug = true, debugEnforced = true, debugTrialMinutes = 2))
            fixture.repository.refresh()
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(Entitlement.Trial(60_000), fixture.repository.state.value.entitlement)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(Entitlement.Expired, fixture.repository.state.value.entitlement)
        }

    @Test
    fun aConnectionTapUpdatesBothTheGateAndUiBeforeTheNextTick() =
        runTest {
            val fixture = fixture(BillingEnforcement(isDebug = true, debugEnforced = true, debugTrialMinutes = 2))
            fixture.trials.startedAt = CLOCK - 119_999
            fixture.repository.refresh()
            runCurrent()
            advanceTimeBy(1)
            assertEquals(Entitlement.Expired, fixture.repository.refreshEntitlement())
            assertEquals(Entitlement.Expired, fixture.repository.state.value.entitlement)
        }

    @Test
    fun aTrialStorageFailureDoesNotGrantFreeAccessAndStillAllowsRestore() =
        runTest {
            val fixture = fixture()
            fixture.trials.error = IOException("Storage unavailable")
            fixture.repository.refresh()
            assertTrue(fixture.repository.state.value.trialLoaded)
            assertTrue(fixture.repository.state.value.ready)
            assertEquals(Entitlement.Expired, fixture.repository.state.value.entitlement)
            assertEquals("Couldn't start the trial securely. Restart Muxy to try again.", fixture.repository.state.value.error)
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.repository.restore()
            runCurrent()
            assertEquals(Entitlement.Unlocked, fixture.repository.state.value.entitlement)
        }

    @Test
    fun refreshRetriesAFailedTrialWrite() =
        runTest {
            val fixture = fixture()
            fixture.trials.error = IOException("Storage unavailable")
            fixture.repository.refresh()
            fixture.trials.error = null
            fixture.repository.refresh()
            assertEquals(1, fixture.trials.starts)
            assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), fixture.repository.state.value.entitlement)
        }

    @Test
    fun cancellationPropagatesWithoutBecomingABillingError() =
        runTest {
            val fixture = fixture()
            fixture.play.connect = { throw CancellationException("Cancelled") }
            assertTrue(runCatching { fixture.repository.refresh() }.exceptionOrNull() is CancellationException)
            assertNull(fixture.repository.state.value.error)
            assertFalse(fixture.repository.state.value.ready)
        }

    @Test
    fun purchasedAppsDoNotLaunchAnotherPurchaseFlow() =
        runTest {
            val fixture = fixture()
            fixture.play.purchases = listOf(unlockPurchase())
            fixture.repository.refresh()
            fixture.repository.buy(Activity())
            runCurrent()
            assertFalse("launch" in fixture.play.calls)
            assertFalse(fixture.repository.state.value.purchasing)
        }

    private fun TestScope.fixture(enforcement: BillingEnforcement = BillingEnforcement(isDebug = false)): Fixture {
        val calls = mutableListOf<String>()
        val play = FakePlayBilling(calls)
        val trials = FakeTrialStore(calls)
        val repository = BillingRepository(play, trials, enforcement, backgroundScope) { CLOCK + testScheduler.currentTime }
        return Fixture(repository, play, trials)
    }

    private data class Fixture(
        val repository: BillingRepository,
        val play: FakePlayBilling,
        val trials: FakeTrialStore,
    )

    private companion object {
        const val CLOCK = 1_000_000L
    }
}
