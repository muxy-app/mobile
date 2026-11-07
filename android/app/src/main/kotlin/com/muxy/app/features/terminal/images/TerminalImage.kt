package com.muxy.app.features.terminal.images

class TerminalImage private constructor(
    val bytes: ByteArray,
    val fileExtension: String,
) {
    val fileName: String
        get() = "pasted-image.$fileExtension"

    val isTooLarge: Boolean
        get() = bytes.size > MAXIMUM_BYTES

    companion object {
        const val MAXIMUM_BYTES = 100 * 1024 * 1024

        fun sentAsIs(bytes: ByteArray): TerminalImage? = SentFormat.detect(bytes)?.let { TerminalImage(bytes, it.fileExtension) }

        fun jpeg(bytes: ByteArray): TerminalImage = TerminalImage(bytes, SentFormat.JPEG.fileExtension)
    }

    private enum class SentFormat(
        val fileExtension: String,
        private val matches: (ByteArray) -> Boolean,
    ) {
        PNG("png", { it.startsWith(PNG_SIGNATURE) }),
        JPEG("jpg", { it.startsWith(JPEG_SIGNATURE) }),
        GIF("gif", { it.startsWith(GIF87_SIGNATURE) || it.startsWith(GIF89_SIGNATURE) }),
        WEBP("webp", { it.startsWith(RIFF_SIGNATURE) && it.startsWith(WEBP_SIGNATURE, offset = WEBP_OFFSET) }),
        ;

        companion object {
            fun detect(bytes: ByteArray): SentFormat? = entries.firstOrNull { it.matches(bytes) }
        }
    }
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val GIF87_SIGNATURE = "GIF87a".toByteArray(Charsets.US_ASCII)
private val GIF89_SIGNATURE = "GIF89a".toByteArray(Charsets.US_ASCII)
private val RIFF_SIGNATURE = "RIFF".toByteArray(Charsets.US_ASCII)
private val WEBP_SIGNATURE = "WEBP".toByteArray(Charsets.US_ASCII)
private const val WEBP_OFFSET = 8

private fun ByteArray.startsWith(
    prefix: ByteArray,
    offset: Int = 0,
): Boolean {
    if (size < offset + prefix.size) return false
    return prefix.indices.all { this[offset + it] == prefix[it] }
}
