package com.muxy.app.features.billing

import kotlin.math.ceil

sealed interface Entitlement {
    data object Loading : Entitlement

    data class Trial(
        val msRemaining: Long,
    ) : Entitlement

    data object Expired : Entitlement

    data object Unlocked : Entitlement
}

const val DAY_MS = 24 * 60 * 60 * 1000L
const val TRIAL_DURATION_MS = 3 * DAY_MS

fun computeEntitlement(
    purchased: Boolean,
    trialStartedAt: Long?,
    now: Long,
    trialDurationMs: Long = TRIAL_DURATION_MS,
): Entitlement {
    if (purchased) return Entitlement.Unlocked
    if (trialStartedAt == null) return Entitlement.Loading
    val remaining = trialDurationMs - (now - trialStartedAt)
    return if (remaining > 0) Entitlement.Trial(remaining) else Entitlement.Expired
}

fun daysRemaining(msRemaining: Long): Int = ceil(msRemaining.toDouble() / DAY_MS).toInt().coerceAtLeast(1)
