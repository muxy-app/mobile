package com.muxy.app.features.terminal

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.terminalkit.StickyModifier
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import com.muxy.app.features.terminalkit.TerminalModifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

interface TerminalDisplay {
    fun screenNeedsRefresh()

    fun prepareForLiveOutput()
}

enum class TerminalMode {
    LIVE,
    HISTORY,
}

class TerminalController(
    private val source: TerminalSource,
    private val scope: CoroutineScope,
) : TerminalSourceListener {
    var display: TerminalDisplay? = null

    private val mutableMode = MutableStateFlow(TerminalMode.LIVE)
    private val mutableFollowing = MutableStateFlow(true)
    private val mutableHistoryStart = MutableStateFlow(false)
    private val mutableNotice = MutableStateFlow<String?>(null)
    private val mutableSticky = MutableStateFlow(StickyModifier())

    val mode: StateFlow<TerminalMode> = mutableMode.asStateFlow()
    val isFollowing: StateFlow<Boolean> = mutableFollowing.asStateFlow()
    val showsHistoryStart: StateFlow<Boolean> = mutableHistoryStart.asStateFlow()
    val notice: StateFlow<String?> = mutableNotice.asStateFlow()
    val sticky: StateFlow<StickyModifier> = mutableSticky.asStateFlow()

    private var history: HistoryDocument? = null
    private var historyPrefetch: ScrollbackRequest? = null
    private var historyPrefetchRevision = 0L
    private var historyEntry: ScrollbackRequest? = null
    private var historyReachedStart = false
    private var isAtHistoryTop = false
    private var screenRevision = 0L
    private var viewportSize: TerminalGridSize? = null
    private var screenRows = 0
    private var noticeJob: Job? = null

    init {
        source.listener = this
    }

    override fun screenDidChange() {
        screenRevision += 1
        display?.screenNeedsRefresh()
    }

    fun currentFrame(): TerminalFrame? = source.frame()?.also { screenRows = it.rows }

    fun viewportDidChange(size: TerminalGridSize) {
        if (!size.isUsable() || size == viewportSize) return
        viewportSize = size
        source.resize(size)
    }

    fun setFollowing(following: Boolean) {
        mutableFollowing.value = following
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        returnToLive()
        val armed = mutableSticky.value
        val stroke = armed.takeIf { it.armed }?.stroke(text)
        if (stroke != null) {
            disarm()
            source.send(stroke)
            return
        }
        source.sendText(text)
    }

    fun send(stroke: TerminalKeyStroke) {
        returnToLive()
        val armed = mutableSticky.value
        if (!armed.armed) {
            source.send(stroke)
            return
        }
        disarm()
        source.send(armed.applied(stroke))
    }

    fun paste(text: String) {
        if (text.isEmpty()) return
        if (text.toByteArray().size > MAXIMUM_PASTE_BYTES) {
            show(PASTE_TOO_LARGE)
            return
        }
        returnToLive()
        source.paste(text)
    }

    fun scroll(
        direction: TerminalScrollDirection,
        cell: TerminalCellPosition,
    ) {
        source.scroll(direction, cell)
    }

    fun click(cell: TerminalCellPosition) {
        source.click(cell)
    }

    fun forwardScroll(deltaY: Double): Boolean = source.forwardScroll(deltaY)

    fun toggleModifier() {
        mutableSticky.update { it.copy(armed = !it.armed) }
    }

    fun selectModifier(modifier: TerminalModifier) {
        mutableSticky.value = StickyModifier(active = modifier, armed = false)
    }

    fun prefetchHistory() {
        if (mutableMode.value != TerminalMode.LIVE || historyPrefetch != null || historyEntry != null) return
        historyPrefetch = ScrollbackRequest(scope, source, HISTORY_PAGE_ROWS)
        historyPrefetchRevision = screenRevision
    }

    fun discardOutdatedHistoryPrefetch() {
        if (historyPrefetchRevision == screenRevision) return
        historyPrefetch = null
    }

    fun enterPrefetchedHistory(): HistoryDocument? {
        if (mutableMode.value != TerminalMode.LIVE) return null
        val snapshot = historyPrefetch?.snapshot ?: return null
        return openHistory(snapshot)
    }

    suspend fun enterHistory(): HistoryDocument? {
        if (mutableMode.value != TerminalMode.LIVE || history != null || historyEntry != null) return null
        val entry = historyPrefetch ?: ScrollbackRequest(scope, source, HISTORY_PAGE_ROWS)
        historyPrefetch = null
        historyEntry = entry
        val snapshot = entry.await()
        if (historyEntry !== entry) return null
        historyEntry = null
        return snapshot?.let(::openHistory)
    }

    suspend fun loadOlderHistory(): Int {
        val document = history ?: return 0
        val added = document.loadOlder(OLDER_PAGE_ROWS)
        if (history !== document) return added
        historyReachedStart = document.reachedStart
        publishHistoryStart()
        return added
    }

    fun setAtHistoryTop(atTop: Boolean) {
        if (isAtHistoryTop == atTop) return
        isAtHistoryTop = atTop
        publishHistoryStart()
    }

    fun returnToLive() {
        leaveHistory()
        setFollowing(true)
        display?.prepareForLiveOutput()
        display?.screenNeedsRefresh()
    }

    private fun openHistory(snapshot: TerminalScrollback): HistoryDocument {
        historyPrefetch = null
        val document = HistoryDocument(snapshot, screenRows)
        history = document
        historyReachedStart = document.reachedStart
        isAtHistoryTop = false
        mutableMode.value = TerminalMode.HISTORY
        setFollowing(false)
        publishHistoryStart()
        return document
    }

    private fun leaveHistory() {
        history = null
        historyPrefetch = null
        historyEntry = null
        mutableMode.value = TerminalMode.LIVE
        historyReachedStart = false
        isAtHistoryTop = false
        publishHistoryStart()
    }

    private fun publishHistoryStart() {
        mutableHistoryStart.value = mutableMode.value == TerminalMode.HISTORY && historyReachedStart && isAtHistoryTop
    }

    private fun disarm() {
        mutableSticky.update { it.copy(armed = false) }
    }

    private fun show(notice: String) {
        mutableNotice.value = notice
        noticeJob?.cancel()
        noticeJob =
            scope.launch {
                delay(NOTICE_DURATION)
                mutableNotice.value = null
            }
    }

    private class ScrollbackRequest(
        scope: CoroutineScope,
        source: TerminalSource,
        maxRows: Int,
    ) {
        var snapshot: TerminalScrollback? = null
            private set

        private val response =
            scope.launch {
                snapshot =
                    attempt { source.scrollback(maxRows) }
                        .onFailure { Log.terminal.error("Scrollback failed", it) }
                        .getOrNull()
            }

        suspend fun await(): TerminalScrollback? {
            response.join()
            return snapshot
        }
    }

    companion object {
        const val MAXIMUM_PASTE_BYTES = 1_048_576 - 12
        const val HISTORY_PAGE_ROWS = 200
        const val OLDER_PAGE_ROWS = 500
        const val PASTE_TOO_LARGE = "That text is too large to paste. The limit is 1 MB."
        private val NOTICE_DURATION = 4.seconds
    }
}
