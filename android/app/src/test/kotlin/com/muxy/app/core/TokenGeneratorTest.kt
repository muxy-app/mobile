package com.muxy.app.core

import com.muxy.app.core.security.TokenGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class TokenGeneratorTest {
    @Test
    fun generatesThirtyTwoRandomBytesAsUnpaddedBase64Url() {
        val token = TokenGenerator().generate()
        assertEquals(43, token.length)
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(32, Base64.getUrlDecoder().decode(token).size)
    }

    @Test
    fun generatesDifferentTokens() {
        val generator = TokenGenerator()
        assertNotEquals(generator.generate(), generator.generate())
    }
}
