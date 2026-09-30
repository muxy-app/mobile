package com.muxy.app.persistence.secrets

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.muxy.app.core.logging.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreSecretCipher(
    private val alias: String = DEFAULT_ALIAS,
) : SecretCipher {
    private val keyLock = Any()

    override fun seal(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        check(iv.size == IV_LENGTH) { "Unexpected IV length" }
        return byteArrayOf(FORMAT_VERSION) + iv + ciphertext
    }

    override fun open(
        sealed: ByteArray,
        associatedData: ByteArray,
    ): ByteArray? {
        if (sealed.size <= HEADER_LENGTH || sealed[0] != FORMAT_VERSION) return null
        val key = existingKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, sealed, 1, IV_LENGTH))
            cipher.updateAAD(associatedData)
            cipher.doFinal(sealed, HEADER_LENGTH, sealed.size - HEADER_LENGTH)
        } catch (error: GeneralSecurityException) {
            Log.persistence.error("A secret no longer decrypts", error)
            null
        }
    }

    private fun existingKey(): SecretKey? =
        try {
            keyStore().getKey(alias, null) as? SecretKey
        } catch (error: GeneralSecurityException) {
            Log.persistence.error("The secrets key is unavailable", error)
            null
        }

    private fun createKey(): SecretKey =
        synchronized(keyLock) {
            existingKey() ?: generateKey()
        }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    companion object {
        const val DEFAULT_ALIAS = "muxy.secrets.v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_LENGTH_BITS = 128
        private const val IV_LENGTH = 12
        private const val FORMAT_VERSION: Byte = 1
        private const val HEADER_LENGTH = 1 + IV_LENGTH
    }
}
