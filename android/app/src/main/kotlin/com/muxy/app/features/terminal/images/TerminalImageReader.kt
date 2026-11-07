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

    suspend fun read(uris: List<Uri>): List<TerminalImage> = withContext(Dispatchers.IO) { uris.mapNotNull(::read) }

    private fun read(uri: Uri): TerminalImage? {
        val bytes = readBytes(uri) ?: return null
        return TerminalImage.sentAsIs(bytes) ?: convertedToJpeg(bytes)
    }

    private fun readBytes(uri: Uri): ByteArray? =
        try {
            resolver.openInputStream(uri)?.use { it.readAtMost(TerminalImage.MAXIMUM_BYTES) }
        } catch (error: IOException) {
            Log.terminal.error("Reading an image failed", error)
            null
        } catch (error: SecurityException) {
            Log.terminal.error("Reading an image was not allowed", error)
            null
        }

    private fun convertedToJpeg(bytes: ByteArray): TerminalImage? =
        try {
            val bitmap =
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            bitmap.recycle()
            TerminalImage.jpeg(output.toByteArray())
        } catch (error: IOException) {
            Log.terminal.error("Converting an image failed", error)
            null
        }

    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) {
                Log.terminal.error("An image was larger than ${TerminalImage.MAXIMUM_BYTES} bytes")
                return null
            }
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val JPEG_QUALITY = 90
        const val BUFFER_BYTES = 64 * 1024
    }
}
