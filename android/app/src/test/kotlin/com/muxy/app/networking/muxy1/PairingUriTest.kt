package com.muxy.app.networking.muxy1

import com.muxy.app.core.Endpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PairingUriTest {
    @Test
    fun parsesAFullUri() {
        assertEquals(
            PairingUri("studio.local", 4865, "Studio", "My Mac"),
            parsed("muxy://pair?host=studio.local&port=4865&service=Studio&label=My%20Mac"),
        )
    }

    @Test
    fun defaultsThePortWhenAbsent() {
        assertEquals(Endpoint.DEFAULT_PORT, parsed("muxy://pair?host=studio.local&service=Studio").port)
    }

    @Test
    fun optionalFieldsAreMissingWhenAbsent() {
        val uri = parsed("muxy://pair?host=studio.local")
        assertNull(uri.serviceName)
        assertNull(uri.label)
    }

    @Test
    fun keepsPlusSignsLikeUrlComponents() {
        assertEquals("Mac+Pro", parsed("muxy://pair?host=studio.local&label=Mac+Pro").label)
    }

    @Test
    fun rejectsInvalidPorts() {
        listOf("0", "70000", "abc").forEach { port ->
            assertEquals(port, rejected(PairingUriError.INVALID_PORT), PairingUri.parse("muxy://pair?host=studio.local&port=$port"))
        }
    }

    @Test
    fun rejectsOtherSchemesAndHosts() {
        listOf("https://pair?host=studio.local", "muxy://connect?host=studio.local", "ssh://pair?host=studio.local").forEach {
            assertEquals(it, rejected(PairingUriError.NOT_MUXY_SCHEME), PairingUri.parse(it))
        }
    }

    @Test
    fun rejectsAMissingHost() {
        assertEquals(rejected(PairingUriError.MISSING_HOST), PairingUri.parse("muxy://pair?service=Studio"))
    }

    @Test
    fun rejectsAnEmptyHost() {
        assertEquals(rejected(PairingUriError.MISSING_HOST), PairingUri.parse("muxy://pair?host="))
    }

    @Test
    fun rejectsMalformedText() {
        assertEquals(rejected(PairingUriError.MALFORMED), PairingUri.parse("muxy://pair?host=studio local"))
        assertEquals(rejected(PairingUriError.MALFORMED), PairingUri.parse("muxy://pair?host=%zz"))
    }

    @Test
    fun neverExtractsAToken() {
        val uri = parsed("muxy://pair?host=studio.local&token=secret&port=4865")
        assertEquals(PairingUri("studio.local", 4865, null, null), uri)
        assertFalse(uri.toString().contains("secret"))
    }

    private fun parsed(text: String): PairingUri = (PairingUri.parse(text) as PairingUriParse.Parsed).uri

    private fun rejected(error: PairingUriError) = PairingUriParse.Rejected(error)
}
