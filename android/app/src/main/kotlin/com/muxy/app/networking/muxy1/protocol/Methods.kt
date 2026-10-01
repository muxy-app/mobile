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
    TAKE_OVER_PANE("takeOverPane"),
    RELEASE_PANE("releasePane"),
    SET_CLIENT_THEME("setClientTheme"),
    TERMINAL_INPUT("terminalInput"),
    TERMINAL_RESIZE("terminalResize"),
    TERMINAL_SCROLL("terminalScroll"),
    LIST_WORKTREES("listWorktrees"),
    SELECT_WORKTREE("selectWorktree"),
    FILES_LIST("filesList"),
    FILES_STAT("filesStat"),
    FILES_READ("filesRead"),
    FILES_WRITE("filesWrite"),
    FILES_MKDIR("filesMkdir"),
    FILES_RENAME("filesRename"),
    FILES_MOVE("filesMove"),
    FILES_DELETE("filesDelete"),
    VCS_REFRESH("vcsRefresh"),
    VCS_LIST_BRANCHES("vcsListBranches"),
    VCS_GET_DIFF("vcsGetDiff"),
    VCS_COMMIT("vcsCommit"),
    VCS_PULL("vcsPull"),
    VCS_PUSH("vcsPush"),
    VCS_SWITCH_BRANCH("vcsSwitchBranch"),
    VCS_CREATE_BRANCH("vcsCreateBranch"),
    VCS_CREATE_PR("vcsCreatePR"),
    VCS_MERGE_PULL_REQUEST("vcsMergePullRequest"),
    VCS_ADD_WORKTREE("vcsAddWorktree"),
    VCS_REMOVE_WORKTREE("vcsRemoveWorktree"),
}

object ResultType {
    const val PAIRING = "pairing"
    const val PROJECTS = "projects"
    const val WORKSPACE = "workspace"
    const val TAB = "tab"
    const val OK = "ok"
    const val PROJECT_LOGO = "projectLogo"
    const val WORKTREES = "worktrees"
    const val FILES = "files"
    const val FILE_STAT = "fileStat"
    const val FILE_CONTENT = "fileContent"
    const val FILE_PATHS = "filePaths"
    const val VCS_STATUS = "vcsStatus"
    const val VCS_BRANCHES = "vcsBranches"
    const val VCS_DIFF = "vcsDiff"
    const val VCS_PR_CREATED = "vcsPRCreated"
}

object EventName {
    const val WORKSPACE_CHANGED = "workspaceChanged"
    const val PROJECTS_CHANGED = "projectsChanged"
    const val TERMINAL_OUTPUT = "terminalOutput"
    const val TERMINAL_SNAPSHOT = "terminalSnapshot"
    const val PANE_OWNERSHIP_CHANGED = "paneOwnershipChanged"
    const val FILE_CHANGED = "fileChanged"
}

object EventType {
    const val WORKSPACE = "workspace"
    const val PROJECTS = "projects"
    const val TERMINAL_OUTPUT = "terminalOutput"
    const val TERMINAL_SNAPSHOT = "terminalSnapshot"
    const val PANE_OWNERSHIP = "paneOwnership"
    const val FILE_CHANGED = "fileChanged"
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
