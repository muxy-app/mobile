package com.muxy.app.features.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPurchaseTest {
    @Test
    fun aCompletedUnlockPurchaseIsAccepted() {
        assertTrue(unlockPurchase().isPurchased)
        assertTrue(unlockPurchase(acknowledged = true).isPurchased)
    }

    @Test
    fun pendingAndUnknownPurchasesNeverUnlock() {
        assertFalse(unlockPurchase(PlayPurchaseState.PENDING).isPurchased)
        assertFalse(unlockPurchase(PlayPurchaseState.UNKNOWN).isPurchased)
    }

    @Test
    fun otherProductsNeverUnlock() {
        assertFalse(unlockPurchase().copy(products = listOf("other")).isPurchased)
        assertFalse(unlockPurchase().copy(products = emptyList()).isPurchased)
        assertTrue(unlockPurchase().copy(products = listOf("other", UNLOCK_PRODUCT_ID)).isPurchased)
    }

    @Test
    fun missingTokensNeverUnlock() {
        assertFalse(unlockPurchase().copy(token = "").isPurchased)
        assertFalse(unlockPurchase().copy(token = " ").isPurchased)
    }
}
