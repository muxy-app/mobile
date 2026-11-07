package com.muxy.app.features.terminal.images

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.DragEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import com.muxy.app.core.logging.Log
import com.muxy.app.features.terminal.SystemTerminalClipboard
import com.muxy.app.features.terminal.TerminalController

class TerminalImageIntake(
    private val context: Context,
    private val clipboard: SystemTerminalClipboard,
) {
    private val reader = TerminalImageReader(context)

    fun send(
        uris: List<Uri>,
        controller: TerminalController,
        afterReading: () -> Unit = {},
    ) {
        controller.sendImages {
            try {
                reader.read(uris)
            } finally {
                afterReading()
            }
        }
    }

    fun pasteClipboard(controller: TerminalController): Boolean {
        if (!controller.acceptsImages || !clipboard.hasImages()) return false
        send(clipboard.imageUris(), controller)
        return true
    }

    fun acceptsDrag(
        description: ClipDescription?,
        controller: TerminalController?,
    ): Boolean = controller?.acceptsImages == true && description?.hasMimeType(IMAGE_MIME_TYPE) == true

    fun drop(
        event: DragEvent,
        controller: TerminalController?,
    ): Boolean {
        if (controller == null || !acceptsDrag(event.clipDescription, controller)) return false
        val uris = event.clipData?.uris().orEmpty()
        if (uris.isEmpty()) return false
        val permissions = context.activity()?.requestDragAndDropPermissions(event)
        send(uris, controller) { permissions?.release() }
        return true
    }

    fun receiveKeyboardImage(
        content: InputContentInfo,
        flags: Int,
        controller: TerminalController,
    ): Boolean {
        if (!content.description.hasMimeType(IMAGE_MIME_TYPE)) return false
        if (flags and InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
            try {
                content.requestPermission()
            } catch (error: SecurityException) {
                Log.terminal.error("The keyboard's image could not be opened", error)
                return false
            }
        }
        send(listOf(content.contentUri), controller) { content.releasePermission() }
        return true
    }

    private fun ClipData.uris(): List<Uri> = (0 until itemCount).mapNotNull { getItemAt(it).uri }

    private tailrec fun Context.activity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.activity()
            else -> null
        }

    companion object {
        private const val IMAGE_MIME_TYPE = "image/*"
        val IMAGE_MIME_TYPES = arrayOf(IMAGE_MIME_TYPE)
    }
}
