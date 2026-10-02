package com.muxy.app.features.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingEnforcementTest {
    @Test
    fun debugBuildsAreNotEnforcedByDefault() {
        assertFalse(BillingEnforcement(isDebug = true).enforced)
    }

    @Test
    fun debugCanEnforceAndShortenTheTrial() {
        val enforcement = BillingEnforcement(isDebug = true, debugEnforced = true, debugTrialMinutes = 2)
        assertTrue(enforcement.enforced)
        assertEquals(120_000L, enforcement.trialDurationMs)
    }

    @Test
    fun releaseAlwaysEnforcesWithAThreeDayTrialRegardlessOfOverrides() {
        for (enforced in listOf(false, true)) {
            for (minutes in listOf(-1L, 0L, 1L, 2L, 4320L, Long.MAX_VALUE)) {
                val enforcement = BillingEnforcement(isDebug = false, debugEnforced = enforced, debugTrialMinutes = minutes)
                assertTrue(enforcement.enforced)
                assertEquals(TRIAL_DURATION_MS, enforcement.trialDurationMs)
            }
        }
    }

    @Test
    fun absentOrInvalidDebugDurationsKeepThreeDays() {
        for (minutes in listOf(-1L, 0L, 4321L, Long.MAX_VALUE)) {
            assertEquals(TRIAL_DURATION_MS, BillingEnforcement(isDebug = true, debugTrialMinutes = minutes).trialDurationMs)
        }
    }

    @Test
    fun onlyExpiredEntitlementIsGatedWhenEnforced() {
        val enforcement = BillingEnforcement(isDebug = false)
        for (entitlement in listOf(Entitlement.Loading, Entitlement.Trial(1), Entitlement.Unlocked, Entitlement.Expired)) {
            assertEquals(entitlement == Entitlement.Expired, enforcement.gates(entitlement))
        }
    }

    @Test
    fun unenforcedBuildsNeverGate() {
        val enforcement = BillingEnforcement(isDebug = true)
        for (entitlement in listOf(Entitlement.Loading, Entitlement.Trial(1), Entitlement.Unlocked, Entitlement.Expired)) {
            assertFalse(enforcement.gates(entitlement))
        }
    }
}
