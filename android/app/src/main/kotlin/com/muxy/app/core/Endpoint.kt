package com.muxy.app.core

data class Endpoint(
    val host: String,
    val port: Int,
) {
    val webSocketUrl: String?
        get() {
            val trimmedHost = host.trim()
            if (trimmedHost.isEmpty()) return null
            return "ws://${bracketed(trimmedHost)}:$port"
        }

    private fun bracketed(host: String): String {
        if (!host.contains(':')) return host
        if (host.startsWith('[')) return host
        return "[$host]"
    }

    companion object {
        const val DEFAULT_PORT = 4865
    }
}
