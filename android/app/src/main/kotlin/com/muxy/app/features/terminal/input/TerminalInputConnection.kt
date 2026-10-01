package com.muxy.app.features.terminal.input

import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest

class TerminalInputConnection(
    view: View,
    private val session: TerminalInputSession,
) : BaseInputConnection(view, false) {
    private val connection = session.openConnection()

    override fun commitText(
        text: CharSequence?,
        newCursorPosition: Int,
    ): Boolean = session.edit(connection) { it.insert(text?.toString().orEmpty()) }

    override fun setComposingText(
        text: CharSequence?,
        newCursorPosition: Int,
    ): Boolean {
        val composed = text?.toString().orEmpty()
        val cursor = if (newCursorPosition > 0) composed.length + newCursorPosition - 1 else newCursorPosition
        return session.edit(connection) { it.setComposing(composed, cursor, session::composesEagerly) }
    }

    override fun setComposingRegion(
        start: Int,
        end: Int,
    ): Boolean = session.edit(connection) { it.setComposingRegion(start, end, session.composesEagerly()) }

    override fun finishComposingText(): Boolean = session.edit(connection) { it.finishComposing() }

    override fun deleteSurroundingText(
        beforeLength: Int,
        afterLength: Int,
    ): Boolean = session.edit(connection) { it.deleteSurrounding(beforeLength, afterLength) }

    override fun deleteSurroundingTextInCodePoints(
        beforeLength: Int,
        afterLength: Int,
    ): Boolean = session.edit(connection) { it.deleteSurroundingCodePoints(beforeLength, afterLength) }

    override fun setSelection(
        start: Int,
        end: Int,
    ): Boolean =
        session.edit(connection) {
            it.select(InputRange(minOf(start, end), kotlin.math.abs(end - start)))
            emptyList()
        }

    override fun getTextBeforeCursor(
        length: Int,
        flags: Int,
    ): CharSequence = session.state.textBefore(length)

    override fun getTextAfterCursor(
        length: Int,
        flags: Int,
    ): CharSequence = session.state.textAfter(length)

    override fun getSelectedText(flags: Int): CharSequence? = session.state.substring(session.state.selection).ifEmpty { null }

    override fun getCursorCapsMode(reqModes: Int): Int = 0

    override fun getExtractedText(
        request: ExtractedTextRequest?,
        flags: Int,
    ): ExtractedText? = null

    override fun beginBatchEdit(): Boolean = session.beginBatch(connection)

    override fun endBatchEdit(): Boolean = session.endBatch(connection)

    override fun performEditorAction(actionCode: Int): Boolean = session.pressEnter(connection)

    override fun performContextMenuAction(id: Int): Boolean =
        when (id) {
            android.R.id.paste, android.R.id.pasteAsPlainText -> session.paste(connection)
            android.R.id.copy -> session.copy(connection)
            else -> false
        }

    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false

    override fun closeConnection() {
        session.close(connection)
        super.closeConnection()
    }

    companion object {
        fun configure(
            info: EditorInfo,
            state: TerminalTextInputState,
        ) {
            info.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            info.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_ENTER_ACTION or EditorInfo.IME_ACTION_NONE
            info.initialSelStart = state.selection.location
            info.initialSelEnd = state.selection.end
        }
    }
}
