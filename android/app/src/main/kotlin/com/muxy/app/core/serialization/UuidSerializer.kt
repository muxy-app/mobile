package com.muxy.app.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.muxy.app.UUID", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: UUID,
    ) {
        encoder.encodeString(value.uuidString)
    }

    override fun deserialize(decoder: Decoder): UUID {
        val text = decoder.decodeString()
        return parseUuid(text) ?: throw SerializationException("Invalid UUID")
    }
}

val UUID.uuidString: String
    get() = toString().uppercase()

fun parseUuid(text: String): UUID? {
    if (text.length != CANONICAL_UUID_LENGTH) return null
    val uuid = runCatching { UUID.fromString(text) }.getOrNull() ?: return null
    return uuid.takeIf { it.toString().equals(text, ignoreCase = true) }
}

private const val CANONICAL_UUID_LENGTH = 36
