package com.muxy.app.features.terminal.viewport

import androidx.compose.ui.unit.IntSize

sealed interface SizeLockResult {
    data object Locked : SizeLockResult

    data class Pending(
        val delayMillis: Long,
    ) : SizeLockResult

    data object Unchanged : SizeLockResult
}

class TerminalSizeLock(
    private val clock: () -> Long,
    private val settleMillis: Long = SETTLE_MILLIS,
) {
    var locked: IntSize? = null
        private set

    private var candidate: IntSize? = null
    private var candidateSince = 0L

    fun sample(
        size: IntSize,
        usable: Boolean,
    ): SizeLockResult {
        if (size == locked || !usable) {
            candidate = null
            return SizeLockResult.Unchanged
        }
        val now = clock()
        if (size != candidate) {
            candidate = size
            candidateSince = now
            return SizeLockResult.Pending(settleMillis)
        }
        val waited = now - candidateSince
        if (waited < settleMillis) return SizeLockResult.Pending(settleMillis - waited)
        locked = size
        candidate = null
        return SizeLockResult.Locked
    }

    companion object {
        const val SETTLE_MILLIS = 80L
    }
}
