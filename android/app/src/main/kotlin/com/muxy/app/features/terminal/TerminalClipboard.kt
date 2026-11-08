package com.muxy.app.features.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.muxy.app.features.terminal.images.TerminalImage

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

    fun hasImages(): Boolean = manager.primaryClipDescription?.hasMimeType(TerminalImage.MIME_TYPE) == true

    fun imageUris(): List<Uri> {
        if (!hasImages()) return emptyList()
        val clip = manager.primaryClip ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }

    private companion object {
        const val LABEL = "Terminal"
    }
}
