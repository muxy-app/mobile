@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.billing

import com.muxy.app.persistence.secrets.SecretStore
import com.muxy.app.testing.InMemorySecretStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TrialStoreTest {
    @Test
    fun firstLaunchSavesTheTimestampWithoutAConnection() =
        runTest {
            val secrets = InMemorySecretStore()
            val store = SecretTrialStore(secrets)
            assertEquals(1000L, store.startIfAbsent(1000))
            assertEquals(mapOf("muxy.trial.startedAt" to "1000"), secrets.values)
        }

    @Test
    fun laterCallsAndNewStoreInstancesKeepTheOriginalTimestamp() =
        runTest {
            val secrets = InMemorySecretStore()
            val store = SecretTrialStore(secrets)
            store.startIfAbsent(1000)
            assertEquals(1000L, store.startIfAbsent(2000))
            assertEquals(1000L, SecretTrialStore(secrets).startIfAbsent(1000 + TRIAL_DURATION_MS))
            assertEquals("1000", secrets.values[SecretTrialStore.STARTED_AT_KEY])
        }

    @Test
    fun malformedNonpositiveAndOverflowedTimestampsAreReplaced() =
        runTest {
            for (value in listOf("", "NaN", "-1", "0", "1e99", "9999999999999999999999")) {
                val secrets = InMemorySecretStore()
                secrets.values[SecretTrialStore.STARTED_AT_KEY] = value
                assertEquals(1000L, SecretTrialStore(secrets).startIfAbsent(1000))
                assertEquals("1000", secrets.values[SecretTrialStore.STARTED_AT_KEY])
            }
        }

    @Test
    fun simultaneousStartsWriteOnlyTheFirstTimestamp() =
        runTest {
            val memory = InMemorySecretStore()
            val allowWrite = CompletableDeferred<Unit>()
            var writes = 0
            val secrets =
                object : SecretStore by memory {
                    override suspend fun write(
                        name: String,
                        value: String,
                    ) {
                        writes += 1
                        allowWrite.await()
                        memory.write(name, value)
                    }
                }
            val store = SecretTrialStore(secrets)
            val first = async { store.startIfAbsent(1000) }
            runCurrent()
            val second = async { store.startIfAbsent(2000) }
            runCurrent()
            assertEquals(1, writes)
            assertFalse(first.isCompleted)
            assertFalse(second.isCompleted)
            allowWrite.complete(Unit)
            assertEquals(1000L, first.await())
            assertEquals(1000L, second.await())
            assertEquals(1, writes)
        }

    @Test
    fun aFailedWriteIsNotReportedAsAStartedTrial() =
        runTest {
            val memory = InMemorySecretStore()
            var fails = true
            val secrets =
                object : SecretStore by memory {
                    override suspend fun write(
                        name: String,
                        value: String,
                    ) {
                        if (fails) throw IOException("Storage unavailable")
                        memory.write(name, value)
                    }
                }
            val store = SecretTrialStore(secrets)
            assertTrue(runCatching { store.startIfAbsent(1000) }.exceptionOrNull() is IOException)
            assertTrue(memory.values.isEmpty())
            fails = false
            assertEquals(2000L, store.startIfAbsent(2000))
        }

    @Test
    fun aReadFailureDoesNotOverwriteTheTimestamp() =
        runTest {
            val memory = InMemorySecretStore()
            memory.values[SecretTrialStore.STARTED_AT_KEY] = "1000"
            val secrets =
                object : SecretStore by memory {
                    override suspend fun read(name: String): String? = throw IOException("Storage unavailable")
                }
            assertTrue(runCatching { SecretTrialStore(secrets).startIfAbsent(2000) }.exceptionOrNull() is IOException)
            assertEquals("1000", memory.values[SecretTrialStore.STARTED_AT_KEY])
        }
}
