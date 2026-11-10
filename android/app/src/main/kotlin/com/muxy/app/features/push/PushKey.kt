package com.muxy.app.features.push

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PushKey private constructor(
    val serverId: String,
    private val bytes: ByteArray,
) {
    val keyId: ByteArray
        get() = MessageDigest.getInstance(SHA_256).digest(IDENTIFIER_LABEL + bytes).copyOf(IDENTIFIER_LENGTH)

    fun collapseId(sessionId: ULong): String = derive("collapse:$sessionId")

    fun threadId(projectId: String): String = derive("thread:$projectId")

    internal fun secretKey(): SecretKeySpec = SecretKeySpec(bytes, AES)

    internal fun encoded(): String = Base64.getEncoder().encodeToString(bytes)

    override fun toString(): String = "PushKey($serverId)"

    private fun derive(label: String): String {
        val mac = Mac.getInstance(HMAC_SHA_256)
        mac.init(SecretKeySpec(bytes, HMAC_SHA_256))
        val code = mac.doFinal(label.toByteArray(Charsets.UTF_8))
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(code)
            .take(DERIVED_LENGTH)
    }

    companion object {
        const val BYTE_COUNT = 32
        internal const val IDENTIFIER_LENGTH = 8
        private const val DERIVED_LENGTH = 16
        private const val SHA_256 = "SHA-256"
        private const val HMAC_SHA_256 = "HmacSHA256"
        private const val AES = "AES"
        private val IDENTIFIER_LABEL = "muxy-push-key-id".toByteArray(Charsets.UTF_8)

        fun of(
            serverId: String,
            bytes: ByteArray,
        ): PushKey? {
            if (serverId.isEmpty() || bytes.size != BYTE_COUNT) return null
            return PushKey(serverId, bytes.copyOf())
        }

        internal fun decode(
            serverId: String,
            encoded: String,
        ): PushKey? {
            val bytes =
                try {
                    Base64.getDecoder().decode(encoded)
                } catch (error: IllegalArgumentException) {
                    return null
                }
            return of(serverId, bytes)
        }
    }
}
