package com.muxy.app.features.push

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

internal class PushPayload private constructor(
    private val bytes: ByteArray,
) {
    val keyId: ByteArray
        get() = bytes.copyOfRange(1, HEADER_LENGTH)

    fun open(key: PushKey): ByteArray? {
        val associatedData = bytes.copyOfRange(0, HEADER_LENGTH) + key.serverId.toByteArray(Charsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key.secretKey(), GCMParameterSpec(TAG_BITS, bytes, HEADER_LENGTH, NONCE_LENGTH))
            cipher.updateAAD(associatedData)
            cipher.doFinal(bytes, CIPHERTEXT_START, bytes.size - CIPHERTEXT_START)
        } catch (error: GeneralSecurityException) {
            null
        }
    }

    companion object {
        private const val VERSION: Byte = 1
        private const val HEADER_LENGTH = 1 + PushKey.IDENTIFIER_LENGTH
        private const val NONCE_LENGTH = 12
        private const val TAG_LENGTH = 16
        private const val TAG_BITS = TAG_LENGTH * 8
        private const val CIPHERTEXT_START = HEADER_LENGTH + NONCE_LENGTH
        private const val MINIMUM_LENGTH = CIPHERTEXT_START + TAG_LENGTH + 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val ALPHABET = Regex("^[A-Za-z0-9_-]*$")

        fun parse(encoded: String): PushPayload? {
            if (!ALPHABET.matches(encoded)) return null
            val bytes =
                try {
                    Base64.getUrlDecoder().decode(encoded)
                } catch (error: IllegalArgumentException) {
                    return null
                }
            if (bytes.size < MINIMUM_LENGTH || bytes[0] != VERSION) return null
            return PushPayload(bytes)
        }
    }
}
