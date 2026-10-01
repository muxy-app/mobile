package com.muxy.app.networking.server

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReconnectSchedule(
    private val initialDelays: List<Duration> = listOf(1.seconds, 2.seconds, 5.seconds),
    private val steadyDelay: Duration = 10.seconds,
    val afterRestart: Duration = 1500.milliseconds,
) {
    var attempt = 0
        private set

    fun nextDelay(): Duration {
        val delay = initialDelays.getOrNull(attempt) ?: steadyDelay
        attempt += 1
        return delay
    }

    fun reset() {
        attempt = 0
    }
}
