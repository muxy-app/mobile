package com.muxy.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muxy.app.app.MuxyApplication
import com.muxy.app.features.billing.SecretTrialStore
import com.muxy.app.features.legacyimport.AndroidLegacyStorage
import com.muxy.app.features.legacyimport.LegacyImportStage
import com.muxy.app.features.legacyimport.LegacyImporter
import com.muxy.app.persistence.connections.DataStoreConnectionStore
import com.muxy.app.persistence.preferencesDataStore
import com.muxy.app.persistence.secrets.DeviceCredential
import com.muxy.app.persistence.secrets.EncryptedSecretStore
import com.muxy.app.persistence.secrets.KeystoreSecretCipher
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.persistence.settings.DataStoreSettingsStore
import com.muxy.app.persistence.workspaces.DataStoreWorkspaceSelectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
class LegacyImportTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "legacy-test-${UUID.randomUUID()}"
    private val directory = File(target.cacheDir, namespace)
    private val context =
        object : ContextWrapper(target) {
            override fun getDatabasePath(name: String): File = File(directory, name)

            override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))

            override fun getSharedPreferences(
                name: String,
                mode: Int,
            ): SharedPreferences = super.getSharedPreferences("$namespace-$name", mode)

            override fun deleteSharedPreferences(name: String): Boolean = super.deleteSharedPreferences("$namespace-$name")
        }
    private val aliases = listOf(AndroidLegacyStorage.SECURE_ALIAS, AndroidLegacyStorage.OLD_SECURE_ALIAS, "_androidx_security_master_key_")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storage = AndroidLegacyStorage(context)
    private var ownsLegacyKeys = false

    @Before
    fun setUp() {
        runBlocking {
            withTimeout(10_000) { (target.applicationContext as MuxyApplication).container.ready.first { it } }
        }
        aliases.forEach { assertFalse("Use a disposable emulator without legacy credentials", keyStore().containsAlias(it)) }
        ownsLegacyKeys = true
        check(directory.mkdirs())
    }

    @After
    fun tearDown() {
        scope.cancel()
        if (ownsLegacyKeys) storage.cleanup()
        context.deleteSharedPreferences(AndroidLegacyStorage.IMPORT_PREFERENCES)
        keyStore().deleteEntry(namespace)
        directory.deleteRecursively()
    }

    @Test
    fun readsSQLiteFixtureWithoutCreatingAMissingDatabase() {
        assertNull(storage.value("missing"))
        assertFalse(context.getDatabasePath(AndroidLegacyStorage.DATABASE).exists())
        writeRecord("muxy.settings.v1", "{\"state\":{\"demoMode\":true},\"version\":0}")
        assertEquals("{\"state\":{\"demoMode\":true},\"version\":0}", storage.value("muxy.settings.v1"))
        assertNull(storage.value("' OR 1=1 --"))
    }

    @Test
    fun decryptsExpoSecureStoreEnvelopeWithTheOriginalAlias() {
        writeSecret("muxy.installToken", "token-π", AndroidLegacyStorage.SECURE_ALIAS)
        assertEquals("token-π", storage.secret("muxy.installToken"))
        assertNull(storage.secret("missing"))
    }

    @Test
    fun decryptsLegacyUnsuffixedAliasAndPreferenceKey() {
        writeSecret("muxy.installToken", "old-token", AndroidLegacyStorage.OLD_SECURE_ALIAS, prefixed = false)
        assertEquals("old-token", storage.secret("muxy.installToken"))
    }

    @Test
    fun missingKeystoreKeyIsNotSilentlyReplaced() {
        writeSecret("muxy.installToken", "token", AndroidLegacyStorage.SECURE_ALIAS)
        keyStore().deleteEntry(AndroidLegacyStorage.SECURE_ALIAS)
        assertThrows(Exception::class.java) { storage.secret("muxy.installToken") }
        assertFalse(keyStore().containsAlias(AndroidLegacyStorage.SECURE_ALIAS))
    }

    @Test
    fun tamperedCiphertextAndUnsupportedParametersAreRejected() {
        writeSecret("muxy.installToken", "token", AndroidLegacyStorage.SECURE_ALIAS)
        val preferences = context.getSharedPreferences(AndroidLegacyStorage.SECURE_PREFERENCES, Context.MODE_PRIVATE)
        val original = requireNotNull(preferences.getString("key_v1-muxy.installToken", null))
        val mutations =
            listOf(
                "ct" to Base64.encodeToString(ByteArray(32), Base64.NO_WRAP),
                "iv" to "AA==",
                "tlen" to 96,
                "scheme" to "hybrid",
                "requireAuthentication" to true,
            )
        mutations.forEach { (key, value) ->
            preferences.edit(commit = true) { putString("key_v1-muxy.installToken", JSONObject(original).put(key, value).toString()) }
            assertThrows(Exception::class.java) { storage.secret("muxy.installToken") }
        }
    }

    @Test
    fun cleanupDeletesAllLegacyStoresAndKeysButPreservesTheMarkerAndNativeKey() {
        writeRecord("value", "fixture")
        writeSecret("muxy.installToken", "token", AndroidLegacyStorage.SECURE_ALIAS)
        aliases.drop(1).forEach(::generateKey)
        generateKey(namespace)
        AndroidLegacyStorage.OLD_PREFERENCES.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) { putString("old", "value") }
        }
        storage.stage = LegacyImportStage.IMPORTED
        assertTrue(storage.cleanup())
        assertTrue(storage.cleanup())
        assertFalse(context.getDatabasePath(AndroidLegacyStorage.DATABASE).exists())
        (AndroidLegacyStorage.OLD_PREFERENCES + AndroidLegacyStorage.SECURE_PREFERENCES).forEach { name ->
            assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())
        }
        aliases.forEach { assertFalse(keyStore().containsAlias(it)) }
        assertTrue(keyStore().containsAlias(namespace))
        assertEquals(LegacyImportStage.IMPORTED, AndroidLegacyStorage(context).stage)
    }

    @Test
    fun importPersistsNativeDataAndNeverRunsAgainWithANewStorageInstance() =
        runBlocking {
            val connectionId = UUID.fromString("b1234567-89ab-4cde-8fab-0123456789ab")
            val workspaceId = UUID.fromString("c1234567-89ab-4cde-8fab-0123456789ab")
            val installId = "a1234567-89ab-4cde-8fab-0123456789ab"
            writeRecord(
                "muxy.devices.v1",
                """{"state":{"installDeviceID":"$installId","devices":[{"id":"$connectionId","label":"Mac","host":"mac.local","port":4865}]},"version":0}""",
            )
            writeRecord("muxy.settings.v1", """{"state":{"useNerdFont":false,"hasOnboarded":true},"version":0}""")
            writeRecord("muxy.projects.v1", """{"state":{"selectedWorkspaceIDs":{"$connectionId":"$workspaceId"}},"version":0}""")
            writeSecret("muxy.installToken", "approved-token", AndroidLegacyStorage.SECURE_ALIAS)
            writeSecret("muxy.trial.startedAt", "1700000000000", AndroidLegacyStorage.SECURE_ALIAS)
            val dataStore = preferencesDataStore("Import test", scope) { File(directory, "native.preferences_pb") }
            val secretDataStore = preferencesDataStore("Import secrets", scope) { File(directory, "secrets.preferences_pb") }
            val connections = DataStoreConnectionStore(dataStore, scope)
            val settings = DataStoreSettingsStore(dataStore, scope)
            val workspaces = DataStoreWorkspaceSelectionStore(dataStore)
            val secrets = EncryptedSecretStore(secretDataStore, KeystoreSecretCipher(namespace))
            val tokens = SecretTokenStore(secrets)
            val trials = SecretTrialStore(secrets)
            withTimeout(10_000) { LegacyImporter(storage, connections, tokens, settings, workspaces, trials).run() }
            assertEquals("Mac", connections.load().single().name)
            assertEquals(DeviceCredential(installId, "approved-token"), tokens.credential(connectionId))
            assertEquals(workspaceId, workspaces.load(connectionId))
            assertEquals(1700000000000L, trials.startIfAbsent(1800000000000))
            assertFalse(requireNotNull(settings.settings.value).useNerdFont)
            assertTrue(requireNotNull(settings.settings.value).hasCompletedOnboarding)
            assertEquals(LegacyImportStage.COMPLETE, storage.stage)
            assertFalse(context.getDatabasePath(AndroidLegacyStorage.DATABASE).exists())
            connections.delete(connectionId)
            writeRecord("muxy.devices.v1", "broken")
            withTimeout(10_000) { LegacyImporter(AndroidLegacyStorage(context), connections, tokens, settings, workspaces, trials).run() }
            assertTrue(connections.load().isEmpty())
            assertEquals("broken", storage.value("muxy.devices.v1"))
        }

    private fun writeRecord(
        key: String,
        value: String,
    ) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(AndroidLegacyStorage.DATABASE), null).use { database ->
            database.execSQL("CREATE TABLE IF NOT EXISTS catalystLocalStorage (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            database.execSQL("INSERT OR REPLACE INTO catalystLocalStorage (key, value) VALUES (?, ?)", arrayOf(key, value))
        }
    }

    private fun writeSecret(
        name: String,
        value: String,
        alias: String,
        prefixed: Boolean = true,
    ) {
        val key = (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val envelope =
            JSONObject()
                .put("scheme", "aes")
                .put("ct", Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
                .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .put("tlen", 128)
                .put("usesKeystoreSuffix", alias == AndroidLegacyStorage.SECURE_ALIAS)
                .put("requireAuthentication", false)
        context.getSharedPreferences(AndroidLegacyStorage.SECURE_PREFERENCES, Context.MODE_PRIVATE).edit(commit = true) {
            putString(if (prefixed) "key_v1-$name" else name, envelope.toString())
        }
    }

    private fun generateKey(alias: String): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec
                    .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
