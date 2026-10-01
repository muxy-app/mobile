package com.muxy.app.testing

import com.muxy.app.persistence.secrets.SecretCipher

object XorSecretCipher : SecretCipher {
    private const val KEY = 0x5A

    override fun seal(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = byteArrayOf(associatedData.size.toByte()) + plaintext.map { (it.toInt() xor KEY).toByte() }

    override fun open(
        sealed: ByteArray,
        associatedData: ByteArray,
    ): ByteArray? {
        if (sealed.firstOrNull() != associatedData.size.toByte()) return null
        return sealed.drop(1).map { (it.toInt() xor KEY).toByte() }.toByteArray()
    }
}

object FailingSecretCipher : SecretCipher {
    override fun seal(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = error("Not used")

    override fun open(
        sealed: ByteArray,
        associatedData: ByteArray,
    ): ByteArray? = null
}
