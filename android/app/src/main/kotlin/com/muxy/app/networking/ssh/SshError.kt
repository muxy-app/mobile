package com.muxy.app.networking.ssh

import net.schmizz.sshj.userauth.UserAuthException

enum class SshError(
    val message: String,
) {
    UNREACHABLE("Couldn't reach the server. Check the host and port."),
    AUTHENTICATION_FAILED("Authentication failed. Check your username and credentials."),
    HOST_KEY_CHANGED("The server's host key has changed. Connection refused to protect against tampering."),
    KEY_PARSE_FAILED("Couldn't read the private key. Check the key and passphrase."),
    MISSING_CREDENTIALS("Missing SSH credentials. Remove this connection and add it again."),
    ;

    companion object {
        fun classify(error: Throwable): SshError {
            val causes = generateSequence(error) { it.cause }.take(16).toList()
            causes.filterIsInstance<SshException>().firstOrNull()?.let { return it.error }
            if (causes.any { it is UserAuthException }) return AUTHENTICATION_FAILED
            return UNREACHABLE
        }
    }
}

class SshException(
    val error: SshError,
) : Exception(error.message)
