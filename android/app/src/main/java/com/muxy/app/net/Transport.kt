package com.muxy.app.net

import com.muxy.app.model.MuxyMessage
import com.muxy.app.model.MuxyResponse
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration

interface Transport {
    val isConnected: Boolean
    val events: SharedFlow<TransportEvent>
    val requiresAuth: Boolean

    fun connect(host: String, port: Int)
    fun disconnect()
    suspend fun send(request: MuxyMessage.Request, timeout: Duration): MuxyResponse
    fun sendFireAndForget(request: MuxyMessage.Request): Boolean
}
