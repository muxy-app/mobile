@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.networking.muxy1.protocol

import com.muxy.app.core.serialization.Base64ByteArraySerializer
import com.muxy.app.core.serialization.UuidSerializer
import com.muxy.app.core.serialization.parseUuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Serializable
data class TakeOverPaneParams(
    @SerialName("paneID")
    val paneId: String,
    val cols: Int,
    val rows: Int,
)

@Serializable
data class ReleasePaneParams(
    @SerialName("paneID")
    val paneId: String,
)

@Serializable
data class TerminalResizeParams(
    @SerialName("paneID")
    val paneId: String,
    val cols: Int,
    val rows: Int,
)

@Serializable
data class TerminalScrollParams(
    @SerialName("paneID")
    val paneId: String,
    val deltaX: Double,
    val deltaY: Double,
    val precise: Boolean,
)

@Serializable
class TerminalInputParams(
    @SerialName("paneID")
    val paneId: String,
    @Serializable(with = Base64ByteArraySerializer::class)
    val bytes: ByteArray,
)

@Serializable
data class SetClientThemeParams(
    val theme: ClientTerminalTheme?,
)

@Serializable
data class ClientTerminalTheme(
    val fg: Int,
    val bg: Int,
    val palette: List<Int>,
    val cursorColor: Int?,
    val cursorText: Int?,
    val selectionBackground: Int?,
    val selectionForeground: Int?,
)

@Serializable
class TerminalBytesEvent(
    @SerialName("paneID")
    val paneId: UUID,
    @Serializable(with = Base64ByteArraySerializer::class)
    val bytes: ByteArray,
) {
    companion object {
        private const val PANE_KEY = "paneID"

        fun paneId(data: RawTagged): UUID? =
            (data.value as? JsonObject)
                ?.get(PANE_KEY)
                ?.jsonPrimitive
                ?.contentOrNull
                ?.let(::parseUuid)
    }
}

@Serializable
data class PaneOwnershipEvent(
    @SerialName("paneID")
    val paneId: UUID,
    val owner: PaneOwner,
)

@Serializable(with = PaneOwnerSerializer::class)
sealed interface PaneOwner {
    val deviceName: String

    data class Mac(
        override val deviceName: String,
    ) : PaneOwner

    data class Remote(
        val deviceId: UUID,
        override val deviceName: String,
    ) : PaneOwner
}

object PaneOwnerSerializer : KSerializer<PaneOwner> {
    private const val MAC_KEY = "mac"
    private const val REMOTE_KEY = "remote"

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.muxy.app.PaneOwner")

    override fun deserialize(decoder: Decoder): PaneOwner {
        val json = decoder as? JsonDecoder ?: throw SerializationException("PaneOwner needs JSON")
        val owner = json.decodeJsonElement() as? JsonObject ?: throw SerializationException("PaneOwner must be an object")
        owner[MAC_KEY]?.let { return PaneOwner.Mac(json.json.decodeFromJsonElement(MacOwner.serializer(), it).deviceName) }
        owner[REMOTE_KEY]?.let { element ->
            val remote = json.json.decodeFromJsonElement(RemoteOwner.serializer(), element)
            return PaneOwner.Remote(remote.deviceId, remote.deviceName)
        }
        throw SerializationException("Unknown pane owner")
    }

    override fun serialize(
        encoder: Encoder,
        value: PaneOwner,
    ) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("PaneOwner needs JSON")
        val owner =
            when (value) {
                is PaneOwner.Mac -> {
                    buildJsonObject { put(MAC_KEY, json.json.encodeToJsonElement(MacOwner.serializer(), MacOwner(value.deviceName))) }
                }

                is PaneOwner.Remote -> {
                    val remote = RemoteOwner(value.deviceId, value.deviceName)
                    buildJsonObject { put(REMOTE_KEY, json.json.encodeToJsonElement(RemoteOwner.serializer(), remote)) }
                }
            }
        json.encodeJsonElement(owner)
    }

    @Serializable
    private data class MacOwner(
        val deviceName: String,
    )

    @Serializable
    private data class RemoteOwner(
        @SerialName("deviceID")
        val deviceId: UUID,
        val deviceName: String,
    )
}
