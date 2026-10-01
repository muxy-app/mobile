package com.muxy.app.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64

object Base64ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.muxy.app.Base64Bytes", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ByteArray,
    ) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val text = decoder.decodeString()
        return try {
            Base64.getDecoder().decode(text)
        } catch (error: IllegalArgumentException) {
            throw SerializationException("Invalid base64", error)
        }
    }
}
