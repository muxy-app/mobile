package com.muxy.app.features.addconnection

import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.testing.device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedMacLookupTest {
    @Test
    fun findsASavedMacByServiceName() {
        val studio = device(host = "192.168.1.20", serviceName = "Studio")
        assertEquals(studio, listOf(studio).savedMac("Studio", "192.168.1.30", 4865))
    }

    @Test
    fun findsASavedMacByHostAndPortIgnoringCase() {
        val studio = device(host = "Studio.local")
        assertEquals(studio, listOf(studio).savedMac(null, "studio.LOCAL", 4865))
    }

    @Test
    fun prefersTheServiceNameMatch() {
        val byHost = device(host = "studio.local")
        val byService = device(host = "192.168.1.20", serviceName = "Studio")
        assertEquals(byService, listOf(byHost, byService).savedMac("Studio", "studio.local", 4865))
    }

    @Test
    fun ignoresOtherPortsKindsAndTheDemo() {
        val otherPort = device(host = "studio.local", port = 5000)
        val server = device(host = "studio.local").copy(kind = ConnectionKind.SERVER)
        val demo = DemoConnection.connection
        assertNull(listOf(otherPort, server, demo).savedMac(null, "studio.local", 4865))
        assertNull(listOf(demo).savedMac(DemoConnection.NAME, demo.host, demo.port))
    }

    @Test
    fun anAddressMatchDoesNotCrossServiceNames() {
        val studio = device(host = "192.168.1.20", serviceName = "Studio")
        assertNull(listOf(studio).savedMac("Laptop", "192.168.1.20", 4865))
        assertEquals(studio, listOf(studio).savedMac(null, "192.168.1.20", 4865))
    }
}
