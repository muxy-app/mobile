package com.muxy.app.networking.ssh

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.persistence.secrets.ConnectionSecret
import com.muxy.app.persistence.secrets.SecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

class SshHostKeyTrust(
    private val secrets: SecretStore,
) {
    private val mutex = Mutex()

    fun verifier(connectionId: UUID): SshHostKeyVerifier =
        SshHostKeyVerifier { blob ->
            mutex.withLock {
                val fingerprint = fingerprint(blob)
                val name = ConnectionSecret.SSH_HOST_KEY.name(connectionId)
                val pinned = secrets.read(name)
                if (pinned != null && pinned != fingerprint) {
                    Log.ssh.error("Host key mismatch for $connectionId")
                    throw SshException(SshError.HOST_KEY_CHANGED)
                }
                if (pinned != null) return@withLock
                attempt { secrets.write(name, fingerprint) }.getOrElse {
                    Log.ssh.error("Failed to pin an SSH host key: ${it.javaClass.simpleName}")
                    throw SshException(SshError.HOST_KEY_CHANGED)
                }
                Log.ssh.debug("Pinned host key for $connectionId")
            }
        }

    companion object {
        fun fingerprint(blob: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(blob).joinToString("") { "%02x".format(it) }
    }
}
