package com.muxy.app.features.projectdetail.terminal

import com.muxy.app.design.ThemePalette
import com.muxy.app.networking.muxy1.protocol.ClientTerminalTheme
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.transport.TransportException
import com.muxy.app.networking.muxy1.transport.TransportFailure

fun ThemePalette.clientTerminalTheme(): ClientTerminalTheme =
    ClientTerminalTheme(
        fg = foreground,
        bg = background,
        palette = ansi,
        cursorColor = cursor,
        cursorText = cursorText,
        selectionBackground = selectionBackground,
        selectionForeground = selectionForeground,
    )

object TakeoverFailure {
    const val TITLE = "Couldn't take control"
    const val TIMED_OUT = "Your Mac didn't respond in time."
    const val PANE_GONE = "This terminal is no longer open on your Mac."
    const val INTERRUPTED = "The connection to your Mac was interrupted."

    fun message(error: Throwable): String =
        when {
            error is TransportException && error.failure == TransportFailure.TIMED_OUT -> TIMED_OUT
            error is ProtocolException && error.code == ErrorCode.NOT_FOUND -> PANE_GONE
            error is ProtocolException -> error.body.message
            else -> INTERRUPTED
        }
}
