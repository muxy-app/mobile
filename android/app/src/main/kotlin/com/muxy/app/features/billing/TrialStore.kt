package com.muxy.app.features.billing

import com.muxy.app.persistence.secrets.SecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TrialStore {
    suspend fun startIfAbsent(now: Long): Long
}

class SecretTrialStore(
    private val secrets: SecretStore,
) : TrialStore {
    private val mutex = Mutex()

    override suspend fun startIfAbsent(now: Long): Long =
        mutex.withLock {
            val startedAt = secrets.read(STARTED_AT_KEY)?.toLongOrNull()?.takeIf { it > 0 }
            if (startedAt != null) return@withLock startedAt
            secrets.write(STARTED_AT_KEY, now.toString())
            now
        }

    companion object {
        const val STARTED_AT_KEY = "muxy.trial.startedAt"
    }
}
