package com.muxy.app.features.terminal

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log

class HistoryDocument(
    private val snapshot: TerminalScrollback,
    screenRows: Int,
) : AutoCloseable {
    var lines: List<TerminalLine> = snapshot.lines()
        private set

    var reachedStart: Boolean = snapshot.historyRows == 0
        private set

    val screenRows: Int = screenRows.coerceAtMost(lines.size)

    private var isLoading = false

    val historyRowCount: Int
        get() = lines.size - screenRows

    suspend fun loadOlder(maxRows: Int): Int {
        if (isLoading || reachedStart) return 0
        isLoading = true
        val older =
            try {
                attempt { snapshot.loadOlder(maxRows) }
                    .onFailure { Log.terminal.error("Loading older history failed", it) }
                    .getOrNull()
            } finally {
                isLoading = false
            }
        if (older == null) return 0
        if (older.isEmpty()) {
            reachedStart = true
            return 0
        }
        lines = older + lines
        return older.size
    }

    override fun close() {
        snapshot.close()
    }
}
