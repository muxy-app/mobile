package com.muxy.app.features.addconnection

import androidx.annotation.DrawableRes
import com.muxy.app.R

object DesktopPairingGuide {
    data class Step(
        @param:DrawableRes val icon: Int,
        val title: String,
        val body: String,
    )

    val steps: List<Step> =
        listOf(
            Step(
                icon = R.drawable.ic_desktop_mac,
                title = "Open Muxy 2",
                body = "Open the Muxy 2 desktop app on your computer.",
            ),
            Step(
                icon = R.drawable.ic_public,
                title = "Manage devices",
                body = "Click the globe icon in the bottom-left corner, then choose Manage devices.",
            ),
            Step(
                icon = R.drawable.ic_qr_code_scanner,
                title = "Show pairing code",
                body = "Click “Show pairing code” to display the QR code.",
            ),
        )

    const val SCAN_INSTRUCTIONS = "Tap Scan QR Code to scan the code on your desktop, then tap Add to connect."
}
