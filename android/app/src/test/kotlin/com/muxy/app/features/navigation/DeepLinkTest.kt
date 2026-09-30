package com.muxy.app.features.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkTest {
    @Test
    fun acceptsOnlyPairingLinks() {
        assertTrue(DeepLink.isPairingLink("muxy://pair?host=studio.local"))
        assertFalse(DeepLink.isPairingLink("muxy://open?project=1"))
        assertFalse(DeepLink.isPairingLink("https://pair?host=studio.local"))
        assertFalse(DeepLink.isPairingLink("not a link"))
    }
}
