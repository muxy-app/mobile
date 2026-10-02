package com.muxy.app.features.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementTest {
    @Test
    fun aPurchaseWinsOverEveryTrialState() {
        for (startedAt in listOf(null, 1L, TRIAL_DURATION_MS)) {
            assertEquals(Entitlement.Unlocked, computeEntitlement(true, startedAt, TRIAL_DURATION_MS + 1))
        }
    }

    @Test
    fun anUnloadedTrialIsLoading() {
        assertEquals(Entitlement.Loading, computeEntitlement(false, null, 1))
    }

    @Test
    fun theTrialLastsExactlyThreeDays() {
        assertEquals(259_200_000L, TRIAL_DURATION_MS)
        assertEquals(Entitlement.Trial(TRIAL_DURATION_MS), computeEntitlement(false, 1, 1))
        assertEquals(Entitlement.Trial(TRIAL_DURATION_MS / 2), computeEntitlement(false, 1, 1 + TRIAL_DURATION_MS / 2))
    }

    @Test
    fun theTrialExpiresAtItsBoundary() {
        assertEquals(Entitlement.Trial(1), computeEntitlement(false, 0, TRIAL_DURATION_MS - 1))
        assertEquals(Entitlement.Expired, computeEntitlement(false, 0, TRIAL_DURATION_MS))
        assertEquals(Entitlement.Expired, computeEntitlement(false, 0, TRIAL_DURATION_MS + 1))
    }

    @Test
    fun aShortenedTrialUsesItsOwnBoundary() {
        assertEquals(Entitlement.Trial(1), computeEntitlement(false, 1, 120_000, 120_000))
        assertEquals(Entitlement.Expired, computeEntitlement(false, 1, 120_001, 120_000))
    }

    @Test
    fun remainingDaysRoundUpPartialDays() {
        assertEquals(1, daysRemaining(1))
        assertEquals(1, daysRemaining(DAY_MS))
        assertEquals(2, daysRemaining(DAY_MS + 1))
        assertEquals(3, daysRemaining(TRIAL_DURATION_MS))
    }

    @Test
    fun remainingDaysAreAtLeastOne() {
        assertEquals(1, daysRemaining(0))
        assertEquals(1, daysRemaining(-100))
    }
}
