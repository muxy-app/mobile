package com.muxy.app.features.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun interface TerminalClipboard {
    fun copy(text: String)
}

class SystemTerminalClipboard(
    context: Context,
) : TerminalClipboard {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(ClipboardManager::class.java)

    override fun copy(text: String) {
        manager.setPrimaryClip(ClipData.newPlainText(LABEL, text))
    }

    fun text(): String? {
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    private companion object {
        const val LABEL = "Terminal"
    }
}
