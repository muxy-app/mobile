package com.muxy.app

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muxy.app.persistence.preferencesDataStore
import com.muxy.app.persistence.secrets.DeviceCredential
import com.muxy.app.persistence.secrets.EncryptedSecretStore
import com.muxy.app.persistence.secrets.KeystoreSecretCipher
import com.muxy.app.persistence.secrets.SecretStore
import com.muxy.app.persistence.secrets.SecretTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "muxy.secrets.test.${UUID.randomUUID()}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(context.cacheDir, "secrets-test-${UUID.randomUUID()}.preferences_pb")
    private val dataStore = preferencesDataStore("Test secrets", scope) { file }
    private val store: SecretStore = EncryptedSecretStore(dataStore, KeystoreSecretCipher(alias))

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
        keyStore().deleteEntry(alias)
    }

    @Test
    fun roundTripsAValue() =
        runTest {
            store.write("connection.a.token", "secret-token")
            assertEquals("secret-token", store.read("connection.a.token"))
        }

    @Test
    fun storesCiphertextOnly() =
        runTest {
            store.write("connection.a.token", "secret-token")
            val stored =
                dataStore.data
                    .first()
                    .asMap()
                    .values
                    .single() as String
            assertFalse(stored.contains("secret-token"))
        }

    @Test
    fun overwritesAValue() =
        runTest {
            store.write("connection.a.token", "first")
            store.write("connection.a.token", "second")
            assertEquals("second", store.read("connection.a.token"))
        }

    @Test
    fun deletesAValue() =
        runTest {
            store.write("connection.a.token", "secret-token")
            store.delete("connection.a.token")
            assertNull(store.read("connection.a.token"))
        }

    @Test
    fun keepsConnectionsApart() =
        runTest {
            val tokens = SecretTokenStore(store)
            val first = UUID.randomUUID()
            val second = UUID.randomUUID()
            tokens.setCredential(DeviceCredential("device-a", "token-a"), first)
            tokens.setCredential(DeviceCredential("device-b", "token-b"), second)
            assertEquals("token-a", tokens.credential(first)?.token)
            assertEquals("token-b", tokens.credential(second)?.token)
            tokens.deleteSecrets(first)
            assertNull(tokens.credential(first))
            assertEquals("token-b", tokens.credential(second)?.token)
        }

    @Test
    fun aValueUnderADeletedKeyReadsAsMissing() =
        runTest {
            store.write("connection.a.token", "secret-token")
            keyStore().deleteEntry(alias)
            assertNull(store.read("connection.a.token"))
        }

    @Test
    fun aValueMovedToAnotherNameReadsAsMissing() =
        runTest {
            store.write("connection.a.token", "secret-token")
            val sealed =
                dataStore.data
                    .first()
                    .asMap()
                    .values
                    .single() as String
            dataStore.updateData {
                it.toMutablePreferences().apply {
                    set(stringPreferencesKey("connection.b.token"), sealed)
                }
            }
            assertNull(store.read("connection.b.token"))
        }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
