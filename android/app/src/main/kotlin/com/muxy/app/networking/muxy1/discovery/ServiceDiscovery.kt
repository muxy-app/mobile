package com.muxy.app.networking.muxy1.discovery

import kotlinx.coroutines.flow.StateFlow

data class DiscoveredService(
    val name: String,
    val host: String,
    val port: Int,
)

interface ServiceDiscovery {
    val services: StateFlow<List<DiscoveredService>>

    fun start()

    fun stop()
}

object NsdMapping {
    const val SERVICE_TYPE = "_muxy._tcp"

    private val PORT_RANGE = 1..65535
    private val unreachableHosts = setOf("localhost", "::1", "127.0.0.1", "0.0.0.0")

    fun service(
        name: String,
        hostAddresses: List<String>,
        port: Int,
    ): DiscoveredService? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null
        if (port !in PORT_RANGE) return null
        val hosts = hostAddresses.mapNotNull(::normalizedHost)
        val host = hosts.firstOrNull(::isIpv4) ?: hosts.firstOrNull() ?: return null
        return DiscoveredService(cleanName, host, port)
    }

    fun normalizedHost(hostName: String?): String? {
        val host = hostName?.trim()?.removeSuffix(".") ?: return null
        if (host.isEmpty() || host.contains('%')) return null
        if (host.lowercase() in unreachableHosts) return null
        return host
    }

    private fun isIpv4(host: String): Boolean = host.split('.').let { parts -> parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 } }
}

fun List<DiscoveredService>.upserting(service: DiscoveredService): List<DiscoveredService> {
    if (none { it.name == service.name }) return this + service
    return map { if (it.name == service.name) service else it }
}

fun List<DiscoveredService>.removing(name: String): List<DiscoveredService> = filterNot { it.name == name }
