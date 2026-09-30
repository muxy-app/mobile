package com.muxy.app.features.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectIconColorTest {
    @Test
    fun parsesAHexColor() {
        assertEquals(0x7C3AED, ProjectIconColor.rgb("#7C3AED", isDark = false))
    }

    @Test
    fun parsesAShortHexColor() {
        assertEquals(0xFFFFFF, ProjectIconColor.rgb("#FFF", isDark = true))
    }

    @Test
    fun mapsNamedColorsToIosSystemColors() {
        assertEquals(0x007AFF, ProjectIconColor.rgb("blue", isDark = false))
        assertEquals(0x0A84FF, ProjectIconColor.rgb("blue", isDark = true))
        assertEquals(ProjectIconColor.rgb("purple", isDark = false), ProjectIconColor.rgb("violet", isDark = false))
        assertEquals(ProjectIconColor.rgb("red", isDark = false), ProjectIconColor.rgb("RED", isDark = false))
        assertEquals(ProjectIconColor.rgb("gray", isDark = true), ProjectIconColor.rgb("grey", isDark = true))
    }

    @Test
    fun fallsBackForMissingTokens() {
        assertNull(ProjectIconColor.rgb(null, isDark = false))
        assertNull(ProjectIconColor.rgb("", isDark = false))
    }

    @Test
    fun fallsBackForUnknownTokens() {
        listOf("chartreuse", "#ZZZ", "#12", "#-12345", "#1234567").forEach { assertNull(it, ProjectIconColor.rgb(it, isDark = false)) }
    }
}
