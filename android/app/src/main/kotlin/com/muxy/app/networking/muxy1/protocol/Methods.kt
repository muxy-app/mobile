package com.muxy.app.networking.muxy1.protocol

import com.muxy.app.models.Pairing
import com.muxy.app.models.Project
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

enum class Method(
    val wireName: String,
) {
    AUTHENTICATE_DEVICE("authenticateDevice"),
    PAIR_DEVICE("pairDevice"),
    LIST_PROJECTS("listProjects"),
    SELECT_PROJECT("selectProject"),
    GET_WORKSPACE("getWorkspace"),
    CREATE_TAB("createTab"),
    CLOSE_TAB("closeTab"),
    SELECT_TAB("selectTab"),
    GET_PROJECT_LOGO("getProjectLogo"),
}

object ResultType {
    const val PAIRING = "pairing"
    const val PROJECTS = "projects"
    const val WORKSPACE = "workspace"
    const val TAB = "tab"
    const val OK = "ok"
    const val PROJECT_LOGO = "projectLogo"
}

object EventName {
    const val WORKSPACE_CHANGED = "workspaceChanged"
    const val PROJECTS_CHANGED = "projectsChanged"
}

object EventType {
    const val WORKSPACE = "workspace"
    const val PROJECTS = "projects"
}

@Serializable
data class AuthParams(
    @SerialName("deviceID")
    val deviceId: String,
    val deviceName: String,
    val token: String,
)

@Serializable
data class PairingResult(
    @SerialName("clientID")
    val clientId: String,
    val deviceName: String,
) {
    val pairing: Pairing
        get() = Pairing(clientId, deviceName)
}

@Serializable
data class SelectProjectParams(
    @SerialName("projectID")
    val projectId: String,
)

@Serializable
data class GetWorkspaceParams(
    @SerialName("projectID")
    val projectId: String,
)

@Serializable
data class CreateTabParams(
    @SerialName("projectID")
    val projectId: String,
    @SerialName("areaID")
    val areaId: String?,
    val kind: String?,
)

@Serializable
data class CloseTabParams(
    @SerialName("projectID")
    val projectId: String,
    @SerialName("areaID")
    val areaId: String,
    @SerialName("tabID")
    val tabId: String,
)

@Serializable
data class SelectTabParams(
    @SerialName("projectID")
    val projectId: String,
    @SerialName("areaID")
    val areaId: String,
    @SerialName("tabID")
    val tabId: String,
)

@Serializable
data class GetProjectLogoParams(
    @SerialName("projectID")
    val projectId: String,
)

@Serializable
data class ProjectLogoResult(
    @SerialName("projectID")
    val projectId: String,
    val pngData: String,
)

@Serializable
class EmptyResult

@Serializable(with = ProjectsResultSerializer::class)
data class ProjectsResult(
    val projects: List<Project>,
)

object ProjectsResultSerializer : KSerializer<ProjectsResult> {
    private const val PROJECTS_KEY = "projects"
    private val listSerializer = ListSerializer(Project.serializer())

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): ProjectsResult {
        val json = decoder as? JsonDecoder ?: throw SerializationException("ProjectsResult needs JSON")
        val element = json.decodeJsonElement()
        val projects = (element as? JsonObject)?.get(PROJECTS_KEY) ?: element
        return ProjectsResult(json.json.decodeFromJsonElement(listSerializer, projects))
    }

    override fun serialize(
        encoder: Encoder,
        value: ProjectsResult,
    ) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("ProjectsResult needs JSON")
        json.encodeJsonElement(buildJsonObject { put(PROJECTS_KEY, json.json.encodeToJsonElement(listSerializer, value.projects)) })
    }
}
