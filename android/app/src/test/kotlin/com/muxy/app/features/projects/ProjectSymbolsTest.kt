package com.muxy.app.features.projects

import com.muxy.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProjectSymbolsTest {
    @Test
    fun fallsBackToAFolder() {
        assertEquals(R.drawable.ic_folder, ProjectSymbols.drawable(null))
        assertEquals(R.drawable.ic_folder, ProjectSymbols.drawable("not.a.symbol"))
    }

    @Test
    fun mapsTheSymbolsTheMacOffers() {
        assertEquals(R.drawable.ic_terminal, ProjectSymbols.drawable("terminal"))
        assertEquals(R.drawable.ic_language, ProjectSymbols.drawable("globe"))
        assertEquals(R.drawable.ic_code, ProjectSymbols.drawable("chevron.left.forwardslash.chevron.right"))
        assertEquals(R.drawable.ic_fork_right, ProjectSymbols.drawable("arrow.triangle.branch"))
    }

    @Test
    fun mapsFilledSymbolsToFilledDrawables() {
        assertEquals(R.drawable.ic_folder_fill, ProjectSymbols.drawable("folder.fill"))
        assertNotEquals(ProjectSymbols.drawable("star"), ProjectSymbols.drawable("star.fill"))
    }
}
