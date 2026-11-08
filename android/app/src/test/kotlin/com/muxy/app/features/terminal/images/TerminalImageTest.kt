package com.muxy.app.features.terminal.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TerminalImageTest {
    @Test
    fun formatsAiToolsReadAreSentAsIs() {
        val samples =
            mapOf(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0) to "png",
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) to "jpg",
                "GIF89a...".toByteArray() to "gif",
                "GIF87a...".toByteArray() to "gif",
                "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray() to "webp",
            )
        samples.forEach { (bytes, fileExtension) ->
            val image = TerminalImage.sentAsIs(bytes)
            assertEquals(fileExtension, image?.fileExtension)
            assertEquals("pasted-image.$fileExtension", image?.fileName)
        }
    }

    @Test
    fun otherFormatsNeedConverting() {
        val heic = byteArrayOf(0, 0, 0, 0x18) + "ftypheic".toByteArray()
        assertNull(TerminalImage.sentAsIs(heic))
        assertNull(TerminalImage.sentAsIs("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray()))
        assertNull(TerminalImage.sentAsIs(byteArrayOf(0x89.toByte(), 0x50)))
        assertNull(TerminalImage.sentAsIs(ByteArray(0)))
        assertEquals("jpg", TerminalImage.jpeg(heic).fileExtension)
    }

    @Test
    fun imagesOverTheUploadLimitAreTooLarge() {
        assertFalse(TerminalImage.jpeg(ByteArray(TerminalImage.MAXIMUM_BYTES)).isTooLarge)
        assertTrue(TerminalImage.jpeg(ByteArray(TerminalImage.MAXIMUM_BYTES + 1)).isTooLarge)
    }

    @Test
    fun readingStopsPastTheLimit() {
        val bytes = ByteArray(200_000) { it.toByte() }
        assertTrue(ByteArrayInputStream(bytes).readAtMost(200_000).contentEquals(bytes))
        assertNull(ByteArrayInputStream(bytes).readAtMost(199_999))
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readAtMost(10)?.size)
    }
}
