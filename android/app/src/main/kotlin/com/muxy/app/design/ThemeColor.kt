package com.muxy.app.design

import kotlin.math.abs
import kotlin.math.roundToInt

class ThemeColor(
    val rgb: Int,
) {
    init {
        require(rgb in 0..0xFFFFFF) { "ThemeColor takes a 24-bit RGB value" }
    }

    val red: Double = channel(rgb shr 16)
    val green: Double = channel(rgb shr 8)
    val blue: Double = channel(rgb)
    val luminance: Double = 0.2126 * linearized(red) + 0.7152 * linearized(green) + 0.0722 * linearized(blue)

    val lightness: Double
        get() = if (luminance > CIE_EPSILON) 116 * StrictMath.cbrt(luminance) - 16 else CIE_KAPPA * luminance

    fun contrast(other: ThemeColor): Double = (maxOf(luminance, other.luminance) + 0.05) / (minOf(luminance, other.luminance) + 0.05)

    fun mixed(
        other: ThemeColor,
        amount: Double,
    ): ThemeColor =
        ThemeColor(
            quantized(red + (other.red - red) * amount) shl 16 or
                (quantized(green + (other.green - green) * amount) shl 8) or
                quantized(blue + (other.blue - blue) * amount),
        )

    fun shiftingLightness(
        delta: Double,
        toward: ThemeColor,
    ): ThemeColor {
        val origin = lightness
        return firstMix(toward) { abs(it.lightness - origin) >= delta } ?: toward
    }

    fun ensuringContrast(
        minimum: Double,
        against: List<ThemeColor>,
        toward: List<ThemeColor>,
    ): ThemeColor {
        val passes: (ThemeColor) -> Boolean = { candidate -> against.all { candidate.contrast(it) >= minimum } }
        if (passes(this)) return this
        return toward.asSequence().mapNotNull { firstMix(it, passes) }.firstOrNull() ?: toward.lastOrNull() ?: this
    }

    override fun equals(other: Any?): Boolean = other is ThemeColor && other.rgb == rgb

    override fun hashCode(): Int = rgb

    override fun toString(): String = "ThemeColor(#${rgb.toString(16).padStart(6, '0').uppercase()})"

    private fun firstMix(
        toward: ThemeColor,
        passes: (ThemeColor) -> Boolean,
    ): ThemeColor? =
        (0..SEARCH_STEPS)
            .asSequence()
            .map { mixed(toward, it.toDouble() / SEARCH_STEPS.toDouble()) }
            .firstOrNull(passes)

    companion object {
        val WHITE = ThemeColor(0xFFFFFF)
        val BLACK = ThemeColor(0x000000)

        private const val SEARCH_STEPS = 200
        private const val CIE_EPSILON = 216.0 / 24389
        private const val CIE_KAPPA = 24389.0 / 27

        private fun channel(value: Int): Double = (value and 0xFF) / 255.0

        private fun quantized(value: Double): Int = (value.coerceIn(0.0, 1.0) * 255).roundToInt()

        private fun linearized(value: Double): Double =
            if (value <= 0.04045) value / 12.92 else StrictMath.pow((value + 0.055) / 1.055, 2.4)
    }
}
