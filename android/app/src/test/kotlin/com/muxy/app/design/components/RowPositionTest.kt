package com.muxy.app.design.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowPositionTest {
    @Test
    fun aLoneRowIsSingle() {
        assertEquals(RowPosition.SINGLE, RowPosition.of(index = 0, count = 1))
    }

    @Test
    fun rowsTakeTheirPlaceInTheSection() {
        assertEquals(
            listOf(RowPosition.FIRST, RowPosition.MIDDLE, RowPosition.MIDDLE, RowPosition.LAST),
            (0 until 4).map { RowPosition.of(index = it, count = 4) },
        )
    }

    @Test
    fun twoRowsAreFirstAndLast() {
        assertEquals(listOf(RowPosition.FIRST, RowPosition.LAST), (0 until 2).map { RowPosition.of(index = it, count = 2) })
    }

    @Test
    fun onlyRowsWithARowBelowHaveAGap() {
        assertTrue(RowPosition.FIRST.hasGapBelow)
        assertTrue(RowPosition.MIDDLE.hasGapBelow)
        assertFalse(RowPosition.LAST.hasGapBelow)
        assertFalse(RowPosition.SINGLE.hasGapBelow)
    }

    @Test
    fun segmentsRoundTheCardCornersOnlyAtTheEnds() {
        val density = Density(1f)
        val size = Size(200f, 100f)
        val outer = SurfaceCorners.card.value
        val inner = SurfaceCorners.segment.value
        assertEquals(listOf(outer, outer, outer, outer), corners(RowPosition.SINGLE, size, density))
        assertEquals(listOf(outer, outer, inner, inner), corners(RowPosition.FIRST, size, density))
        assertEquals(listOf(inner, inner, inner, inner), corners(RowPosition.MIDDLE, size, density))
        assertEquals(listOf(inner, inner, outer, outer), corners(RowPosition.LAST, size, density))
    }

    @Test
    fun segmentShapeMatchesTheRowPosition() {
        assertEquals(RowPosition.FIRST.shape, segmentShape(index = 0, count = 3))
        assertEquals(RowPosition.MIDDLE.shape, segmentShape(index = 1, count = 3))
        assertEquals(RowPosition.LAST.shape, segmentShape(index = 2, count = 3))
        assertEquals(RowPosition.SINGLE.shape, segmentShape(index = 0, count = 1))
    }

    private fun corners(
        position: RowPosition,
        size: Size,
        density: Density,
    ): List<Float> {
        val shape = position.shape as RoundedCornerShape
        return listOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart).map { it.toPx(size, density) }
    }
}
