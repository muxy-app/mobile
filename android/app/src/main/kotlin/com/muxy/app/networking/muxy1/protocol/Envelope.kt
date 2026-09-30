package com.muxy.app.networking.muxy1.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val ProtocolJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

@Serializable
data class RawTagged(
    val type: String,
    val value: JsonElement = JsonNull,
) {
    fun <T> decode(deserializer: DeserializationStrategy<T>): T = ProtocolJson.decodeFromJsonElement(deserializer, value)

    inline fun <reified T> decode(): T = ProtocolJson.decodeFromJsonElement(value)

    companion object {
        inline fun <reified T> of(
            type: String,
            value: T,
        ): RawTagged = RawTagged(type, ProtocolJson.encodeToJsonElement(value))
    }
}

@Serializable
data class ResponseEnvelope(
    val id: String,
    val result: RawTagged? = null,
    val error: ProtocolErrorBody? = null,
)

@Serializable
data class EventEnvelope(
    val event: String,
    val data: RawTagged? = null,
)

object RequestEnvelope {
    fun encode(
        id: String,
        method: Method,
        params: JsonElement?,
    ): String {
        val payload =
            buildJsonObject {
                put("id", JsonPrimitive(id))
                put("method", JsonPrimitive(method.wireName))
                put("params", params?.let { tagged(method, it) } ?: JsonNull)
            }
        val root =
            buildJsonObject {
                put("type", JsonPrimitive("request"))
                put("payload", payload)
            }
        return ProtocolJson.encodeToString(JsonElement.serializer(), root)
    }

    private fun tagged(
        method: Method,
        params: JsonElement,
    ): JsonElement =
        buildJsonObject {
            put("type", JsonPrimitive(method.wireName))
            put("value", params)
        }
}

class FrameException(
    val type: String?,
) : SerializationException("Unknown frame type $type")

sealed interface IncomingFrame {
    data class Response(
        val envelope: ResponseEnvelope,
    ) : IncomingFrame

    data class Event(
        val envelope: EventEnvelope,
    ) : IncomingFrame

    companion object {
        fun parse(text: String): IncomingFrame {
            val root = ProtocolJson.parseToJsonElement(text).jsonObject
            val payload = root["payload"] ?: throw SerializationException("Missing payload")
            return when (val type = root["type"]?.jsonPrimitive?.content) {
                "response" -> Response(ProtocolJson.decodeFromJsonElement(ResponseEnvelope.serializer(), payload))
                "event" -> Event(ProtocolJson.decodeFromJsonElement(EventEnvelope.serializer(), payload))
                else -> throw FrameException(type)
            }
        }
    }
}
