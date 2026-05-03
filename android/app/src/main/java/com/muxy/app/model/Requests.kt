package com.muxy.app.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement

/**
 * Request builders for Phase 2 methods. Each one wraps params in the
 * { type, value } shape that Swift's MuxyParams encoder produces.
 */

private fun emptyParams(typeName: String) = TaggedValue(type = typeName, value = null)

private inline fun <reified T> taggedParams(typeName: String, params: T, serializer: kotlinx.serialization.KSerializer<T>) =
    TaggedValue(type = typeName, value = MuxyJson.encodeToJsonElement(serializer, params))

private fun req(id: String, method: String, params: TaggedValue?) =
    MuxyMessage.Request(MuxyRequest(id = id, method = method, params = params))

@Serializable data class ProjectIDParams(val projectID: String)
@Serializable data class SelectWorktreeParams(val projectID: String, val worktreeID: String)
@OptIn(ExperimentalSerializationApi::class)
@Serializable data class CreateTabParams(
    val projectID: String,
    val areaID: String? = null,
    @EncodeDefault val kind: TabKindDTO = TabKindDTO.TERMINAL,
)
@Serializable data class TabRefParams(val projectID: String, val areaID: String, val tabID: String)
@Serializable data class VCSSwitchBranchParams(val projectID: String, val branch: String)
@Serializable data class VCSCreateBranchParams(val projectID: String, val name: String)
@Serializable data class VCSAddWorktreeParams(val projectID: String, val name: String, val branch: String, val createBranch: Boolean)
@Serializable data class VCSRemoveWorktreeParams(val projectID: String, val worktreeID: String)

fun listProjectsRequest(id: String) =
    req(id, "listProjects", null)

fun selectProjectRequest(id: String, projectID: String) =
    req(id, "selectProject", taggedParams("selectProject", ProjectIDParams(projectID), ProjectIDParams.serializer()))

fun listWorktreesRequest(id: String, projectID: String) =
    req(id, "listWorktrees", taggedParams("listWorktrees", ProjectIDParams(projectID), ProjectIDParams.serializer()))

fun selectWorktreeRequest(id: String, projectID: String, worktreeID: String) =
    req(id, "selectWorktree", taggedParams("selectWorktree", SelectWorktreeParams(projectID, worktreeID), SelectWorktreeParams.serializer()))

fun getWorkspaceRequest(id: String, projectID: String) =
    req(id, "getWorkspace", taggedParams("getWorkspace", ProjectIDParams(projectID), ProjectIDParams.serializer()))

fun createTabRequest(id: String, params: CreateTabParams) =
    req(id, "createTab", taggedParams("createTab", params, CreateTabParams.serializer()))

fun selectTabRequest(id: String, params: TabRefParams) =
    req(id, "selectTab", taggedParams("selectTab", params, TabRefParams.serializer()))

fun closeTabRequest(id: String, params: TabRefParams) =
    req(id, "closeTab", taggedParams("closeTab", params, TabRefParams.serializer()))

fun getProjectLogoRequest(id: String, projectID: String) =
    req(id, "getProjectLogo", taggedParams("getProjectLogo", ProjectIDParams(projectID), ProjectIDParams.serializer()))

fun vcsListBranchesRequest(id: String, projectID: String) =
    req(id, "vcsListBranches", taggedParams("vcsListBranches", ProjectIDParams(projectID), ProjectIDParams.serializer()))

fun vcsSwitchBranchRequest(id: String, params: VCSSwitchBranchParams) =
    req(id, "vcsSwitchBranch", taggedParams("vcsSwitchBranch", params, VCSSwitchBranchParams.serializer()))

fun vcsCreateBranchRequest(id: String, params: VCSCreateBranchParams) =
    req(id, "vcsCreateBranch", taggedParams("vcsCreateBranch", params, VCSCreateBranchParams.serializer()))

fun vcsAddWorktreeRequest(id: String, params: VCSAddWorktreeParams) =
    req(id, "vcsAddWorktree", taggedParams("vcsAddWorktree", params, VCSAddWorktreeParams.serializer()))

fun vcsRemoveWorktreeRequest(id: String, params: VCSRemoveWorktreeParams) =
    req(id, "vcsRemoveWorktree", taggedParams("vcsRemoveWorktree", params, VCSRemoveWorktreeParams.serializer()))

// --- Result decoders ---

fun decodeProjects(result: TaggedValue?): List<ProjectDTO>? {
    if (result?.type != "projects" || result.value == null) return null
    return MuxyJson.decodeFromJsonElement(ListSerializer(ProjectDTO.serializer()), result.value)
}

fun decodeWorktrees(result: TaggedValue?): List<WorktreeDTO>? {
    if (result?.type != "worktrees" || result.value == null) return null
    return MuxyJson.decodeFromJsonElement(ListSerializer(WorktreeDTO.serializer()), result.value)
}

fun decodeWorkspace(result: TaggedValue?): WorkspaceDTO? {
    if (result?.type != "workspace" || result.value == null) return null
    return MuxyJson.decodeFromJsonElement(WorkspaceDTO.serializer(), result.value)
}

fun decodeProjectLogo(result: TaggedValue?): ProjectLogoDTO? {
    if (result?.type != "projectLogo" || result.value == null) return null
    return MuxyJson.decodeFromJsonElement(ProjectLogoDTO.serializer(), result.value)
}

fun decodeBranches(result: TaggedValue?): VCSBranchesDTO? {
    if (result?.type != "vcsBranches" || result.value == null) return null
    return MuxyJson.decodeFromJsonElement(VCSBranchesDTO.serializer(), result.value)
}

fun isOk(result: TaggedValue?): Boolean = result?.type == "ok"

// --- Event data decoders ---

fun decodeEventWorkspace(data: TaggedValue?): WorkspaceDTO? {
    if (data?.type != "workspace" || data.value == null) return null
    return MuxyJson.decodeFromJsonElement(WorkspaceDTO.serializer(), data.value)
}

fun decodeEventProjects(data: TaggedValue?): List<ProjectDTO>? {
    if (data?.type != "projects" || data.value == null) return null
    return MuxyJson.decodeFromJsonElement(ListSerializer(ProjectDTO.serializer()), data.value)
}

fun decodeEventDeviceTheme(data: TaggedValue?): DeviceThemeEventDTO? {
    if (data?.type != "deviceTheme" || data.value == null) return null
    return MuxyJson.decodeFromJsonElement(DeviceThemeEventDTO.serializer(), data.value)
}

fun decodeEventTab(data: TaggedValue?): TabChangeEventDTO? {
    if (data?.type != "tab" || data.value == null) return null
    return MuxyJson.decodeFromJsonElement(TabChangeEventDTO.serializer(), data.value)
}
