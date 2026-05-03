package com.muxy.app.ui.terminal

import com.muxy.app.data.PaneSession
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class MuxyTerminalSession(
    private val pane: PaneSession,
    sessionClient: TerminalSessionClient,
    transcriptRows: Int? = DEFAULT_TRANSCRIPT_ROWS,
) : TerminalSession(transcriptRows, sessionClient) {

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        pane.sendBytes(data, offset, count)
    }

    fun acceptRemoteOutput(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        feedRemoteOutput(bytes, bytes.size)
    }

    fun resetEmulatorScreen() {
        emulator?.reset()
    }

    companion object {
        const val DEFAULT_TRANSCRIPT_ROWS: Int = 2000
    }
}
