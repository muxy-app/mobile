package com.muxy.app.networking.muxy1

import com.muxy.app.core.Endpoint
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

enum class PairingUriError {
    NOT_MUXY_SCHEME,
    MISSING_HOST,
    INVALID_PORT,
    MALFORMED,
}

sealed interface PairingUriParse {
    data class Parsed(
        val uri: PairingUri,
    ) : PairingUriParse

    data class Rejected(
        val error: PairingUriError,
    ) : PairingUriParse
}

data class PairingUri(
    val host: String,
    val port: Int,
    val serviceName: String?,
    val label: String?,
) {
    companion object {
        private const val SCHEME = "muxy"
        private const val PAIR_HOST = "pair"
        private val PORT_RANGE = 1..65535

        fun parse(text: String): PairingUriParse {
            val uri =
                try {
                    URI(text.trim())
                } catch (error: URISyntaxException) {
                    return PairingUriParse.Rejected(PairingUriError.MALFORMED)
                }
            if (uri.scheme != SCHEME || uri.host != PAIR_HOST) return PairingUriParse.Rejected(PairingUriError.NOT_MUXY_SCHEME)
            val items = queryItems(uri.rawQuery) ?: return PairingUriParse.Rejected(PairingUriError.MALFORMED)
            val host = items.value("host") ?: return PairingUriParse.Rejected(PairingUriError.MISSING_HOST)
            val port = port(items) ?: return PairingUriParse.Rejected(PairingUriError.INVALID_PORT)
            return PairingUriParse.Parsed(PairingUri(host, port, items.value("service"), items.value("label")))
        }

        private fun port(items: List<Pair<String, String>>): Int? {
            val raw = items.value("port") ?: return Endpoint.DEFAULT_PORT
            return raw.toIntOrNull()?.takeIf { it in PORT_RANGE }
        }

        private fun List<Pair<String, String>>.value(name: String): String? =
            firstOrNull { it.first == name }
                ?.second
                ?.trim()
                ?.takeIf(String::isNotEmpty)

        private fun queryItems(rawQuery: String?): List<Pair<String, String>>? {
            if (rawQuery.isNullOrEmpty()) return emptyList()
            return try {
                rawQuery.split('&').filter(String::isNotEmpty).map { item ->
                    val name = item.substringBefore('=')
                    val value = if (item.contains('=')) item.substringAfter('=') else ""
                    decoded(name) to decoded(value)
                }
            } catch (error: IllegalArgumentException) {
                null
            }
        }

        private fun decoded(component: String): String = URLDecoder.decode(component.replace("+", "%2B"), Charsets.UTF_8.name())
    }
}
