package com.muxy.app.features.terminal.viewport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalMomentumTest {
    private val frame = 16_666_667L
    private val momentum = TerminalMomentum.scaled(density = 2f)

    @Test
    fun slowFlingsDoNotStart() {
        assertFalse(momentum.start(velocity = 199f, nanos = 0))
        assertFalse(momentum.isActive)
    }

    @Test
    fun aFlingMovesByItsVelocityAndDecaysEachFrame() {
        assertTrue(momentum.start(velocity = 1000f, nanos = 0))
        assertEquals(16.67f, momentum.step(frame), 0.01f)
        assertEquals(16.0f, momentum.step(frame * 2), 0.01f)
        assertEquals(15.36f, momentum.step(frame * 3), 0.01f)
    }

    @Test
    fun aLongFrameCountsAsAtMostTwoFrames() {
        momentum.start(velocity = 1000f, nanos = 0)
        assertEquals(33.33f, momentum.step(frame * 10), 0.01f)
    }

    @Test
    fun aFlingStopsBelowThirtyDpPerSecond() {
        momentum.start(velocity = -300f, nanos = 0)
        var now = 0L
        var frames = 0
        while (momentum.isActive) {
            now += frame
            assertTrue(momentum.step(now) < 0f)
            frames += 1
        }
        assertEquals(40, frames)
    }

    @Test
    fun stoppingEndsTheFling() {
        momentum.start(velocity = 1000f, nanos = 0)
        momentum.stop()
        assertFalse(momentum.isActive)
    }
}
