package com.muxy.app.features.onboarding

import androidx.annotation.DrawableRes
import com.muxy.app.R
import com.muxy.app.features.addconnection.DesktopPairingGuide

data class OnboardingSlide(
    val title: String,
    val content: Content,
) {
    sealed interface Content {
        data class Hero(
            @param:DrawableRes val icon: Int?,
            val body: String,
        ) : Content

        data class Rows(
            val rows: List<OnboardingRow>,
        ) : Content
    }

    companion object {
        val all: List<OnboardingSlide> =
            listOf(
                OnboardingSlide(
                    title = "Welcome to Muxy",
                    content =
                        Content.Hero(
                            icon = null,
                            body =
                                "The remote control for your desktop terminal. " +
                                    "Drive sessions, switch projects, and ship changes from your phone.",
                        ),
                ),
                OnboardingSlide(
                    title = "Get your pairing code",
                    content =
                        Content.Rows(
                            DesktopPairingGuide.steps.map { step ->
                                OnboardingRow(icon = step.icon, title = step.title, body = step.body)
                            },
                        ),
                ),
                OnboardingSlide(
                    title = "Scan to connect",
                    content =
                        Content.Hero(
                            icon = R.drawable.ic_qr_code_scanner,
                            body = DesktopPairingGuide.SCAN_INSTRUCTIONS,
                        ),
                ),
            )
    }
}

data class OnboardingRow(
    @param:DrawableRes val icon: Int,
    val title: String,
    val body: String,
)
