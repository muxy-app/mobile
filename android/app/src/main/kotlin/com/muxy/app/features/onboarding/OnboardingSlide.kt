package com.muxy.app.features.onboarding

import androidx.annotation.DrawableRes
import com.muxy.app.R

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
                    title = "How it works",
                    content =
                        Content.Rows(
                            listOf(
                                OnboardingRow(
                                    icon = R.drawable.ic_wifi,
                                    title = "Same network",
                                    body = "Your phone and desktop talk directly over your local network.",
                                ),
                                OnboardingRow(
                                    icon = R.drawable.ic_toggle_on,
                                    title = "Enable the Mobile server",
                                    body = "On your desktop: Muxy > Settings > Mobile, then toggle the server on.",
                                ),
                                OnboardingRow(
                                    icon = R.drawable.ic_bolt,
                                    title = "Stay in sync",
                                    body = "Open projects, run commands, and review changes in real time.",
                                ),
                            ),
                        ),
                ),
                OnboardingSlide(
                    title = "Pair your desktop",
                    content =
                        Content.Hero(
                            icon = R.drawable.ic_grid_view,
                            body =
                                "Enter your desktop's IP address and the port shown in Muxy's Mobile settings. " +
                                    "Default port is 4865.",
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
