package com.muxy.app.features.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DemoShellTest {
    private val shell = DemoShell()
    private val pane = UUID.randomUUID()

    @Test
    fun openingShowsTheBannerAndPrompt() {
        assertEquals(DemoShell.BANNER + DemoShell.PROMPT, shell.open(pane))
    }

    @Test
    fun typedInputIsEchoed() {
        shell.open(pane)
        assertEquals("l", shell.input(pane, "l"))
        assertEquals("s", shell.input(pane, "s"))
    }

    @Test
    fun enterAfterACommandPrintsTheNotice() {
        shell.open(pane)
        shell.input(pane, "l")
        shell.input(pane, "s")
        assertEquals("\r\n" + DemoShell.NOTICE + "\r\n" + DemoShell.PROMPT, shell.input(pane, "\r"))
    }

    @Test
    fun enterOnAnEmptyLineOnlyPrintsThePrompt() {
        shell.open(pane)
        assertEquals("\r\n" + DemoShell.PROMPT, shell.input(pane, "\r"))
    }

    @Test
    fun backspaceErasesTheLastCharacter() {
        shell.open(pane)
        shell.input(pane, "a")
        assertEquals("\b \b", shell.input(pane, "\u007F"))
        assertEquals("\r\n" + DemoShell.PROMPT, shell.input(pane, "\r"))
        assertEquals("", shell.input(pane, "\u007F"))
    }

    @Test
    fun controlCStartsANewPrompt() {
        shell.open(pane)
        shell.input(pane, "ls")
        assertEquals("^C\r\n" + DemoShell.PROMPT, shell.input(pane, "\u0003"))
        assertEquals("\r\n" + DemoShell.PROMPT, shell.input(pane, "\r"))
    }

    @Test
    fun arrowKeysMoveTheCursorButOtherEscapesAreDropped() {
        shell.open(pane)
        assertEquals("\u001B[D", shell.input(pane, "\u001B[D"))
        assertEquals("\u001BOA", shell.input(pane, "\u001BOA"))
        assertTrue(shell.input(pane, "\u001Bc").isEmpty())
    }
}
