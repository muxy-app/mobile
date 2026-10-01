package com.muxy.app.features.terminal.input

class TerminalInputSession(
    private val host: Host,
) {
    interface Host {
        fun deliver(effects: List<TerminalInputEffect>)

        fun markedTextChanged(text: String?)

        fun reportSelection(state: TerminalTextInputState)

        fun restartInput()

        fun composesEagerly(): Boolean

        fun pressEnter()

        fun pasteFromClipboard()

        fun copySelection()
    }

    val state = TerminalTextInputState()

    private var generation = 0
    private var batchDepth = 0

    fun openConnection(): Int {
        generation += 1
        batchDepth = 0
        return generation
    }

    fun isCurrent(connection: Int): Boolean = connection == generation

    fun edit(
        connection: Int,
        change: (TerminalTextInputState) -> List<TerminalInputEffect>,
    ): Boolean {
        if (!isCurrent(connection)) return false
        val effects = change(state)
        host.markedTextChanged(state.markedText)
        if (effects.isNotEmpty()) host.deliver(effects)
        if (state.needsReset) {
            reset()
            return true
        }
        if (batchDepth == 0) host.reportSelection(state)
        return true
    }

    fun composesEagerly(): Boolean = host.composesEagerly()

    fun beginBatch(connection: Int): Boolean {
        if (!isCurrent(connection)) return false
        batchDepth += 1
        return true
    }

    fun endBatch(connection: Int): Boolean {
        if (!isCurrent(connection)) return false
        batchDepth = (batchDepth - 1).coerceAtLeast(0)
        if (batchDepth == 0) host.reportSelection(state)
        return batchDepth > 0
    }

    fun close(connection: Int) {
        if (!isCurrent(connection)) return
        batchDepth = 0
        state.reset()
        host.markedTextChanged(null)
    }

    fun pressEnter(connection: Int): Boolean {
        if (!isCurrent(connection)) return false
        host.pressEnter()
        return true
    }

    fun paste(connection: Int): Boolean {
        if (!isCurrent(connection)) return false
        host.pasteFromClipboard()
        return true
    }

    fun copy(connection: Int): Boolean {
        if (!isCurrent(connection)) return false
        host.copySelection()
        return true
    }

    fun flushComposition() {
        val effects = state.finishComposing()
        host.markedTextChanged(null)
        if (effects.isNotEmpty()) host.deliver(effects)
    }

    fun reset() {
        if (state.length == 0 && state.composing == null) return
        state.reset()
        host.markedTextChanged(null)
        host.restartInput()
    }
}
