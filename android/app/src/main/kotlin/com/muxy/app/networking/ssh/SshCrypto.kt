package com.muxy.app.networking.ssh

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

internal object SshCrypto {
    @Synchronized
    fun initialize() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) is BouncyCastleProvider) return
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }
}
