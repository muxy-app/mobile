package com.muxy.app.features.navigation

import java.net.URI
import java.net.URISyntaxException

object DeepLink {
    private const val SCHEME = "muxy"
    private const val PAIR_HOST = "pair"

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
