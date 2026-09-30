@file:UseSerializers(UuidSerializer::class)

package com.muxy.app.models

import com.muxy.app.core.serialization.UuidSerializer
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Serializable
enum class SplitDirection {
    @SerialName("horizontal")
    HORIZONTAL,

    @SerialName("vertical")
    VERTICAL,
}

@Serializable
data class TabArea(
    val id: UUID,
    val projectPath: String,
    val tabs: List<Tab>,
    @SerialName("activeTabID")
    val activeTabId: UUID? = null,
)

@Serializable
data class WorkspaceSplit(
    val id: UUID,
    val direction: SplitDirection,
    val ratio: Double,
    val first: WorkspaceNode,
    val second: WorkspaceNode,
)

@Serializable(with = WorkspaceNodeSerializer::class)
sealed interface WorkspaceNode {
    data class Area(
        val area: TabArea,
    ) : WorkspaceNode

    data class Split(
        val split: WorkspaceSplit,
    ) : WorkspaceNode
}

@Serializable
data class Workspace(
    @SerialName("projectID")
    val projectId: UUID,
    @SerialName("worktreeID")
    val worktreeId: UUID,
    @SerialName("focusedAreaID")
    val focusedAreaId: UUID? = null,
    val root: WorkspaceNode,
)

object WorkspaceNodeSerializer : KSerializer<WorkspaceNode> {
    private const val TYPE_KEY = "type"
    private const val TAB_AREA = "tabArea"
    private const val SPLIT = "split"

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.muxy.app.WorkspaceNode")

    override fun deserialize(decoder: Decoder): WorkspaceNode {
        val json = decoder as? JsonDecoder ?: throw SerializationException("WorkspaceNode needs JSON")
        val node = json.decodeJsonElement().jsonObject
        return when (val type = node[TYPE_KEY]?.jsonPrimitive?.content) {
            TAB_AREA -> WorkspaceNode.Area(json.json.decodeFromJsonElement(TabArea.serializer(), node.required(TAB_AREA)))
            SPLIT -> WorkspaceNode.Split(json.json.decodeFromJsonElement(WorkspaceSplit.serializer(), node.required(SPLIT)))
            else -> throw SerializationException("Unknown node type $type")
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: WorkspaceNode,
    ) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("WorkspaceNode needs JSON")
        val node =
            when (value) {
                is WorkspaceNode.Area -> {
                    buildJsonObject {
                        put(TYPE_KEY, JsonPrimitive(TAB_AREA))
                        put(TAB_AREA, json.json.encodeToJsonElement(TabArea.serializer(), value.area))
                    }
                }

                is WorkspaceNode.Split -> {
                    buildJsonObject {
                        put(TYPE_KEY, JsonPrimitive(SPLIT))
                        put(SPLIT, json.json.encodeToJsonElement(WorkspaceSplit.serializer(), value.split))
                    }
                }
            }
        json.encodeJsonElement(node)
    }

    private fun JsonObject.required(key: String) = get(key) ?: throw SerializationException("Missing $key")
}
