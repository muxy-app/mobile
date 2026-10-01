package com.muxy.app.features.terminal

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muxy.app.features.terminalkit.TerminalAccessoryActions
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import com.muxy.app.features.terminalkit.TerminalModifier

@Stable
class TerminalSurfaceHandle(
    private val controller: TerminalController,
) : TerminalAccessoryActions {
    var canCopy by mutableStateOf(false)
        private set

    var keyboardVisible = false

    var view: TerminalSurfaceView? = null
        private set

    private var surfaceBottom: Float? = null
    private var barTop: Float? = null

    fun attach(view: TerminalSurfaceView) {
        this.view = view
        view.onSelectionChange = { canCopy = it }
        updateOcclusion()
    }

    fun release(view: TerminalSurfaceView) {
        view.onSelectionChange = null
        if (this.view !== view) return
        this.view = null
        canCopy = false
    }

    fun surfacePlaced(bottom: Float) {
        surfaceBottom = bottom
        updateOcclusion()
    }

    fun barPlaced(top: Float) {
        barTop = top
        updateOcclusion()
    }

    override fun key(key: TerminalKey) {
        view?.press(TerminalKeyStroke(key))
    }

    override fun text(text: String) {
        view?.sendAccessoryText(text)
    }

    override fun paste() {
        view?.pasteFromClipboard()
    }

    override fun copy() {
        view?.copySelection()
    }

    override fun toggleModifier() {
        controller.toggleModifier()
    }

    override fun selectModifier(modifier: TerminalModifier) {
        controller.selectModifier(modifier)
    }

    override fun toggleKeyboard() {
        view?.toggleKeyboard(keyboardVisible)
    }

    private fun updateOcclusion() {
        val bottom = surfaceBottom ?: return
        val top = barTop ?: return
        view?.setKeyboardOcclusion((bottom - top).coerceAtLeast(0f))
    }
}
