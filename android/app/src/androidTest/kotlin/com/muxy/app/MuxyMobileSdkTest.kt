package com.muxy.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.muxy_mobile.MobileException
import uniffi.muxy_mobile.parsePairingLink

@RunWith(AndroidJUnit4::class)
class MuxyMobileSdkTest {
    private val pairingLink =
        "muxy://pair?v=1&h=192.168.1.20&h=studio.local&p=7419&f=${"07".repeat(32)}&s=${"09".repeat(16)}"

    @Test
    fun parsesTheHostsAndPortOfAPairingLink() {
        val link = parsePairingLink(pairingLink)
        assertEquals(listOf("192.168.1.20", "studio.local"), link.hosts)
        assertEquals(7419.toUShort(), link.port)
    }

    @Test
    fun rejectsTextThatIsNotAPairingLink() {
        assertThrows(MobileException.InvalidLink::class.java) { parsePairingLink("https://example.com") }
    }
}
