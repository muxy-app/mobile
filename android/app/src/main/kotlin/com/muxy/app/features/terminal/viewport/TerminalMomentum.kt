package com.muxy.app.features.terminal.viewport

import kotlin.math.abs
import kotlin.math.pow

class TerminalMomentum(
    private val startVelocity: Float,
    private val stopVelocity: Float,
) {
    var isActive: Boolean = false
        private set

    private var velocity = 0f
    private var lastNanos = 0L

    fun start(
        velocity: Float,
        nanos: Long,
    ): Boolean {
        if (abs(velocity) <= startVelocity) {
            stop()
            return false
        }
        this.velocity = velocity
        lastNanos = nanos
        isActive = true
        return true
    }

    fun step(nanos: Long): Float {
        val elapsed = ((nanos - lastNanos) / NANOS_PER_SECOND).coerceIn(0.0, MAXIMUM_STEP_SECONDS)
        lastNanos = nanos
        val delta = (velocity * elapsed).toFloat()
        velocity *= DECAY.pow(elapsed / FRAME_SECONDS).toFloat()
        if (abs(velocity) < stopVelocity) isActive = false
        return delta
    }

    fun stop() {
        isActive = false
        velocity = 0f
    }

    companion object {
        const val START_VELOCITY_DP = 100f
        const val STOP_VELOCITY_DP = 30f
        private const val DECAY = 0.96
        private const val FRAME_SECONDS = 1.0 / 60.0
        private const val MAXIMUM_STEP_SECONDS = 1.0 / 30.0
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        fun scaled(density: Float): TerminalMomentum = TerminalMomentum(START_VELOCITY_DP * density, STOP_VELOCITY_DP * density)
    }
}
