@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.models

import com.muxy.app.core.serialization.UuidSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

@Serializable(with = TabKindSerializer::class)
sealed interface TabKind {
    val rawValue: String

    data object Terminal : TabKind {
        override val rawValue = "terminal"
    }

    data object Vcs : TabKind {
        override val rawValue = "vcs"
    }

    data class Unsupported(
        override val rawValue: String,
    ) : TabKind

    companion object {
        fun of(rawValue: String): TabKind =
            when (rawValue) {
                Terminal.rawValue -> Terminal
                Vcs.rawValue -> Vcs
                else -> Unsupported(rawValue)
            }
    }
}

object TabKindSerializer : KSerializer<TabKind> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.muxy.app.TabKind", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: TabKind,
    ) {
        encoder.encodeString(value.rawValue)
    }

    override fun deserialize(decoder: Decoder): TabKind = TabKind.of(decoder.decodeString())
}

@Serializable
data class Tab(
    val id: UUID,
    val kind: TabKind,
    val title: String,
    val isPinned: Boolean,
    @SerialName("paneID")
    val paneId: UUID? = null,
)
