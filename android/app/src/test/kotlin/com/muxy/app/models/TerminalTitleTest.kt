package com.muxy.app.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class TerminalTitleTest {
    @Test
    fun stripsDecorations() {
        assertEquals("Fix pairing timeout", TerminalTitle.sanitized("✳ Fix pairing timeout"))
        assertEquals("Working on tests", TerminalTitle.sanitized("⠐ Working on tests"))
        assertEquals("build", TerminalTitle.sanitized("🤖 build"))
        assertEquals("first step", TerminalTitle.sanitized("1️⃣ first step"))
        assertEquals("family", TerminalTitle.sanitized("👨‍👩‍👧 family"))
    }

    @Test
    fun keepsPlainText() {
        listOf("zsh", "npm run dev", "café au lait", "xal — \$code-review pr 1248").forEach {
            assertEquals(it, TerminalTitle.sanitized(it))
        }
    }

    @Test
    fun trimsWhitespaceAndEmptiesDecorationOnlyTitles() {
        assertEquals("vim", TerminalTitle.sanitized("  vim  "))
        assertEquals("", TerminalTitle.sanitized("✳ ⠂"))
    }

    @Test
    fun workspaceTabsFallBackToTerminal() {
        val decorated = Tab(UUID.randomUUID(), TabKind.Terminal, "🤖 build", isPinned = false)
        val empty = Tab(UUID.randomUUID(), TabKind.Terminal, "✳", isPinned = false)

        assertEquals("build", decorated.displayTitle)
        assertEquals("Terminal", empty.displayTitle)
    }
}
