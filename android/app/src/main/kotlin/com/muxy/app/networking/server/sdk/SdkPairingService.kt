package com.muxy.app.networking.server.sdk

import com.muxy.app.networking.server.ServerPairingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.muxy_mobile.PairingLink
import uniffi.muxy_mobile.ServerCredential
import uniffi.muxy_mobile.parsePairingLink
import uniffi.muxy_mobile.pair as sdkPair

class SdkPairingService : ServerPairingService {
    override fun parse(link: String): PairingLink = parsePairingLink(link)

    override suspend fun pair(
        link: String,
        deviceName: String,
    ): ServerCredential = withContext(Dispatchers.IO) { sdkPair(link, deviceName) }
}
