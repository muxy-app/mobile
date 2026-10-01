package com.muxy.app.features.terminal.input

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyModifiers
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareKeyMapperTest {
    private val control = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
    private val shift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
    private val leftAlt = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
    private val rightAlt = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
    private val meta = KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON

    private fun action(
        keyCode: Int,
        metaState: Int = 0,
        character: Char? = null,
    ) = HardwareKeyMapper.action(keyCode, metaState, character?.code ?: 0)

    private fun stroke(
        key: TerminalKey,
        modifiers: TerminalKeyModifiers = TerminalKeyModifiers.NONE,
    ) = HardwareKeyAction.Stroke(TerminalKeyStroke(key, modifiers))

    @Test
    fun arrowsAndNavigationKeysAreHandled() {
        assertEquals(stroke(TerminalKey.Up), action(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(stroke(TerminalKey.PageDown), action(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(stroke(TerminalKey.Delete), action(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(stroke(TerminalKey.Escape), action(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(stroke(TerminalKey.Home), action(KeyEvent.KEYCODE_MOVE_HOME))
        assertEquals(stroke(TerminalKey.Insert), action(KeyEvent.KEYCODE_INSERT))
    }

    @Test
    fun modifiersTravelWithSpecialKeys() {
        assertEquals(
            stroke(TerminalKey.Left, TerminalKeyModifiers(shift = true, control = true)),
            action(KeyEvent.KEYCODE_DPAD_LEFT, control or shift),
        )
        assertEquals(stroke(TerminalKey.Right, TerminalKeyModifiers.ALT), action(KeyEvent.KEYCODE_DPAD_RIGHT, leftAlt))
    }

    @Test
    fun shiftTabBecomesBackTab() {
        assertEquals(stroke(TerminalKey.Tab), action(KeyEvent.KEYCODE_TAB))
        assertEquals(stroke(TerminalKey.BackTab), action(KeyEvent.KEYCODE_TAB, shift))
    }

    @Test
    fun functionKeysAreNumbered() {
        assertEquals(stroke(TerminalKey.Function(1)), action(KeyEvent.KEYCODE_F1))
        assertEquals(stroke(TerminalKey.Function(12)), action(KeyEvent.KEYCODE_F12))
    }

    @Test
    fun enterAndBackspaceAreKeys() {
        assertEquals(stroke(TerminalKey.Enter), action(KeyEvent.KEYCODE_ENTER))
        assertEquals(stroke(TerminalKey.Backspace), action(KeyEvent.KEYCODE_DEL))
    }

    @Test
    fun controlLettersAreSentAsKeys() {
        assertEquals(stroke(TerminalKey.Character("c"), TerminalKeyModifiers.CONTROL), action(KeyEvent.KEYCODE_C, control, 'c'))
    }

    @Test
    fun leftAltSendsTheKeyWithAlt() {
        assertEquals(stroke(TerminalKey.Character("b"), TerminalKeyModifiers.ALT), action(KeyEvent.KEYCODE_B, leftAlt, 'b'))
    }

    @Test
    fun rightAltTypesTheLayoutCharacter() {
        assertEquals(HardwareKeyAction.Text("@"), action(KeyEvent.KEYCODE_Q, rightAlt, '@'))
    }

    @Test
    fun metaAndControlShiftShortcutsPasteAndCopy() {
        assertEquals(HardwareKeyAction.Paste, action(KeyEvent.KEYCODE_V, meta, 'v'))
        assertEquals(HardwareKeyAction.Copy, action(KeyEvent.KEYCODE_C, meta, 'c'))
        assertEquals(HardwareKeyAction.Paste, action(KeyEvent.KEYCODE_V, control or shift, 'V'))
        assertEquals(HardwareKeyAction.Copy, action(KeyEvent.KEYCODE_C, control or shift, 'C'))
        assertNull(action(KeyEvent.KEYCODE_X, meta, 'x'))
    }

    @Test
    fun plainTypingIsText() {
        assertEquals(HardwareKeyAction.Text("a"), action(KeyEvent.KEYCODE_A, 0, 'a'))
        assertEquals(HardwareKeyAction.Text("A"), action(KeyEvent.KEYCODE_A, shift, 'A'))
    }

    @Test
    fun deadKeysAndUnknownKeysAreLeftToTheSystem() {
        assertNull(HardwareKeyMapper.action(KeyEvent.KEYCODE_GRAVE, 0, KeyCharacterMap.COMBINING_ACCENT or '`'.code))
        assertNull(action(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun printableMetaStateKeepsShiftAndRightAltOnly() {
        assertEquals(KeyEvent.META_SHIFT_ON, HardwareKeyMapper.printableMetaState(KeyEvent.META_SHIFT_ON or control))
        assertEquals(0, HardwareKeyMapper.printableMetaState(leftAlt or meta))
        assertEquals(rightAlt, HardwareKeyMapper.printableMetaState(rightAlt))
    }
}
