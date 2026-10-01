package com.muxy.app.features.terminal.input

import com.muxy.app.core.text.Graphemes
import kotlin.math.abs

sealed interface TerminalInputEffect {
    data class Text(
        val text: String,
    ) : TerminalInputEffect

    data class Backspaces(
        val count: Int,
    ) : TerminalInputEffect
}

data class InputRange(
    val location: Int,
    val length: Int,
) {
    val end: Int
        get() = location + length

    companion object {
        fun at(location: Int): InputRange = InputRange(location, 0)
    }
}

class TerminalTextInputState {
    var text: String = ""
        private set

    var selection: InputRange = InputRange.at(0)
        private set

    var composing: InputRange? = null
        private set

    private var sent = ""
    private var composingOriginal = ""
    private var composesEagerly = true

    val length: Int
        get() = text.length

    val markedText: String?
        get() = composing?.takeIf { !composesEagerly && composingOriginal.isEmpty() }?.let(::substring)

    val needsReset: Boolean
        get() = composing == null && (length > RESET_LENGTH || text.any(::isLineBreak))

    fun substring(range: InputRange): String {
        val clamped = clamp(range)
        return text.substring(clamped.location, clamped.end)
    }

    fun textBefore(count: Int): String = text.substring((selection.location - count.coerceAtLeast(0)).coerceAtLeast(0), selection.location)

    fun textAfter(count: Int): String = text.substring(selection.end, (selection.end + count.coerceAtLeast(0)).coerceAtMost(length))

    fun insert(inserted: String): List<TerminalInputEffect> {
        val target = composing ?: selection
        splice(target, inserted)
        endComposition()
        selection = InputRange.at(target.location + inserted.length)
        return flush()
    }

    fun deleteBackward(): List<TerminalInputEffect> {
        if (selection.length > 0) {
            splice(selection, "")
            selection = InputRange.at(selection.location)
            return flush()
        }
        if (selection.location <= 0) return emptyBufferBackspace()
        val start = Graphemes.previousBoundary(text, selection.location)
        splice(InputRange(start, selection.location - start), "")
        selection = InputRange.at(start)
        return flush()
    }

    fun deleteSurrounding(
        before: Int,
        after: Int,
    ): List<TerminalInputEffect> {
        endComposition()
        val afterStart = selection.end
        val afterEnd = codePointBoundary((afterStart + after.coerceAtLeast(0)).coerceAtMost(length), forward = true)
        splice(InputRange(afterStart, afterEnd - afterStart), "")
        if (before <= 0) return flush()
        val end = selection.location
        if (end <= 0) return flush() + emptyBufferBackspace()
        val start = codePointBoundary((end - before).coerceAtLeast(0), forward = false)
        splice(InputRange(start, end - start), "")
        selection = InputRange(start, selection.length)
        return flush()
    }

    fun deleteSurroundingCodePoints(
        before: Int,
        after: Int,
    ): List<TerminalInputEffect> {
        val beforeUnits = selection.location - offsetByCodePoints(selection.location, -before.coerceAtLeast(0))
        val afterUnits = offsetByCodePoints(selection.end, after.coerceAtLeast(0)) - selection.end
        return deleteSurrounding(beforeUnits, afterUnits)
    }

    fun replace(
        range: InputRange,
        replacement: String,
    ): List<TerminalInputEffect> {
        if (composing != null) return emptyList()
        val clamped = clamp(range)
        splice(clamped, replacement)
        selection = InputRange.at(clamped.location + replacement.length)
        return flush()
    }

    fun setComposing(
        newText: String,
        cursor: Int,
        eager: Boolean,
    ): List<TerminalInputEffect> {
        val target = composing ?: selection
        if (composing == null) {
            composesEagerly = eager
            composingOriginal = substring(target)
        }
        splice(target, newText)
        if (newText.isEmpty()) {
            endComposition()
            selection = InputRange.at(target.location)
            return flush()
        }
        composing = InputRange(target.location, newText.length)
        selection = InputRange.at(target.location + cursor.coerceIn(0, newText.length))
        return flush()
    }

    fun setComposingRegion(
        start: Int,
        end: Int,
        eager: Boolean,
    ): List<TerminalInputEffect> {
        endComposition()
        val region = clamp(InputRange(minOf(start, end), abs(end - start)))
        if (region.length > 0) {
            composing = region
            composingOriginal = substring(region)
            composesEagerly = eager
        }
        return flush()
    }

    fun finishComposing(): List<TerminalInputEffect> {
        val marked = composing ?: return emptyList()
        endComposition()
        selection = InputRange.at(marked.end)
        return flush()
    }

    fun select(range: InputRange) {
        selection = clamp(range)
    }

    fun reset() {
        text = ""
        sent = ""
        selection = InputRange.at(0)
        endComposition()
    }

    private fun flush(): List<TerminalInputEffect> {
        val target = terminalText()
        val prefix = Graphemes.commonPrefixLength(sent, target)
        val removed = sent.substring(prefix)
        val inserted = target.substring(prefix)
        sent = target
        return buildList {
            if (removed.isNotEmpty()) add(TerminalInputEffect.Backspaces(Graphemes.count(removed)))
            if (inserted.isNotEmpty()) add(TerminalInputEffect.Text(inserted))
        }
    }

    private fun terminalText(): String {
        val marked = composing ?: return text
        if (composesEagerly) return text
        val clamped = clamp(marked)
        return text.replaceRange(clamped.location, clamped.end, composingOriginal)
    }

    private fun emptyBufferBackspace(): List<TerminalInputEffect> {
        if (sent.isNotEmpty()) return emptyList()
        return listOf(TerminalInputEffect.Backspaces(1))
    }

    private fun endComposition() {
        composing = null
        composingOriginal = ""
    }

    private fun splice(
        range: InputRange,
        replacement: String,
    ) {
        val clamped = clamp(range)
        text = text.replaceRange(clamped.location, clamped.end, replacement)
    }

    private fun clamp(range: InputRange): InputRange {
        val location = range.location.coerceIn(0, length)
        return InputRange(location, range.length.coerceIn(0, length - location))
    }

    private fun codePointBoundary(
        offset: Int,
        forward: Boolean,
    ): Int {
        val splitsPair = offset in 1 until length && Character.isLowSurrogate(text[offset]) && Character.isHighSurrogate(text[offset - 1])
        if (!splitsPair) return offset
        return if (forward) offset + 1 else offset - 1
    }

    private fun offsetByCodePoints(
        from: Int,
        codePoints: Int,
    ): Int {
        var offset = from
        repeat(abs(codePoints)) {
            offset =
                when {
                    codePoints > 0 && offset < length -> offset + Character.charCount(text.codePointAt(offset))
                    codePoints < 0 && offset > 0 -> offset - Character.charCount(text.codePointBefore(offset))
                    else -> return offset
                }
        }
        return offset
    }

    private fun isLineBreak(character: Char): Boolean =
        character == '\n' || character == '\r' || character == '\u000B' || character == '\u000C' ||
            character == '\u0085' || character == ' ' || character == ' '

    private companion object {
        const val RESET_LENGTH = 256
    }
}
