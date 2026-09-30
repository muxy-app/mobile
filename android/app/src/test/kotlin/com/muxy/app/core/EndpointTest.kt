package com.muxy.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointTest {
    @Test
    fun buildsWebSocketUrlForHostname() {
        assertEquals("ws://studio.tailnet.ts.net:4865", Endpoint("studio.tailnet.ts.net", 4865).webSocketUrl)
    }

    @Test
    fun buildsWebSocketUrlForTailscaleIpv4() {
        assertEquals("ws://100.64.0.1:4865", Endpoint("100.64.0.1", 4865).webSocketUrl)
    }

    @Test
    fun wrapsIpv6HostForWebSocketUrl() {
        assertEquals("ws://[fd7a:115c:a1e0::1]:4865", Endpoint("fd7a:115c:a1e0::1", 4865).webSocketUrl)
    }

    @Test
    fun keepsBracketedIpv6HostForWebSocketUrl() {
        assertEquals("ws://[fd7a:115c:a1e0::1]:4865", Endpoint("[fd7a:115c:a1e0::1]", 4865).webSocketUrl)
    }

    @Test
    fun hasNoUrlForABlankHost() {
        assertNull(Endpoint("  ", 4865).webSocketUrl)
    }
}
