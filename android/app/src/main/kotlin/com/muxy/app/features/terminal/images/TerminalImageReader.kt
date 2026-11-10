package com.muxy.app.features.terminal.images

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

class TerminalImageReader(
    context: Context,
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    suspend fun read(uris: List<Uri>): List<TerminalImageRead> = withContext(Dispatchers.IO) { uris.map(::read) }

    private fun read(uri: Uri): TerminalImageRead {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            Log.terminal.error("Ignored an image that isn't shared content")
            return TerminalImageRead.Unreadable
        }
        val bytes =
            try {
                val stream = resolver.openInputStream(uri) ?: return TerminalImageRead.Unreadable
                stream.use { it.readAtMost(TerminalImage.MAXIMUM_BYTES) } ?: return TerminalImageRead.TooLarge
            } catch (error: IOException) {
                Log.terminal.error("Reading an image failed", error)
                return TerminalImageRead.Unreadable
            } catch (error: SecurityException) {
                Log.terminal.error("Reading an image was not allowed", error)
                return TerminalImageRead.Unreadable
            }
        val image = TerminalImage.sentAsIs(bytes) ?: convertedToJpeg(bytes) ?: return TerminalImageRead.Unreadable
        return TerminalImageRead.Loaded(image)
    }

    private fun convertedToJpeg(bytes: ByteArray): TerminalImage? =
        try {
            val bitmap =
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            val output = ByteArrayOutputStream()
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            bitmap.recycle()
            output.toByteArray().takeIf { compressed && it.isNotEmpty() }?.let(TerminalImage::jpeg)
        } catch (error: IOException) {
            Log.terminal.error("Converting an image failed", error)
            null
        }

    private companion object {
        const val JPEG_QUALITY = 90
    }
}

internal fun InputStream.readAtMost(limit: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_BYTES)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > limit) return null
        output.write(buffer, 0, count)
    }
}

private const val READ_BUFFER_BYTES = 64 * 1024
