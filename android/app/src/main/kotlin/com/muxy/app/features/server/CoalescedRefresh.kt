package com.muxy.app.features.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CoalescedRefresh(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var pending: (suspend () -> Unit)? = null

    fun request(action: suspend () -> Unit) {
        pending = action
        if (job?.isActive == true) return
        val drain = scope.launch(start = CoroutineStart.LAZY) { drain() }
        job = drain
        drain.start()
    }

    fun cancel() {
        job?.cancel()
        job = null
        pending = null
    }

    private suspend fun drain() {
        while (true) {
            val action = pending ?: return
            pending = null
            action()
        }
    }
}
