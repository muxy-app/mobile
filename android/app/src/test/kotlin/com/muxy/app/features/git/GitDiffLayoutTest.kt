package com.muxy.app.features.git

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.muxy.app.core.text.Graphemes
import com.muxy.app.models.VcsDiffRow
import com.muxy.app.models.VcsDiffRowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitDiffLayoutTest {
    @Test
    fun oversizedLinesFitComposeConstraintsAcrossDensitiesAndFontScales() {
        for (scale in listOf(0.75f, 1f, 1.5f, 2.75f, 4f)) {
            for (fontScale in listOf(0.85f, 1f, 1.3f, 2f, 3f)) {
                val density = Density(scale, fontScale)
                val viewport = with(density) { Constraints(maxWidth = 400.dp.roundToPx(), maxHeight = 900.dp.roundToPx()) }
                for (length in listOf(40_000, 1_000_000, Int.MAX_VALUE)) {
                    val layout = GitDiffLayout.measure(length, density, viewport)
                    val width = with(density) { layout.width.roundToPx() }
                    val bounded = Constraints(width, width, 0, viewport.maxHeight)
                    assertEquals(width, bounded.maxWidth)
                    if (length >= 1_000_000) assertTrue(layout.requiresWrap)
                    assertTrue(width >= viewport.maxWidth)
                }
            }
        }
        val originalCrash = GitDiffLayout.measure(40_000, Density(1f), Constraints(maxWidth = 400, maxHeight = 900))
        assertTrue(originalCrash.requiresWrap)
    }

    @Test
    fun ordinaryDiffsKeepTheUnwrappedMinimumAndViewportWidth() {
        val density = Density(2f)
        val narrow = GitDiffLayout.measure(20, density, Constraints(maxWidth = 800, maxHeight = 1800))
        assertEquals(760.dp, narrow.width)
        assertFalse(narrow.requiresWrap)
        val wide = GitDiffLayout.measure(20, density, Constraints(maxWidth = 2400, maxHeight = 1800))
        assertEquals(1200.dp, wide.width)
        assertFalse(wide.requiresWrap)
    }

    @Test
    fun fractionalPixelRoundingDoesNotForceOrdinaryLinesToWrap() {
        for (scale in listOf(1f, 1.5f, 2.75f)) {
            val layout = GitDiffLayout.measure(101, Density(scale), Constraints(maxWidth = 800, maxHeight = 1800))
            assertFalse(layout.requiresWrap)
        }
    }

    @Test
    fun wrappedSegmentsPreserveAllTextAndOnlyNumberTheFirstSegment() {
        val text = "🙂e\u0301".repeat(20_000)
        val row = VcsDiffRow(VcsDiffRowKind.CONTEXT, 41, 42, text)
        val rows = wrappedDiffRows(listOf(row))
        assertEquals(text, rows.joinToString("") { it.text })
        assertTrue(rows.size > 1)
        assertEquals(41L, rows.first().oldLineNumber)
        assertEquals(42L, rows.first().newLineNumber)
        assertTrue(rows.drop(1).all { it.oldLineNumber == null && it.newLineNumber == null })
        assertTrue(rows.all { it.kind == row.kind && Graphemes.count(it.text) <= 512 })
        assertTrue(rows.all { it.text.startsWith("🙂") && it.text.endsWith("e\u0301") })
    }

    @Test
    fun shortAndEmptyRowsKeepTheirOrderKindsAndLineNumbers() {
        val rows =
            listOf(
                VcsDiffRow(VcsDiffRowKind.HUNK, text = "@@ -1 +1 @@"),
                VcsDiffRow(VcsDiffRowKind.DELETION, oldLineNumber = 1, text = "old"),
                VcsDiffRow(VcsDiffRowKind.ADDITION, newLineNumber = 1, text = ""),
            )
        assertEquals(rows, wrappedDiffRows(rows))
    }
}
