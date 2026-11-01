package com.muxy.app.features.onboarding

import com.muxy.app.R
import com.muxy.app.features.addconnection.DesktopPairingGuide
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSlideTest {
    @Test
    fun desktopSetupUsesTheSharedPairingGuide() {
        val slide = OnboardingSlide.all[1]
        val content = slide.content as OnboardingSlide.Content.Rows
        assertEquals("Get your pairing code", slide.title)
        assertEquals(
            DesktopPairingGuide.steps.map { OnboardingRow(icon = it.icon, title = it.title, body = it.body) },
            content.rows,
        )
    }

    @Test
    fun theLastSlideExplainsScanningAndConfirmation() {
        val slide = OnboardingSlide.all.last()
        val content = slide.content as OnboardingSlide.Content.Hero
        assertEquals("Scan to connect", slide.title)
        assertEquals(R.drawable.ic_qr_code_scanner, content.icon)
        assertEquals(DesktopPairingGuide.SCAN_INSTRUCTIONS, content.body)
    }
}
