package com.muxy.app.persistence.credentials

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.muxy.app.persistence.preferencesDataStore
import com.muxy.app.persistence.secrets.EncryptedSecretStore
import com.muxy.app.persistence.secrets.SecretCipher
import com.muxy.app.testing.FailingSecretCipher
import com.muxy.app.testing.InMemorySecretStore
import com.muxy.app.testing.XorSecretCipher
import com.muxy.app.testing.serverCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uniffi.muxy_mobile.ServerCredential
import java.io.File
import java.util.Base64

class SecretCredentialStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun assertSameCredential(
        expected: ServerCredential,
        actual: ServerCredential?,
    ) {
        assertEquals(expected.serverId, actual?.serverId)
        assertEquals(expected.serverName, actual?.serverName)
        assertEquals(expected.hosts, actual?.hosts)
        assertEquals(expected.port, actual?.port)
        assertEquals(expected.fingerprint.toList(), actual?.fingerprint?.toList())
        assertEquals(expected.deviceId, actual?.deviceId)
        assertEquals(expected.token.toList(), actual?.token?.toList())
    }

    @Test
    fun savesAndReadsEveryField() =
        runTest {
            val store = Fixture(this).credentials()
            store.save(serverCredential())
            assertSameCredential(serverCredential(), store.credential("server-1"))
        }

    @Test
    fun pairingTheSameComputerAgainReplacesItsCredential() =
        runTest {
            val store = Fixture(this).credentials()
            store.save(serverCredential(name = "Old Name"))
            store.save(serverCredential(name = "New Name"))
            assertEquals("New Name", store.credential("server-1")?.serverName)
        }

    @Test
    fun eachComputerKeepsItsOwnCredential() =
        runTest {
            val store = Fixture(this).credentials()
            store.save(serverCredential(id = "a", name = "Laptop"))
            store.save(serverCredential(id = "b", name = "Workstation"))
            assertEquals("Laptop", store.credential("a")?.serverName)
            assertEquals("Workstation", store.credential("b")?.serverName)
        }

    @Test
    fun deleteForgetsTheComputer() =
        runTest {
            val store = Fixture(this).credentials()
            store.save(serverCredential())
            store.delete("server-1")
            assertNull(store.credential("server-1"))
        }

    @Test
    fun deletingAnUnknownComputerDoesNotThrow() =
        runTest {
            Fixture(this).credentials().delete("missing")
        }

    @Test
    fun theStoredValueIsSealed() =
        runTest {
            val fixture = Fixture(this)
            fixture.credentials().save(serverCredential())
            val stored = fixture.dataStore.data.first()[stringPreferencesKey("server.server-1.credential")]!!
            assertFalse(String(Base64.getDecoder().decode(stored)).contains("Studio"))
        }

    @Test
    fun aValueThatNoLongerDecryptsReadsAsMissing() =
        runTest {
            val fixture = Fixture(this)
            fixture.credentials().save(serverCredential())
            assertNull(fixture.credentials(FailingSecretCipher).credential("server-1"))
        }

    @Test
    fun malformedJsonReadsAsMissing() =
        runTest {
            val secrets = InMemorySecretStore()
            secrets.values["server.server-1.credential"] = "{\"serverId\":"
            assertNull(SecretCredentialStore(secrets).credential("server-1"))
        }

    @Test
    fun anOutOfRangePortReadsAsMissing() =
        runTest {
            val secrets = InMemorySecretStore()
            val store = SecretCredentialStore(secrets)
            store.save(serverCredential())
            secrets.values["server.server-1.credential"] = secrets.values.getValue("server.server-1.credential").replace("7419", "70000")
            assertNull(store.credential("server-1"))
        }

    @Test
    fun storedCredentialRoundTripsThroughJsonWithBase64Bytes() {
        val json = Json.encodeToString(StoredCredential.serializer(), StoredCredential.from(serverCredential()))
        val fields = Json.parseToJsonElement(json).jsonObject
        assertEquals(7419, fields.getValue("port").jsonPrimitive.int)
        assertEquals(Base64.getEncoder().encodeToString(ByteArray(32) { 9 }), fields.getValue("token").jsonPrimitive.content)
        assertSameCredential(serverCredential(), Json.decodeFromString(StoredCredential.serializer(), json).credential)
    }

    private inner class Fixture(
        test: TestScope,
    ) {
        private val dispatcher: TestDispatcher = StandardTestDispatcher(test.testScheduler)
        private val scope = CoroutineScope(dispatcher + Job(test.backgroundScope.coroutineContext[Job]))

        val dataStore: DataStore<Preferences> =
            preferencesDataStore("Secrets", scope) { File(folder.newFolder(), "secrets.preferences_pb") }

        fun credentials(cipher: SecretCipher = XorSecretCipher): CredentialStore =
            SecretCredentialStore(EncryptedSecretStore(dataStore, cipher, dispatcher))
    }
}
