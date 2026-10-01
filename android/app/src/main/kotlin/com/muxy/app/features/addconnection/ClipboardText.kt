package com.muxy.app.features.addconnection

import android.content.ClipboardManager
import android.content.Context

class ClipboardText(
    context: Context,
) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(ClipboardManager::class.java)

    fun text(): String? {
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }
}
