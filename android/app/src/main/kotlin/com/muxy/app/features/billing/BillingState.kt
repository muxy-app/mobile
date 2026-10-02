package com.muxy.app.features.billing

data class BillingState(
    val trialLoaded: Boolean = false,
    val ready: Boolean = false,
    val entitlement: Entitlement = Entitlement.Loading,
    val productPrice: String? = null,
    val purchasing: Boolean = false,
    val restoring: Boolean = false,
    val error: String? = null,
) {
    val busy: Boolean
        get() = purchasing || restoring
}
