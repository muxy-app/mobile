package com.muxy.app.core.security

import java.security.SecureRandom
import java.util.Base64

fun interface TokenGenerating {
    fun generate(): String
}

class TokenGenerator(
    private val byteCount: Int = DEFAULT_BYTE_COUNT,
    private val random: SecureRandom = SecureRandom(),
) : TokenGenerating {
    override fun generate(): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val DEFAULT_BYTE_COUNT = 32
    }
}
