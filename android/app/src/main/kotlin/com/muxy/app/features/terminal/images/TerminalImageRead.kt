package com.muxy.app.features.terminal.images

sealed interface TerminalImageRead {
    class Loaded(
        val image: TerminalImage,
    ) : TerminalImageRead

    data object TooLarge : TerminalImageRead

    data object Unreadable : TerminalImageRead
}
