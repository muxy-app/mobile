package com.muxy.app.features.navigation

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun opensAViewIntentsLink() {
        assertEquals("muxy://pair?host=a", DeepLink.linkToOpen(Intent.ACTION_VIEW, 0, "muxy://pair?host=a"))
    }

    @Test
    fun ignoresOtherActionsAndRelaunchesFromRecents() {
        assertNull(DeepLink.linkToOpen(Intent.ACTION_MAIN, 0, "muxy://pair?host=a"))
        assertNull(DeepLink.linkToOpen(Intent.ACTION_VIEW, Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY, "muxy://pair?host=a"))
    }
}
