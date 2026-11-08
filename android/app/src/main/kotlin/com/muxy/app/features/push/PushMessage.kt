package com.muxy.app.features.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class PushMessage(
    @SerialName("v") val version: Int,
    @SerialName("server_id") val serverId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("session_id") val sessionId: ULong,
    @SerialName("event_id") val eventId: ULong,
    val kind: String,
    val title: String,
    val body: String,
    @SerialName("ts") val timestamp: Long,
) {
    val target: PushTarget
        get() = PushTarget(serverId, sessionId, projectId, eventId)

    companion object {
        const val SUPPORTED_VERSION = 1
    }
}

@Serializable
data class PushTarget(
    val serverId: String,
    val sessionId: ULong,
    val projectId: String,
    val eventId: ULong,
)

object PushOpener {
    private val json = Json { ignoreUnknownKeys = true }

    fun open(
        encoded: String,
        keys: List<PushKey>,
    ): PushMessage? {
        val payload = PushPayload.parse(encoded) ?: return null
        val key = keys.firstOrNull { it.keyId.contentEquals(payload.keyId) } ?: return null
        val plaintext = payload.open(key) ?: return null
        val message =
            try {
                json.decodeFromString(PushMessage.serializer(), plaintext.toString(Charsets.UTF_8))
            } catch (error: SerializationException) {
                return null
            } catch (error: IllegalArgumentException) {
                return null
            }
        if (message.version != PushMessage.SUPPORTED_VERSION || message.serverId != key.serverId) return null
        return message
    }
}
