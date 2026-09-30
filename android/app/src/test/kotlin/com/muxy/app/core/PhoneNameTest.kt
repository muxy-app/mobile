package com.muxy.app.core

import com.muxy.app.core.device.PhoneName
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNameTest {
    @Test
    fun prefersTheDeviceName() {
        assertEquals("Saeed's Pixel", PhoneName.resolve(" Saeed's Pixel ", "Pixel 10"))
    }

    @Test
    fun fallsBackToTheModel() {
        assertEquals("Pixel 10", PhoneName.resolve("  ", "Pixel 10"))
        assertEquals("Pixel 10", PhoneName.resolve(null, "Pixel 10"))
    }

    @Test
    fun fallsBackToAndroidWithoutEither() {
        assertEquals(PhoneName.FALLBACK, PhoneName.resolve(null, " "))
    }
}
