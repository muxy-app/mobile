package com.muxy.app.features.terminal.input

import com.muxy.app.features.terminal.input.TerminalInputEffect.Backspaces
import com.muxy.app.features.terminal.input.TerminalInputEffect.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalTextInputStateTest {
    private val state = TerminalTextInputState()

    @Test
    fun typedTextIsSent() {
        assertEquals(listOf(Text("ls")), state.insert("ls"))
        assertEquals("ls", state.text)
        assertEquals(InputRange.at(2), state.selection)
    }

    @Test
    fun typingOverASelectionErasesItFirst() {
        state.insert("teh")
        state.select(InputRange(1, 2))
        assertEquals(listOf(Backspaces(2), Text("he")), state.insert("he"))
        assertEquals("the", state.text)
    }

    @Test
    fun deferredCompositionIsNotSentUntilCommitted() {
        assertTrue(state.setComposing("ni", cursor = 2, eager = false).isEmpty())
        assertEquals("ni", state.markedText)
        assertEquals(listOf(Text("你")), state.insert("你"))
        assertNull(state.markedText)
        assertEquals("你", state.text)
    }

    @Test
    fun finishingADeferredCompositionCommitsIt() {
        state.setComposing("かな", cursor = 2, eager = false)
        assertEquals(listOf(Text("かな")), state.finishComposing())
        assertNull(state.markedText)
    }

    @Test
    fun clearingTheCompositionSendsNothing() {
        state.setComposing("n", cursor = 1, eager = false)
        assertTrue(state.setComposing("", cursor = 0, eager = false).isEmpty())
        assertNull(state.markedText)
        assertTrue(state.finishComposing().isEmpty())
    }

    @Test
    fun eagerCompositionSendsEachChangeAsItHappens() {
        assertEquals(listOf(Text("h")), state.setComposing("h", cursor = 1, eager = true))
        assertEquals(listOf(Text("e")), state.setComposing("he", cursor = 2, eager = true))
        assertNull(state.markedText)
        assertEquals(listOf(Backspaces(1)), state.setComposing("h", cursor = 1, eager = true))
        assertEquals(listOf(Backspaces(1), Text("the ")), state.insert("the "))
        assertTrue(state.finishComposing().isEmpty())
    }

    @Test
    fun deletingWithNothingBufferedStillSendsBackspace() {
        assertEquals(listOf(Backspaces(1)), state.deleteBackward())
        assertEquals(listOf(Backspaces(1)), state.deleteSurrounding(1, 0))
    }

    @Test
    fun deletingRemovesAWholeEmoji() {
        state.insert("a👍🏽")
        assertEquals(listOf(Backspaces(1)), state.deleteBackward())
        assertEquals("a", state.text)
    }

    @Test
    fun deletingSurroundingTextNeverSplitsASurrogatePair() {
        state.insert("a😀")
        assertEquals(listOf(Backspaces(1)), state.deleteSurrounding(1, 0))
        assertEquals("a", state.text)
    }

    @Test
    fun codePointDeletionsCountCodePoints() {
        state.insert("ab😀")
        assertEquals(listOf(Backspaces(2)), state.deleteSurroundingCodePoints(2, 0))
        assertEquals("a", state.text)
    }

    @Test
    fun replacingAWordSendsOnlyTheChangedTail() {
        state.insert("git stauts")
        assertEquals(listOf(Backspaces(3), Text("tus")), state.replace(InputRange(4, 6), "status"))
        assertEquals("git status", state.text)
    }

    @Test
    fun editingInTheMiddleRetypesTheTail() {
        state.insert("git status")
        state.select(InputRange.at(7))
        assertEquals(listOf(Backspaces(3), Text("xtus")), state.insert("x"))
        assertEquals("git staxtus", state.text)
    }

    @Test
    fun aRecomposedWordIsCorrectedInPlace() {
        state.insert("teh ")
        assertTrue(state.setComposingRegion(0, 3, eager = true).isEmpty())
        assertEquals(listOf(Backspaces(3), Text("he ")), state.insert("the"))
        assertEquals("the ", state.text)
    }

    @Test
    fun aDeferredRecompositionWaitsAndIsNotDrawnTwice() {
        state.insert("teh ")
        state.setComposingRegion(0, 3, eager = false)
        assertTrue(state.setComposing("the", cursor = 3, eager = false).isEmpty())
        assertNull(state.markedText)
        assertEquals(listOf(Backspaces(3), Text("he ")), state.finishComposing())
    }

    @Test
    fun replacingDuringCompositionIsIgnored() {
        state.setComposing("ni", cursor = 2, eager = false)
        assertTrue(state.replace(InputRange(0, 2), "x").isEmpty())
    }

    @Test
    fun aNewLineAsksForAFreshBuffer() {
        state.insert("ls")
        assertFalse(state.needsReset)
        state.insert("\n")
        assertTrue(state.needsReset)
        state.reset()
        assertEquals(0, state.length)
    }

    @Test
    fun aLongBufferAsksForAFreshBuffer() {
        state.insert("a".repeat(257))
        assertTrue(state.needsReset)
    }

    @Test
    fun outOfRangeSubstringsAreClamped() {
        state.insert("abc")
        assertEquals("c", state.substring(InputRange(2, 10)))
        assertTrue(state.substring(InputRange(10, 1)).isEmpty())
    }

    @Test
    fun surroundingTextIsReadAroundTheSelection() {
        state.insert("hello")
        state.select(InputRange.at(2))
        assertEquals("he", state.textBefore(10))
        assertEquals("llo", state.textAfter(10))
    }
}
