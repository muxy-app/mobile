package com.muxy.app.features.demo

import com.termux.terminal.WcWidth
import java.util.UUID

class DemoShell {
    private val lines = mutableMapOf<UUID, StringBuilder>()

    fun open(paneId: UUID): String {
        lines[paneId] = StringBuilder()
        return BANNER + PROMPT
    }

    fun input(
        paneId: UUID,
        text: String,
    ): String {
        val line = lines.getOrPut(paneId) { StringBuilder() }
        val output = StringBuilder()
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (codePoint == ESCAPE) {
                val end = escapeSequenceEnd(text, index + 1)
                if (isCursorSequence(text, index + 1)) output.append(text, index, end)
                index = end
                continue
            }
            index += Character.charCount(codePoint)
            output.append(respond(codePoint, line))
        }
        return output.toString()
    }

    private fun respond(
        codePoint: Int,
        line: StringBuilder,
    ): String =
        when (codePoint) {
            CARRIAGE_RETURN, LINE_FEED -> runCommand(line)
            DELETE, BACKSPACE -> erase(line)
            INTERRUPT -> interrupt(line)
            else -> type(codePoint, line)
        }

    private fun runCommand(line: StringBuilder): String {
        val command = line.toString()
        line.setLength(0)
        if (command.isBlank()) return NEW_LINE + PROMPT
        return NEW_LINE + NOTICE + NEW_LINE + PROMPT
    }

    private fun erase(line: StringBuilder): String {
        if (line.isEmpty()) return ""
        val codePoint = line.codePointBefore(line.length)
        line.setLength(line.length - Character.charCount(codePoint))
        val cells = WcWidth.width(codePoint).coerceAtLeast(1)
        return "\b".repeat(cells) + " ".repeat(cells) + "\b".repeat(cells)
    }

    private fun interrupt(line: StringBuilder): String {
        line.setLength(0)
        return "^C" + NEW_LINE + PROMPT
    }

    private fun type(
        codePoint: Int,
        line: StringBuilder,
    ): String {
        if (codePoint < SPACE && codePoint != TAB) return ""
        line.appendCodePoint(codePoint)
        return String(Character.toChars(codePoint))
    }

    private fun isCursorSequence(
        text: String,
        start: Int,
    ): Boolean = start < text.length && (text[start] == '[' || text[start] == 'O')

    private fun escapeSequenceEnd(
        text: String,
        start: Int,
    ): Int {
        if (start >= text.length) return start
        return when (text[start]) {
            '[' -> {
                val end = (start + 1 until text.length).firstOrNull { text[it].code in FINAL_BYTES }
                (end ?: (text.length - 1)) + 1
            }

            'O' -> {
                (start + 2).coerceAtMost(text.length)
            }

            else -> {
                start + 1
            }
        }
    }

    companion object {
        const val PROMPT = "demo@muxy ~ % "
        const val NOTICE = "\u001B[33m[Demo Mode]\u001B[0m Commands are not executed in demo mode."
        const val BANNER =
            "\u001B[1;32mDemo Mode\u001B[0m - this terminal is simulated.\r\n" +
                "Type any command and press Enter to see the demo response.\r\n"

        private const val NEW_LINE = "\r\n"
        private const val ESCAPE = 0x1B
        private const val CARRIAGE_RETURN = 0x0D
        private const val LINE_FEED = 0x0A
        private const val DELETE = 0x7F
        private const val BACKSPACE = 0x08
        private const val INTERRUPT = 0x03
        private const val TAB = 0x09
        private const val SPACE = 0x20
        private val FINAL_BYTES = 0x40..0x7E
    }
}
