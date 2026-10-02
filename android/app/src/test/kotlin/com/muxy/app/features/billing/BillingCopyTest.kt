package com.muxy.app.features.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingCopyTest {
    private val trial = Entitlement.Trial(TRIAL_DURATION_MS)
    private val oneDay = Entitlement.Trial(1)

    @Test
    fun paywallTitlesMatchTheReference() {
        assertEquals("Trial Active", BillingCopy.paywallTitle(trial))
        assertEquals("Trial Ended", BillingCopy.paywallTitle(Entitlement.Expired))
        assertEquals("Unlock Muxy", BillingCopy.paywallTitle(Entitlement.Loading))
        assertEquals("Unlock Muxy", BillingCopy.paywallTitle(Entitlement.Unlocked))
    }

    @Test
    fun paywallSubtitlesPluralizeDaysAndExplainExpiry() {
        assertEquals("3 days left in your free trial.", BillingCopy.paywallSubtitle(trial))
        assertEquals("1 day left in your free trial.", BillingCopy.paywallSubtitle(oneDay))
        assertEquals("Your 3-day trial has ended. Unlock Muxy to keep connecting.", BillingCopy.paywallSubtitle(Entitlement.Expired))
        assertEquals("Pay once to connect to your desktop.", BillingCopy.paywallSubtitle(Entitlement.Loading))
        assertEquals("Pay once to connect to your desktop.", BillingCopy.paywallSubtitle(Entitlement.Unlocked))
    }

    @Test
    fun primaryLabelsIncludeTheLivePriceAndOfferCloseWhenUnlocked() {
        assertEquals("Unlock now — €5.99", BillingCopy.primaryCtaLabel(trial, "€5.99"))
        assertEquals("Unlock now", BillingCopy.primaryCtaLabel(trial, null))
        assertEquals("Unlock now", BillingCopy.primaryCtaLabel(trial, ""))
        assertEquals("Close", BillingCopy.primaryCtaLabel(Entitlement.Unlocked, "$5"))
        assertEquals("Unlock — $5", BillingCopy.paywallButtonLabel("$5"))
        assertEquals("Unlock", BillingCopy.paywallButtonLabel(null))
    }

    @Test
    fun footerCopyMatchesEachStateWithAndWithoutPrices() {
        assertNull(BillingCopy.footerText(Entitlement.Unlocked, "$5"))
        assertEquals("Free for 3 days, then $5", BillingCopy.footerText(Entitlement.Loading, "$5"))
        assertEquals("Free for 3 days", BillingCopy.footerText(Entitlement.Loading, null))
        assertEquals("Trial: 3 days left", BillingCopy.footerText(trial, "$5"))
        assertEquals("Trial: 1 day left", BillingCopy.footerText(oneDay, null))
        assertEquals("Trial ended — unlock for $5", BillingCopy.footerText(Entitlement.Expired, "$5"))
        assertEquals("Trial ended", BillingCopy.footerText(Entitlement.Expired, null))
    }

    @Test
    fun sheetTitlesMatchEveryEntitlement() {
        assertEquals("Unlock Muxy", BillingCopy.sheetTitle(Entitlement.Loading))
        assertEquals("Trial active", BillingCopy.sheetTitle(trial))
        assertEquals("Trial ended", BillingCopy.sheetTitle(Entitlement.Expired))
        assertEquals("Unlocked", BillingCopy.sheetTitle(Entitlement.Unlocked))
    }

    @Test
    fun bulletsUseFirstLaunchInsteadOfPairingAndOtherwiseMatchTheReference() {
        assertEquals(
            listOf(
                "Free for 3 days starting from your first app launch after installation.",
                "After the trial ends, connecting to a desktop requires a one-time purchase of $5.",
                "Pay once. No subscription, no recurring charges.",
                "Tied to your store account — works on all your devices.",
                "If you reinstall or switch devices, tap \"Restore purchase\" to recover access.",
            ),
            BillingCopy.sheetBullets("$5"),
        )
    }

    @Test
    fun bulletsKeepTheReferenceFallbackWhenThePriceIsUnavailable() {
        assertEquals(
            "After the trial ends, connecting to a desktop requires a one-time purchase of a one-time purchase.",
            BillingCopy.sheetBullets(null)[1],
        )
    }
}
