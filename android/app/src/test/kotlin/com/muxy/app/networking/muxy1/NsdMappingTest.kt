package com.muxy.app.networking.muxy1

import com.muxy.app.networking.muxy1.discovery.DiscoveredService
import com.muxy.app.networking.muxy1.discovery.NsdMapping
import com.muxy.app.networking.muxy1.discovery.removing
import com.muxy.app.networking.muxy1.discovery.upserting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NsdMappingTest {
    @Test
    fun buildsAServiceFromAHostName() {
        assertEquals(DiscoveredService("Studio", "studio.local", 4865), NsdMapping.service("Studio", listOf("studio.local."), 4865))
    }

    @Test
    fun stripsATrailingDot() {
        assertEquals("studio.local", NsdMapping.normalizedHost("studio.local."))
    }

    @Test
    fun keepsAHostWithoutATrailingDot() {
        assertEquals("studio.local", NsdMapping.normalizedHost("studio.local"))
    }

    @Test
    fun rejectsUnreachableHosts() {
        listOf("localhost", "::1", "127.0.0.1", "0.0.0.0", "LOCALHOST").forEach { assertNull(it, NsdMapping.normalizedHost(it)) }
    }

    @Test
    fun rejectsScopedIpv6Addresses() {
        assertNull(NsdMapping.normalizedHost("fe80::1%wlan0"))
    }

    @Test
    fun rejectsAMissingHost() {
        assertNull(NsdMapping.normalizedHost(null))
        assertNull(NsdMapping.service("Studio", emptyList(), 4865))
    }

    @Test
    fun rejectsAnEmptyName() {
        assertNull(NsdMapping.service("   ", listOf("studio.local"), 4865))
    }

    @Test
    fun rejectsALoopbackService() {
        assertNull(NsdMapping.service("Studio", listOf("::1"), 4865))
    }

    @Test
    fun rejectsInvalidPorts() {
        listOf(0, 65536, -1).forEach { assertNull("$it", NsdMapping.service("Studio", listOf("studio.local"), it)) }
    }

    @Test
    fun prefersIpv4() {
        assertEquals("192.168.1.20", NsdMapping.service("Studio", listOf("fd7a::1", "127.0.0.1", "192.168.1.20"), 4865)?.host)
    }

    @Test
    fun fallsBackToTheFirstReachableHost() {
        assertEquals("fd7a::1", NsdMapping.service("Studio", listOf("::1", "fd7a::1"), 4865)?.host)
    }

    @Test
    fun deDuplicatesByServiceName() {
        val first = DiscoveredService("Studio", "192.168.1.20", 4865)
        val moved = DiscoveredService("Studio", "192.168.1.30", 4865)
        val laptop = DiscoveredService("Laptop", "192.168.1.40", 4865)
        assertEquals(listOf(moved, laptop), listOf(first).upserting(laptop).upserting(moved))
        assertEquals(listOf(laptop), listOf(first, laptop).removing("Studio"))
    }
}
