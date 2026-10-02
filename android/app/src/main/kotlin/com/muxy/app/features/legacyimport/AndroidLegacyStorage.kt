package com.muxy.app.features.legacyimport

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import com.muxy.app.core.logging.Log
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class AndroidLegacyStorage(
    private val context: Context,
) : LegacyStorage {
    override var stage: LegacyImportStage
        get() {
            val value = context.getSharedPreferences(IMPORT_PREFERENCES, Context.MODE_PRIVATE).getString("stage", null)
            return LegacyImportStage.entries.firstOrNull { it.name == value } ?: LegacyImportStage.PENDING
        }

        @SuppressLint("UseKtx")
        set(value) {
            check(
                context
                    .getSharedPreferences(IMPORT_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putString("stage", value.name)
                    .commit(),
            )
        }

    override fun value(key: String): String? {
        val file = context.getDatabasePath(DATABASE)
        if (!file.exists()) return null
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.query("catalystLocalStorage", arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0)
            }
        }
    }

    override fun secret(key: String): String? {
        val preferences = context.getSharedPreferences(SECURE_PREFERENCES, Context.MODE_PRIVATE)
        val encoded = preferences.getString("key_v1-$key", null) ?: preferences.getString(key, null) ?: return null
        val envelope = JSONObject(encoded)
        require(envelope.getString("scheme") == "aes")
        require(!envelope.optBoolean("requireAuthentication", false))
        require(envelope.getInt("tlen") == 128)
        val alias = if (envelope.optBoolean("usesKeystoreSuffix", false)) SECURE_ALIAS else OLD_SECURE_ALIAS
        val entry = keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry ?: error("Missing legacy key")
        val iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT)
        require(iv.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, entry.secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(Base64.decode(envelope.getString("ct"), Base64.DEFAULT)).toString(Charsets.UTF_8)
    }

    override fun cleanup(): Boolean {
        val actions =
            listOf<() -> Unit>(
                { if (context.getDatabasePath(DATABASE).exists()) check(context.deleteDatabase(DATABASE)) },
                { check(context.deleteSharedPreferences(SECURE_PREFERENCES)) },
            ) +
                OLD_PREFERENCES.map { name -> { check(context.deleteSharedPreferences(name)) } } +
                listOf(SECURE_ALIAS, OLD_SECURE_ALIAS, "_androidx_security_master_key_").map { alias ->
                    { keyStore().deleteEntry(alias) }
                }
        var succeeded = true
        actions.forEach { action ->
            try {
                action()
            } catch (error: Exception) {
                succeeded = false
                Log.persistence.error("Legacy cleanup failed: ${error.javaClass.simpleName}")
            }
        }
        return succeeded
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        const val DATABASE = "RKStorage"
        const val SECURE_PREFERENCES = "SecureStore"
        const val SECURE_ALIAS = "AES/GCM/NoPadding:key_v1:keystoreUnauthenticated"
        const val OLD_SECURE_ALIAS = "AES/GCM/NoPadding:key_v1"
        const val IMPORT_PREFERENCES = "muxy_legacy_import"
        val OLD_PREFERENCES = listOf("muxy_credentials", "muxy_trial", "muxy_devices", "muxy_demo", "muxy_terminal_prefs")
    }
}
