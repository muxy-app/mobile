package com.muxy.app.networking.server.sdk

import com.muxy.app.core.logging.Log
import com.muxy.app.networking.server.ServerFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SdkLanes(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val requests = dispatcher.limitedParallelism(1, "muxy.sdk.requests")
    private val input = CoroutineScope(SupervisorJob() + dispatcher.limitedParallelism(1, "muxy.sdk.input"))

    suspend fun <T> request(work: () -> T): T = withContext(requests) { work() }

    fun send(work: () -> Unit) {
        input.launch {
            try {
                work()
            } catch (error: Exception) {
                Log.terminal.error("Terminal input failed: ${ServerFailure.from(error)}")
            }
        }
    }

    fun close() {
        input.cancel()
    }
}
