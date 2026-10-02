package com.muxy.app.features.billing

object BillingCopy {
    fun paywallTitle(entitlement: Entitlement): String =
        when (entitlement) {
            is Entitlement.Trial -> "Trial Active"
            Entitlement.Expired -> "Trial Ended"
            else -> "Unlock Muxy"
        }

    fun paywallSubtitle(entitlement: Entitlement): String =
        when (entitlement) {
            is Entitlement.Trial -> "${daysLeft(entitlement)} left in your free trial."
            Entitlement.Expired -> "Your 3-day trial has ended. Unlock Muxy to keep connecting."
            else -> "Pay once to connect to your desktop."
        }

    fun primaryCtaLabel(
        entitlement: Entitlement,
        price: String?,
    ): String = if (entitlement == Entitlement.Unlocked) "Close" else "Unlock now${priceSuffix(price)}"

    fun paywallButtonLabel(price: String?): String = "Unlock${priceSuffix(price)}"

    fun footerText(
        entitlement: Entitlement,
        price: String?,
    ): String? =
        when (entitlement) {
            Entitlement.Unlocked -> null
            Entitlement.Loading -> if (price.isNullOrEmpty()) "Free for 3 days" else "Free for 3 days, then $price"
            is Entitlement.Trial -> "Trial: ${daysLeft(entitlement)} left"
            Entitlement.Expired -> if (price.isNullOrEmpty()) "Trial ended" else "Trial ended — unlock for $price"
        }

    fun sheetTitle(entitlement: Entitlement): String =
        when (entitlement) {
            Entitlement.Loading -> "Unlock Muxy"
            is Entitlement.Trial -> "Trial active"
            Entitlement.Expired -> "Trial ended"
            Entitlement.Unlocked -> "Unlocked"
        }

    fun sheetBullets(price: String?): List<String> =
        listOf(
            "Free for 3 days starting from your first app launch after installation.",
            "After the trial ends, connecting to a desktop requires a one-time purchase of ${price ?: "a one-time purchase"}.",
            "Pay once. No subscription, no recurring charges.",
            "Tied to your store account — works on all your devices.",
            "If you reinstall or switch devices, tap \"Restore purchase\" to recover access.",
        )

    private fun daysLeft(entitlement: Entitlement.Trial): String {
        val days = daysRemaining(entitlement.msRemaining)
        return "$days day${if (days == 1) "" else "s"}"
    }

    private fun priceSuffix(price: String?): String = if (price.isNullOrEmpty()) "" else " — $price"
}
