package com.muxy.app.persistence.secrets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.persistence.preferencesDataStore
import com.muxy.app.testing.FailingSecretCipher
import com.muxy.app.testing.XorSecretCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import java.util.UUID

class SecretTokenStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val credential = DeviceCredential("device-1", "secret-token")

    @Test
    fun setThenGetReturnsTheCredential() =
        runTest {
            val store = Fixture(this).tokens()
            val id = UUID.randomUUID()
            store.setCredential(credential, id)
            assertEquals(credential, store.credential(id))
        }

    @Test
    fun getMissingReturnsNothing() =
        runTest {
            assertNull(Fixture(this).tokens().credential(UUID.randomUUID()))
        }

    @Test
    fun setOverwritesAnExistingCredential() =
        runTest {
            val store = Fixture(this).tokens()
            val id = UUID.randomUUID()
            store.setCredential(credential, id)
            store.setCredential(credential.copy(token = "second"), id)
            assertEquals("second", store.credential(id)?.token)
        }

    @Test
    fun deleteRemovesTheCredential() =
        runTest {
            val store = Fixture(this).tokens()
            val id = UUID.randomUUID()
            store.setCredential(credential, id)
            store.deleteSecrets(id)
            assertNull(store.credential(id))
        }

    @Test
    fun deleteMissingDoesNotThrow() =
        runTest {
            Fixture(this).tokens().deleteSecrets(UUID.randomUUID())
        }

    @Test
    fun credentialsAreIsolatedByConnectionId() =
        runTest {
            val store = Fixture(this).tokens()
            val first = UUID.randomUUID()
            val second = UUID.randomUUID()
            store.setCredential(credential.copy(token = "token-a"), first)
            store.setCredential(credential.copy(token = "token-b"), second)
            assertEquals("token-a", store.credential(first)?.token)
            assertEquals("token-b", store.credential(second)?.token)
        }

    @Test
    fun storesOnlyCiphertext() =
        runTest {
            val fixture = Fixture(this)
            fixture.tokens().setCredential(credential, UUID.randomUUID())
            val stored =
                fixture.dataStore.data
                    .first()
                    .asMap()
                    .values
                    .single() as String
            assertFalse(String(Base64.getDecoder().decode(stored)).contains(credential.token))
        }

    @Test
    fun aValueThatNoLongerDecryptsReadsAsMissing() =
        runTest {
            val fixture = Fixture(this)
            val id = UUID.randomUUID()
            fixture.tokens().setCredential(credential, id)
            assertNull(fixture.tokens(FailingSecretCipher).credential(id))
        }

    @Test
    fun aMalformedValueReadsAsMissing() =
        runTest {
            val fixture = Fixture(this)
            val id = UUID.randomUUID()
            fixture.dataStore.edit { it[stringPreferencesKey("connection.${id.uuidString}.token")] = "%%%" }
            assertNull(fixture.tokens().credential(id))
        }

    private inner class Fixture(
        test: TestScope,
    ) {
        private val dispatcher: TestDispatcher = StandardTestDispatcher(test.testScheduler)
        private val scope = CoroutineScope(dispatcher + Job(test.backgroundScope.coroutineContext[Job]))
        private val file = File(folder.newFolder(), "secrets.preferences_pb")

        val dataStore: DataStore<Preferences> = preferencesDataStore("Secrets", scope) { file }

        fun tokens(cipher: SecretCipher = XorSecretCipher): TokenStore =
            SecretTokenStore(EncryptedSecretStore(dataStore, cipher, dispatcher))
    }
}
