package com.muxy.app.networking.server

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReconnectScheduleTest {
    @Test
    fun backsOffThenRetriesEveryTenSeconds() {
        val schedule = ReconnectSchedule()
        val delays = List(6) { schedule.nextDelay() }
        assertEquals(listOf(1.seconds, 2.seconds, 5.seconds, 10.seconds, 10.seconds, 10.seconds), delays)
    }

    @Test
    fun countsAttempts() {
        val schedule = ReconnectSchedule()
        schedule.nextDelay()
        schedule.nextDelay()
        assertEquals(2, schedule.attempt)
    }

    @Test
    fun resetStartsOverFromOneSecond() {
        val schedule = ReconnectSchedule()
        schedule.nextDelay()
        schedule.nextDelay()
        schedule.reset()
        assertEquals(0, schedule.attempt)
        assertEquals(1.seconds, schedule.nextDelay())
    }

    @Test
    fun restartWaitsASecondAndAHalf() {
        assertEquals(1500.milliseconds, ReconnectSchedule().afterRestart)
    }

    @Test
    fun delaysCanBeShortenedForTests() {
        val schedule = ReconnectSchedule(listOf(1.milliseconds), 2.milliseconds, Duration.ZERO)
        assertEquals(1.milliseconds, schedule.nextDelay())
        assertEquals(2.milliseconds, schedule.nextDelay())
        assertEquals(Duration.ZERO, schedule.afterRestart)
    }
}
