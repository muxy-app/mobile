package com.muxy.app.features.billing

class BillingEnforcement(
    isDebug: Boolean,
    debugEnforced: Boolean = false,
    debugTrialMinutes: Long = 0,
) {
    val enforced: Boolean = !isDebug || debugEnforced
    val trialDurationMs: Long =
        if (isDebug && debugTrialMinutes in 1..4320) debugTrialMinutes * 60_000 else TRIAL_DURATION_MS

    fun gates(entitlement: Entitlement): Boolean = enforced && entitlement == Entitlement.Expired
}
