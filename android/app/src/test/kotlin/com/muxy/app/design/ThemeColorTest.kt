package com.muxy.app.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ThemeColorTest {
    @Test
    fun luminanceSpansBlackToWhite() {
        assertEquals(0.0, ThemeColor.BLACK.luminance, 0.0)
        assertEquals(1.0, ThemeColor.WHITE.luminance, 1e-12)
    }

    @Test
    fun luminanceLinearizesSrgbChannels() {
        assertEquals(0.2158605, ThemeColor(0x808080).luminance, 1e-7)
        assertEquals(0.2126, ThemeColor(0xFF0000).luminance, 1e-12)
        assertEquals(0.7152, ThemeColor(0x00FF00).luminance, 1e-12)
        assertEquals(0.0722, ThemeColor(0x0000FF).luminance, 1e-12)
    }

    @Test
    fun lightnessIsCieLStar() {
        assertEquals(0.0, ThemeColor.BLACK.lightness, 0.0)
        assertEquals(100.0, ThemeColor.WHITE.lightness, 1e-9)
        assertEquals(53.585, ThemeColor(0x808080).lightness, 1e-3)
    }

    @Test
    fun contrastIsSymmetricAndSpansOneToTwentyOne() {
        assertEquals(21.0, ThemeColor.WHITE.contrast(ThemeColor.BLACK), 1e-9)
        assertEquals(ThemeColor.WHITE.contrast(ThemeColor.BLACK), ThemeColor.BLACK.contrast(ThemeColor.WHITE), 0.0)
        assertEquals(1.0, ThemeColor(0x808080).contrast(ThemeColor(0x808080)), 0.0)
    }

    @Test
    fun mixingQuantizesToEightBitChannels() {
        assertEquals(ThemeColor(0x808080), ThemeColor.BLACK.mixed(ThemeColor.WHITE, 0.5))
        assertEquals(ThemeColor(0x808080), ThemeColor.WHITE.mixed(ThemeColor.BLACK, 0.5))
        assertEquals(ThemeColor(0x1A1A1A), ThemeColor.BLACK.mixed(ThemeColor.WHITE, 0.1))
    }

    @Test
    fun mixingByZeroOrOneKeepsTheEndpoints() {
        val color = ThemeColor(0x19171F)
        val target = ThemeColor(0xC9C2D9)
        assertEquals(color, color.mixed(target, 0.0))
        assertEquals(target, color.mixed(target, 1.0))
    }

    @Test
    fun shiftingLightnessMovesAtLeastTheDelta() {
        val color = ThemeColor(0x19171F)
        val shifted = color.shiftingLightness(6.0, ThemeColor.WHITE)
        assertTrue(abs(shifted.lightness - color.lightness) >= 6.0)
        assertNotEquals(ThemeColor.WHITE, shifted)
    }

    @Test
    fun shiftingLightnessFallsBackToTheTargetWhenTheDeltaIsOutOfReach() {
        assertEquals(ThemeColor.WHITE, ThemeColor.BLACK.shiftingLightness(200.0, ThemeColor.WHITE))
    }

    @Test
    fun ensuringContrastKeepsAColorThatAlreadyPasses() {
        assertEquals(ThemeColor.WHITE, ThemeColor.WHITE.ensuringContrast(4.5, listOf(ThemeColor.BLACK), listOf(ThemeColor.BLACK)))
    }

    @Test
    fun ensuringContrastMixesTowardTheTargetUntilItPasses() {
        val adjusted = ThemeColor(0x555555).ensuringContrast(4.5, listOf(ThemeColor.BLACK), listOf(ThemeColor.WHITE))
        assertTrue(adjusted.contrast(ThemeColor.BLACK) >= 4.5)
        assertNotEquals(ThemeColor.WHITE, adjusted)
    }

    @Test
    fun ensuringContrastFallsBackToTheLastTargetWhenNothingPasses() {
        val last = ThemeColor(0x888888)
        assertEquals(last, ThemeColor(0x777777).ensuringContrast(22.0, listOf(ThemeColor.BLACK), listOf(ThemeColor.WHITE, last)))
    }

    @Test
    fun rejectsValuesOutsideTwentyFourBits() {
        assertThrows(IllegalArgumentException::class.java) { ThemeColor(0x1000000) }
        assertThrows(IllegalArgumentException::class.java) { ThemeColor(-1) }
    }
}
