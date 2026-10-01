package com.muxy.app.features.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import android.view.ActionMode
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.withClip
import com.muxy.app.core.text.Graphemes
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.design.ThemePalette
import com.muxy.app.features.terminal.input.HardwareKeyAction
import com.muxy.app.features.terminal.input.HardwareKeyMapper
import com.muxy.app.features.terminal.input.TerminalInputConnection
import com.muxy.app.features.terminal.input.TerminalInputEffect
import com.muxy.app.features.terminal.input.TerminalInputSession
import com.muxy.app.features.terminal.input.TerminalTextInputState
import com.muxy.app.features.terminal.rendering.TerminalColorResolver
import com.muxy.app.features.terminal.rendering.TerminalMetrics
import com.muxy.app.features.terminal.rendering.TerminalPaints
import com.muxy.app.features.terminal.rendering.TerminalRenderer
import com.muxy.app.features.terminal.rendering.TerminalScrollMath
import com.muxy.app.features.terminal.rendering.TerminalSelection
import com.muxy.app.features.terminal.viewport.SizeLockResult
import com.muxy.app.features.terminal.viewport.TerminalMomentum
import com.muxy.app.features.terminal.viewport.TerminalSizeLock
import com.muxy.app.features.terminal.viewport.TerminalViewportState
import com.muxy.app.features.terminalkit.TerminalFont
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TerminalSurfaceView(
    context: Context,
) : View(context),
    TerminalDisplay,
    TerminalInputSession.Host {
    var onSelectionChange: ((Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val inputMethods = context.getSystemService(InputMethodManager::class.java)
    private val clipboard = SystemTerminalClipboard(context)
    private val sizeLock = TerminalSizeLock(SystemClock::uptimeMillis)
    private val viewport = TerminalViewportState()
    private val momentum = TerminalMomentum.scaled(density)
    private val input = TerminalInputSession(this)
    private val backgroundPaint = Paint()
    private val confirmSize = Runnable { sampleSize() }
    private val longPress = Runnable { beginSelection() }
    private val momentumFrame = Choreographer.FrameCallback { stepMomentum() }

    private var palette = ThemeCatalog.muxy
    private var useNerdFont = true
    private var renderer = makeRenderer()
    private var controller: TerminalController? = null
    private var scope: CoroutineScope? = null
    private var lines: List<TerminalLine> = emptyList()
    private var columnCount = 0
    private var cursor: TerminalCursor? = null
    private var cursorRect: Rect? = null
    private var modes = TerminalModes()
    private var historyRows = 0
    private var displayedHistory: HistoryDocument? = null
    private var needsFrame = true
    private var offsetX = 0f
    private var offsetY = 0f
    private var selection: TerminalSelection? = null
    private var markedText: String? = null
    private var actionMode: ActionMode? = null
    private var restartPosted = false
    private var gesture = Gesture.NONE
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var tapPending = false
    private var lineAccumulator = 0f
    private var historyPullDistance = 0f
    private var isRequestingHistory = false
    private var isLoadingOlderHistory = false

    private val metrics: TerminalMetrics
        get() = renderer.metrics

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = ACCESSIBILITY_LABEL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) isAutoHandwritingEnabled = false
    }

    fun bind(
        controller: TerminalController,
        palette: ThemePalette,
        useNerdFont: Boolean,
    ) {
        if (this.controller !== controller) {
            detachController()
            this.controller = controller
            if (isAttachedToWindow) attachController()
        }
        if (palette == this.palette && useNerdFont == this.useNerdFont) return
        this.palette = palette
        this.useNerdFont = useNerdFont
        renderer = makeRenderer()
        sizeLock.locked?.let { clampOffsets() }
        reportViewport()
        invalidate()
    }

    fun activate(autoFocus: Boolean) {
        requestFocus()
        post { if (autoFocus) showKeyboard() else hideKeyboard() }
    }

    fun setKeyboardOcclusion(height: Float) {
        val next = height.coerceIn(0f, this.height.toFloat())
        if (abs(next - viewport.keyboardOffset) <= HALF_PIXEL) return
        stopMomentum()
        historyPullDistance = 0f
        viewport.updateKeyboardOffset(next)
        placeCursorAboveKeyboard()
        invalidate()
    }

    fun toggleKeyboard(visible: Boolean) {
        if (visible) {
            hideKeyboard()
            return
        }
        stopMomentum()
        viewport.followCursor()
        placeCursorAboveKeyboard()
        showKeyboard()
    }

    fun press(stroke: TerminalKeyStroke) {
        prepareForKeyInput()
        controller?.send(stroke)
    }

    fun sendAccessoryText(text: String) {
        prepareForKeyInput()
        controller?.sendText(text)
    }

    override fun pasteFromClipboard() {
        val text = clipboard.text()?.takeIf { it.isNotEmpty() } ?: return
        prepareForKeyInput()
        controller?.paste(text)
    }

    override fun copySelection() {
        val current = selection ?: return
        clipboard.copy(current.text(lines, columnCount))
        dismissMenu()
        updateSelection(null)
    }

    override fun screenNeedsRefresh() {
        needsFrame = true
        postInvalidateOnAnimation()
    }

    override fun prepareForLiveOutput() {
        stopMomentum()
        historyPullDistance = 0f
        viewport.followCursor()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        attachController()
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        stopMomentum()
        removeCallbacks(confirmSize)
        removeCallbacks(longPress)
        dismissMenu()
        isRequestingHistory = false
        isLoadingOlderHistory = false
        historyPullDistance = 0f
        if (hasFocus()) hideKeyboard()
        detachController()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        sampleSize()
    }

    override fun onDraw(canvas: Canvas) {
        backgroundPaint.color = renderer.backgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        pullFrame()
        val locked = sizeLock.locked ?: return
        val shift = viewport.viewportOffset
        canvas.withClip(0f, 0f, min(width, locked.width).toFloat(), min(height.toFloat(), locked.height - shift)) {
            translate(-offsetX, -offsetY - shift)
            drawDocument(this, renderer.rows(offsetY, offsetY + locked.height, lines.size))
        }
        if (viewport.keyboardOffset > 0f) {
            canvas.drawRect(0f, height - viewport.keyboardOffset, width.toFloat(), height.toFloat(), backgroundPaint)
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        TerminalInputConnection.configure(outAttrs, input.state)
        return TerminalInputConnection(this, input)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (event.isSystem || keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        val unicodeChar = event.getUnicodeChar(HardwareKeyMapper.printableMetaState(event.metaState))
        val action = HardwareKeyMapper.action(keyCode, event.metaState, unicodeChar) ?: return super.onKeyDown(keyCode, event)
        when (action) {
            is HardwareKeyAction.Stroke -> press(action.stroke)
            is HardwareKeyAction.Text -> sendAccessoryText(action.text)
            HardwareKeyAction.Paste -> pasteFromClipboard()
            HardwareKeyAction.Copy -> copySelection()
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchDown(event)
            MotionEvent.ACTION_MOVE -> touchMove(event)
            MotionEvent.ACTION_UP -> if (touchUp(tracker)) performClick()
            MotionEvent.ACTION_CANCEL -> touchCancel()
            MotionEvent.ACTION_POINTER_DOWN -> removeCallbacks(longPress)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        val tapped = tapPending
        tapPending = false
        dismissMenu()
        updateSelection(null)
        val current = controller
        if (tapped && current != null && modes.mouseTracking && displayedHistory == null) {
            cell(downX, downY)?.let(current::click)
            return true
        }
        showKeyboard()
        return true
    }

    override fun deliver(effects: List<TerminalInputEffect>) {
        updateSelection(null)
        val current = controller ?: return
        for (effect in effects) {
            when (effect) {
                is TerminalInputEffect.Text -> if (isPasted(effect.text)) current.paste(effect.text) else current.sendText(effect.text)
                is TerminalInputEffect.Backspaces -> repeat(effect.count) { current.send(TerminalKeyStroke(TerminalKey.Backspace)) }
            }
        }
    }

    override fun markedTextChanged(text: String?) {
        if (markedText == text) return
        markedText = text
        invalidate()
    }

    override fun reportSelection(state: TerminalTextInputState) {
        val composing = state.composing
        inputMethods.updateSelection(this, state.selection.location, state.selection.end, composing?.location ?: -1, composing?.end ?: -1)
    }

    override fun restartInput() {
        if (restartPosted) return
        restartPosted = true
        post {
            restartPosted = false
            if (hasFocus()) inputMethods.restartInput(this)
        }
    }

    override fun composesEagerly(): Boolean {
        val tag = inputMethods.currentInputMethodSubtype?.languageTag.orEmpty()
        return Locale.forLanguageTag(tag).language !in DEFERRED_COMPOSITION_LANGUAGES
    }

    override fun pressEnter() {
        press(TerminalKeyStroke(TerminalKey.Enter))
    }

    private fun prepareForKeyInput() {
        dismissMenu()
        updateSelection(null)
        input.flushComposition()
        input.reset()
    }

    private fun attachController() {
        val current = controller ?: return
        current.display = this
        needsFrame = true
        reportViewport()
        postInvalidateOnAnimation()
    }

    private fun detachController() {
        val current = controller ?: return
        if (current.display === this) current.display = null
    }

    private fun makeRenderer(): TerminalRenderer {
        val typefaces = TerminalFont.typefaces(resources, useNerdFont)
        val textSize = TerminalFont.SIZE_DP * density
        val metrics = TerminalMetrics.measure(TerminalPaints.base(typefaces.normal, textSize), density)
        return TerminalRenderer(metrics, TerminalPaints.create(typefaces, textSize, metrics.cellWidth), TerminalColorResolver(palette))
    }

    private fun sampleSize() {
        val usable = metrics.gridSize(width.toFloat(), height.toFloat())?.isUsable() == true
        when (val result = sizeLock.sample(IntSize(width, height), usable)) {
            is SizeLockResult.Pending -> {
                removeCallbacks(confirmSize)
                postDelayed(confirmSize, result.delayMillis)
            }

            SizeLockResult.Locked -> {
                clampOffsets()
                reportViewport()
                if (isFollowing() && !isInteracting()) revealCursor()
                placeCursorAboveKeyboard()
                invalidate()
            }

            SizeLockResult.Unchanged -> {
                Unit
            }
        }
    }

    private fun reportViewport() {
        val locked = sizeLock.locked ?: return
        val grid = metrics.gridSize(locked.width.toFloat(), locked.height.toFloat()) ?: return
        controller?.viewportDidChange(grid)
    }

    private fun pullFrame() {
        val current = controller ?: return
        if (!needsFrame || current.mode.value != TerminalMode.LIVE) return
        needsFrame = false
        if (displayedHistory != null) {
            displayedHistory = null
            updateSelection(null)
        }
        val frame = current.currentFrame() ?: return
        apply(frame, current)
    }

    private fun apply(
        frame: TerminalFrame,
        current: TerminalController,
    ) {
        val rowCount = max(frame.rows, frame.lines.size)
        lines = if (frame.lines.size >= rowCount) frame.lines else frame.lines + List(rowCount - frame.lines.size) { TerminalLine.EMPTY }
        if (columnCount != frame.columns) {
            updateSelection(null)
            columnCount = frame.columns
        }
        modes = frame.modes
        historyRows = frame.historyRows
        val column = frame.cursor.column.coerceIn(0, max(columnCount - 1, 0))
        cursorRect = metrics.cellRect(frame.cursor.row, column)
        cursor = frame.cursor.takeIf { it.visible }?.copy(column = column)
        clampOffsets()
        if (current.isFollowing.value && !isInteracting()) revealCursor()
        placeCursorAboveKeyboard()
    }

    private fun drawDocument(
        canvas: Canvas,
        rows: IntRange,
    ) {
        for (row in rows) renderer.drawBackgrounds(canvas, lines[row], row)
        selection?.let { current ->
            for (row in rows) current.columns(row, columnCount)?.let { renderer.drawSelection(canvas, it, row) }
        }
        for (row in rows) renderer.drawText(canvas, lines[row], row)
        drawCursor(canvas, rows)
    }

    private fun drawCursor(
        canvas: Canvas,
        rows: IntRange,
    ) {
        if (displayedHistory != null) return
        val current = cursor ?: return
        if (current.row !in rows) return
        val marked = markedText
        if (!marked.isNullOrEmpty()) {
            renderer.drawMarkedText(canvas, marked, current)
            return
        }
        renderer.drawCursor(canvas, current, lines.getOrNull(current.row))
    }

    private fun touchDown(event: MotionEvent) {
        stopMomentum()
        downX = event.x
        downY = event.y
        lastX = event.x
        lastY = event.y
        gesture = Gesture.PENDING
        if (isWiderThanTerminal()) parent?.requestDisallowInterceptTouchEvent(true)
        postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun touchMove(event: MotionEvent) {
        when (gesture) {
            Gesture.PENDING -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) {
                    beginDrag(Gesture.VERTICAL, event)
                } else if (abs(dx) > touchSlop) {
                    removeCallbacks(longPress)
                    gesture = if (isWiderThanTerminal()) Gesture.HORIZONTAL else Gesture.IGNORED
                    if (gesture == Gesture.HORIZONTAL) beginDrag(Gesture.HORIZONTAL, event)
                }
            }

            Gesture.VERTICAL -> {
                val delta = lastY - event.y
                lastX = event.x
                lastY = event.y
                route(delta)
            }

            Gesture.HORIZONTAL -> {
                val delta = lastX - event.x
                lastX = event.x
                scrollHorizontally(delta)
            }

            Gesture.SELECTING -> {
                val head = cell(event.x, event.y) ?: return
                selection?.let { updateSelection(it.copy(head = head)) }
            }

            Gesture.NONE, Gesture.IGNORED -> {
                Unit
            }
        }
    }

    private fun touchUp(tracker: VelocityTracker): Boolean {
        removeCallbacks(longPress)
        val tapped = gesture == Gesture.PENDING
        when (gesture) {
            Gesture.VERTICAL -> {
                tracker.computeCurrentVelocity(VELOCITY_UNITS)
                startMomentum(-tracker.yVelocity)
            }

            Gesture.HORIZONTAL -> {
                settleScrolling()
            }

            Gesture.SELECTING -> {
                showCopyMenu()
            }

            Gesture.PENDING, Gesture.NONE, Gesture.IGNORED -> {
                Unit
            }
        }
        endGesture()
        tapPending = tapped
        return tapped
    }

    private fun touchCancel() {
        removeCallbacks(longPress)
        when (gesture) {
            Gesture.VERTICAL, Gesture.HORIZONTAL -> {
                historyPullDistance = 0f
                settleScrolling()
            }

            Gesture.SELECTING -> {
                updateSelection(null)
            }

            Gesture.PENDING, Gesture.NONE, Gesture.IGNORED -> {
                Unit
            }
        }
        endGesture()
    }

    private fun endGesture() {
        gesture = Gesture.NONE
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun beginDrag(
        kind: Gesture,
        event: MotionEvent,
    ) {
        removeCallbacks(longPress)
        parent?.requestDisallowInterceptTouchEvent(true)
        gesture = kind
        lastX = event.x
        lastY = event.y
        dismissMenu()
        viewport.stopFollowingCursor()
        controller?.setFollowing(false)
        controller?.discardOutdatedHistoryPrefetch()
        historyPullDistance = 0f
        lineAccumulator = 0f
    }

    private fun beginSelection() {
        if (gesture != Gesture.PENDING) return
        val anchor = cell(downX, downY) ?: return
        gesture = Gesture.SELECTING
        parent?.requestDisallowInterceptTouchEvent(true)
        updateSelection(TerminalSelection(anchor, anchor))
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun route(delta: Float): Boolean {
        val current = controller ?: return false
        if (delta < 0f) prefetchHistory()
        val previousShift = viewport.viewportOffset
        val residual = viewport.consume(delta)
        val shiftMoved = previousShift != viewport.viewportOffset
        if (shiftMoved) invalidate()
        if (residual == 0f) return shiftMoved
        val forwarded = current.forwardScroll(-residual / density.toDouble())
        if (displayedHistory == null && (modes.mouseTracking || modes.alternateScroll)) {
            if (forwarded) return true
            return routeLines(residual, current) || shiftMoved
        }
        return scrollContent(residual) || forwarded || shiftMoved
    }

    private fun routeLines(
        delta: Float,
        current: TerminalController,
    ): Boolean {
        lineAccumulator += delta
        val count = (lineAccumulator / metrics.cellHeight).toInt()
        if (count == 0) return true
        lineAccumulator -= count * metrics.cellHeight
        val cell = cell(lastX, lastY) ?: return true
        val direction = if (count > 0) TerminalScrollDirection.DOWN else TerminalScrollDirection.UP
        repeat(abs(count)) { current.scroll(direction, cell) }
        return true
    }

    private fun scrollContent(delta: Float): Boolean {
        val previous = offsetY
        offsetY = (previous + delta).coerceIn(0f, maxOffsetY())
        if (displayedHistory == null && delta < 0f && offsetY == 0f) {
            historyPullDistance += max(0f, -(previous + delta))
            pullIntoHistory(historyPullDistance, canWait = true)
        } else {
            historyPullDistance = 0f
        }
        prefetchOlderHistory()
        reportHistoryTop()
        invalidate()
        return offsetY != previous
    }

    private fun scrollHorizontally(delta: Float) {
        offsetX = (offsetX + delta).coerceIn(0f, maxOffsetX())
        invalidate()
    }

    private fun startMomentum(velocity: Float) {
        if (!momentum.start(velocity, System.nanoTime())) {
            settleScrolling()
            return
        }
        Choreographer.getInstance().postFrameCallback(momentumFrame)
    }

    private fun stepMomentum() {
        if (!momentum.isActive) return
        val moved = route(momentum.step(System.nanoTime()))
        if (!moved || !momentum.isActive) {
            stopMomentum()
            settleScrolling()
            return
        }
        Choreographer.getInstance().postFrameCallback(momentumFrame)
    }

    private fun stopMomentum() {
        momentum.stop()
        Choreographer.getInstance().removeFrameCallback(momentumFrame)
    }

    private fun prefetchHistory() {
        if (displayedHistory != null || historyRows <= 0) return
        controller?.prefetchHistory()
    }

    private fun pullIntoHistory(
        distance: Float,
        canWait: Boolean,
    ) {
        val current = controller ?: return
        if (distance <= 0f) return
        prefetchHistory()
        current.enterPrefetchedHistory()?.let {
            show(it)
            return
        }
        if (!canWait || distance <= metrics.cellHeight * HISTORY_PULL_THRESHOLD_ROWS) return
        requestHistory(current)
    }

    private fun requestHistory(current: TerminalController) {
        if (isRequestingHistory || historyRows <= 0) return
        val launcher = scope ?: return
        isRequestingHistory = true
        launcher.launch {
            val document = current.enterHistory()
            isRequestingHistory = false
            document?.let(::show)
        }
    }

    private fun show(history: HistoryDocument) {
        displayedHistory = history
        updateSelection(null)
        lines = history.lines
        cursor = null
        offsetY = max(0f, offsetY + history.historyRowCount * metrics.cellHeight - historyPullDistance)
        historyPullDistance = 0f
        clampOffsets()
        reportHistoryTop()
        prefetchOlderHistory()
        invalidate()
    }

    private fun prefetchOlderHistory() {
        val history = displayedHistory ?: return
        val current = controller ?: return
        if (isLoadingOlderHistory || history.reachedStart) return
        if (offsetY >= metrics.cellHeight * OLDER_HISTORY_LOOKAHEAD_ROWS) return
        val launcher = scope ?: return
        isLoadingOlderHistory = true
        launcher.launch {
            val added = current.loadOlderHistory()
            isLoadingOlderHistory = false
            if (added <= 0 || displayedHistory !== history) return@launch
            lines = history.lines
            offsetY += added * metrics.cellHeight
            selection?.let { updateSelection(it.shifted(added)) }
            invalidate()
            prefetchOlderHistory()
        }
    }

    private fun settleScrolling() {
        val current = controller ?: return
        if (displayedHistory != null) {
            if (isScrolledToBottom()) current.returnToLive()
            return
        }
        if (isCursorInView()) current.setFollowing(true)
    }

    private fun reportHistoryTop() {
        if (displayedHistory == null) return
        controller?.setAtHistoryTop(offsetY <= metrics.cellHeight / 2)
    }

    private fun placeCursorAboveKeyboard() {
        if (sizeLock.locked == null || !viewport.needsCursorPlacement) return
        if (displayedHistory != null) {
            viewport.stopFollowingCursor()
            return
        }
        val rect = cursorRect ?: return
        viewport.placeCursor(rect.translate(-offsetX, -offsetY), height.toFloat())
    }

    private fun revealCursor() {
        if (displayedHistory != null) return
        val visible = visibleSize() ?: return
        val content = contentSize()
        val current = Offset(offsetX, offsetY)
        val rect = cursor?.let { metrics.cellRect(it.row, it.column) }
        val target =
            if (rect == null) {
                TerminalScrollMath.bottomOffset(visible, content, current)
            } else {
                TerminalScrollMath.offset(rect.inflate(metrics.cellWidth, 0f), visible, content, current)
            }
        offsetX = target.x
        offsetY = target.y
    }

    private fun Rect.inflate(
        horizontal: Float,
        vertical: Float,
    ): Rect = Rect(left - horizontal, top - vertical, right + horizontal, bottom + vertical)

    private fun isScrolledToBottom(): Boolean {
        val visible = visibleSize() ?: return true
        return TerminalScrollMath.isAtBottom(Offset(offsetX, offsetY), visible, contentSize(), metrics.cellHeight / 2)
    }

    private fun isCursorInView(): Boolean {
        val current = cursor ?: return isScrolledToBottom()
        return TerminalScrollMath.isVisible(
            metrics.cellRect(current.row, current.column),
            Offset(offsetX, offsetY + viewport.viewportOffset),
            Size(width.toFloat(), max(0f, height - viewport.keyboardOffset)),
            Size(metrics.cellWidth / 2, metrics.cellHeight / 2),
        )
    }

    private fun isFollowing(): Boolean = controller?.isFollowing?.value == true

    private fun isInteracting(): Boolean = gesture == Gesture.VERTICAL || gesture == Gesture.HORIZONTAL || momentum.isActive

    private fun isWiderThanTerminal(): Boolean {
        val locked = sizeLock.locked ?: return false
        return columnCount * metrics.cellWidth > locked.width + HALF_PIXEL
    }

    private fun visibleSize(): Size? = sizeLock.locked?.let { Size(it.width.toFloat(), it.height.toFloat()) }

    private fun contentSize(): Size = Size(columnCount * metrics.cellWidth, lines.size * metrics.cellHeight)

    private fun maxOffsetX(): Float = max(0f, contentSize().width - (visibleSize()?.width ?: 0f))

    private fun maxOffsetY(): Float = max(0f, contentSize().height - (visibleSize()?.height ?: 0f))

    private fun clampOffsets() {
        offsetX = offsetX.coerceIn(0f, maxOffsetX())
        offsetY = offsetY.coerceIn(0f, maxOffsetY())
    }

    private fun cell(
        x: Float,
        y: Float,
    ): TerminalCellPosition? {
        if (lines.isEmpty() || columnCount <= 0) return null
        val row = ((y + viewport.viewportOffset + offsetY) / metrics.cellHeight).toInt().coerceIn(0, lines.size - 1)
        val column = ((x + offsetX) / metrics.cellWidth).toInt().coerceIn(0, columnCount - 1)
        return TerminalCellPosition(row, column)
    }

    private fun updateSelection(value: TerminalSelection?) {
        if (selection == value) return
        val hadSelection = selection != null
        selection = value
        if (hadSelection != (value != null)) onSelectionChange?.invoke(value != null)
        invalidate()
    }

    private fun showCopyMenu() {
        if (selection == null) return
        dismissMenu()
        actionMode = startActionMode(CopyMenu(), ActionMode.TYPE_FLOATING)
    }

    private fun dismissMenu() {
        val mode = actionMode ?: return
        actionMode = null
        mode.finish()
    }

    private fun showKeyboard() {
        requestFocus()
        inputMethods.showSoftInput(this, 0)
    }

    private fun hideKeyboard() {
        inputMethods.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun isPasted(text: String): Boolean = text.any { it == '\n' || it == '\r' } && Graphemes.count(text) > 1

    private inner class CopyMenu : ActionMode.Callback2() {
        override fun onCreateActionMode(
            mode: ActionMode,
            menu: Menu,
        ): Boolean {
            menu.add(Menu.NONE, android.R.id.copy, Menu.NONE, COPY_TITLE)
            return true
        }

        override fun onPrepareActionMode(
            mode: ActionMode,
            menu: Menu,
        ): Boolean = false

        override fun onActionItemClicked(
            mode: ActionMode,
            item: MenuItem,
        ): Boolean {
            if (item.itemId != android.R.id.copy) return false
            copySelection()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            if (actionMode === mode) actionMode = null
        }

        override fun onGetContentRect(
            mode: ActionMode,
            view: View,
            outRect: android.graphics.Rect,
        ) {
            val current = selection ?: return super.onGetContentRect(mode, view, outRect)
            val start = metrics.cellRect(current.start.row, current.start.column)
            val end = metrics.cellRect(current.end.row, current.end.column)
            val dy = -offsetY - viewport.viewportOffset
            outRect.set(
                (min(start.left, end.left) - offsetX).toInt(),
                (start.top + dy).toInt(),
                (max(start.right, end.right) - offsetX).toInt(),
                (end.bottom + dy).toInt(),
            )
        }
    }

    private enum class Gesture {
        NONE,
        PENDING,
        VERTICAL,
        HORIZONTAL,
        SELECTING,
        IGNORED,
    }

    private companion object {
        const val ACCESSIBILITY_LABEL = "Terminal"
        const val COPY_TITLE = "Copy"
        const val HALF_PIXEL = 0.5f
        const val VELOCITY_UNITS = 1000
        const val HISTORY_PULL_THRESHOLD_ROWS = 2f
        const val OLDER_HISTORY_LOOKAHEAD_ROWS = 500f
        val DEFERRED_COMPOSITION_LANGUAGES = setOf("ja", "zh", "ko")
    }
}
