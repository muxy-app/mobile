package com.muxy.app.features.navigation

import android.content.Intent
import java.net.URI
import java.net.URISyntaxException

object DeepLink {
    private const val SCHEME = "muxy"
    private const val PAIR_HOST = "pair"

    fun linkToOpen(
        action: String?,
        flags: Int,
        data: String?,
    ): String? {
        if (action != Intent.ACTION_VIEW) return null
        if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return data
    }

    fun isPairingLink(text: String): Boolean {
        val uri =
            try {
                URI(text)
            } catch (error: URISyntaxException) {
                return false
            }
        return uri.scheme == SCHEME && uri.host == PAIR_HOST
    }
}
