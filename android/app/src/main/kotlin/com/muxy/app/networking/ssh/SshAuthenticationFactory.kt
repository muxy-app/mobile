package com.muxy.app.networking.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordUtils

internal object SshAuthenticationFactory {
    fun privateKey(
        client: SSHClient,
        credentials: SshCredentials,
    ): KeyProvider {
        val password = credentials.passphrase?.toCharArray()
        try {
            val finder = password?.let(PasswordUtils::createOneOff)
            return client.loadKeys(credentials.secret, null, finder).also {
                it.private
                it.public
            }
        } catch (_: Exception) {
            throw SshException(SshError.KEY_PARSE_FAILED)
        } finally {
            password?.fill('\u0000')
        }
    }
}
