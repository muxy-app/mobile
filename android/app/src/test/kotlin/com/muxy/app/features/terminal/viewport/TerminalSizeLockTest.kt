package com.muxy.app.features.terminal.viewport

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalSizeLockTest {
    private var now = 0L
    private val lock = TerminalSizeLock(clock = { now })
    private val portrait = IntSize(1080, 1800)

    private fun lockAt(size: IntSize) {
        assertEquals(SizeLockResult.Pending(80), lock.sample(size, usable = true))
        now += 80
        assertEquals(SizeLockResult.Locked, lock.sample(size, usable = true))
    }

    @Test
    fun aSizeLocksOnceItIsStableForEightyMilliseconds() {
        lockAt(portrait)
        assertEquals(portrait, lock.locked)
    }

    @Test
    fun aSizeThatChangesBeforeTheSecondSampleStartsOver() {
        lock.sample(portrait, usable = true)
        now += 50
        assertEquals(SizeLockResult.Pending(80), lock.sample(IntSize(1080, 1700), usable = true))
        now += 50
        assertEquals(SizeLockResult.Pending(30), lock.sample(IntSize(1080, 1700), usable = true))
        assertNull(lock.locked)
    }

    @Test
    fun anUnusableSizeNeverLocks() {
        assertEquals(SizeLockResult.Unchanged, lock.sample(IntSize(100, 100), usable = false))
        now += 100
        assertEquals(SizeLockResult.Unchanged, lock.sample(IntSize(100, 100), usable = false))
        assertNull(lock.locked)
    }

    @Test
    fun theLockedSizeIsKeptUntilANewSizeSettles() {
        lockAt(portrait)
        assertEquals(SizeLockResult.Pending(80), lock.sample(IntSize(1080, 900), usable = true))
        assertEquals(portrait, lock.locked)
        assertEquals(SizeLockResult.Unchanged, lock.sample(portrait, usable = true))
        now += 200
        assertEquals(portrait, lock.locked)
    }

    @Test
    fun aWidthChangeRelocks() {
        lockAt(portrait)
        lockAt(IntSize(1800, 1000))
        assertEquals(IntSize(1800, 1000), lock.locked)
    }

    @Test
    fun aSettledHeightChangeRelocks() {
        lockAt(portrait)
        lockAt(IntSize(1080, 900))
        assertEquals(IntSize(1080, 900), lock.locked)
    }
}
