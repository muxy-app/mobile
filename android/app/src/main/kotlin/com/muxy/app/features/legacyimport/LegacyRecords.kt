package com.muxy.app.features.legacyimport

import com.muxy.app.core.logging.Log
import com.muxy.app.core.serialization.parseUuid
import com.muxy.app.core.validation.ConnectionInputValidator
import com.muxy.app.core.validation.InputValidation
import com.muxy.app.models.Connection
import com.muxy.app.models.PairingState
import com.muxy.app.persistence.settings.AppSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.util.UUID

internal object LegacyRecords {
    private val json = Json { ignoreUnknownKeys = true }
    private val validator = ConnectionInputValidator()
    private val demoId = UUID.fromString("00000000-0000-0000-0000-0000000000de")

    fun state(encoded: String?): JsonObject? {
        if (encoded == null) return null
        val envelope = json.parseToJsonElement(encoded) as? JsonObject ?: error("Invalid legacy envelope")
        require((envelope["version"] as? JsonPrimitive)?.intOrNull == 0)
        return envelope["state"] as? JsonObject ?: error("Invalid legacy state")
    }

    fun installDeviceId(state: JsonObject?): String? =
        (state?.get("installDeviceID") as? JsonPrimitive)?.contentOrNull?.takeIf { parseUuid(it) != null }

    fun devices(state: JsonObject?): List<LegacyDevice> =
        (state?.get("devices") as? JsonArray)
            .orEmpty()
            .mapNotNull { element ->
                try {
                    val device = json.decodeFromJsonElement(LegacyDevice.serializer(), element)
                    if (device.connection()?.id == demoId) return@mapNotNull null
                    require(device.connection() != null)
                    device
                } catch (error: Exception) {
                    Log.persistence.error("Skipping a legacy device: ${error.javaClass.simpleName}")
                    null
                }
            }.distinctBy { it.connection()?.id }

    fun settings(state: JsonObject): LegacySettings = json.decodeFromJsonElement(LegacySettings.serializer(), state)

    fun workspaces(state: JsonObject?): Map<UUID, UUID> =
        (state?.get("selectedWorkspaceIDs") as? JsonObject)
            .orEmpty()
            .mapNotNull { (connection, workspace) ->
                val connectionId = parseUuid(connection) ?: return@mapNotNull null
                val workspaceId = (workspace as? JsonPrimitive)?.contentOrNull?.let(::parseUuid) ?: return@mapNotNull null
                connectionId to workspaceId
            }.toMap()

    @Serializable
    data class LegacyDevice(
        val id: String,
        val label: String,
        val host: String,
        val port: Int,
        val serviceName: String? = null,
        val needsRepair: Boolean = false,
    ) {
        fun connection(): Connection? {
            val connectionId = parseUuid(id) ?: return null
            val input = validator.validate(label, host, port.toString()) as? InputValidation.Valid ?: return null
            return Connection(
                id = connectionId,
                name = input.value.name,
                host = input.value.host,
                port = input.value.port,
                serviceName = serviceName,
                pairingState = PairingState.PAIRED,
            )
        }
    }

    @Serializable
    data class LegacySettings(
        val useNerdFont: Boolean? = null,
        val autoFocusTerminal: Boolean? = null,
        val hasOnboarded: Boolean? = null,
        val demoMode: Boolean? = null,
    ) {
        fun applyingTo(settings: AppSettings): AppSettings =
            settings.copy(
                useNerdFont = useNerdFont ?: settings.useNerdFont,
                autoFocusTerminal = autoFocusTerminal ?: settings.autoFocusTerminal,
                hasCompletedOnboarding = hasOnboarded ?: settings.hasCompletedOnboarding,
                demoMode = demoMode ?: settings.demoMode,
            )
    }
}
