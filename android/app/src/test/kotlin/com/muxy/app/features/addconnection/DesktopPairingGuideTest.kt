package com.muxy.app.features.addconnection

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPairingGuideTest {
    @Test
    fun desktopStepsFollowTheMuxy2PairingFlow() {
        assertEquals(listOf("Open Muxy 2", "Manage devices", "Show pairing code"), DesktopPairingGuide.steps.map { it.title })
        assertEquals(
            listOf(
                "Open the Muxy 2 desktop app on your computer.",
                "Click the globe icon in the bottom-left corner, then choose Manage devices.",
                "Click “Show pairing code” to display the QR code.",
            ),
            DesktopPairingGuide.steps.map { it.body },
        )
    }

    @Test
    fun scanningInstructionsIncludeExplicitConfirmation() {
        assertEquals(
            "Tap Scan QR Code to scan the code on your desktop, then tap Add to connect.",
            DesktopPairingGuide.SCAN_INSTRUCTIONS,
        )
    }
}
