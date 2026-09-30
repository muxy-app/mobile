package com.muxy.app.persistence.secrets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Base64

class EncryptedSecretStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecretStore {
    override suspend fun read(name: String): String? {
        val encoded =
            try {
                dataStore.data.first()[stringPreferencesKey(name)]
            } catch (error: IOException) {
                Log.persistence.error("Reading a secret failed", error)
                null
            } ?: return null
        val sealed =
            try {
                Base64.getDecoder().decode(encoded)
            } catch (error: IllegalArgumentException) {
                Log.persistence.error("A stored secret is malformed", error)
                return null
            }
        return withContext(dispatcher) { cipher.open(sealed, name.toByteArray()) }?.toString(Charsets.UTF_8)
    }

    override suspend fun write(
        name: String,
        value: String,
    ) {
        val sealed = withContext(dispatcher) { cipher.seal(value.toByteArray(), name.toByteArray()) }
        val encoded = Base64.getEncoder().encodeToString(sealed)
        dataStore.edit { it[stringPreferencesKey(name)] = encoded }
    }

    override suspend fun delete(name: String) {
        dataStore.edit { it.remove(stringPreferencesKey(name)) }
    }
}
