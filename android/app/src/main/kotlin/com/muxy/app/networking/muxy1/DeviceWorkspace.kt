package com.muxy.app.networking.muxy1

import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Tab
import com.muxy.app.models.TabArea
import com.muxy.app.models.TabKind
import com.muxy.app.models.Workspace
import com.muxy.app.models.WorkspaceFlattening
import com.muxy.app.networking.muxy1.protocol.CreateTabParams
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.GetWorkspaceParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.protocol.SelectProjectParams
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

suspend fun ConnectionManager.readWorkspace(
    connectionId: UUID,
    projectId: UUID,
): Workspace? {
    currentCoroutineContext().ensureActive()
    val result =
        try {
            request(connectionId, Method.GET_WORKSPACE, GetWorkspaceParams(projectId.uuidString))
        } catch (error: ProtocolException) {
            if (error.code == ErrorCode.NOT_FOUND) return null
            throw error
        }
    currentCoroutineContext().ensureActive()
    check(result.type == ResultType.WORKSPACE) { "Unexpected result ${result.type}" }
    return result.decode(Workspace.serializer()).also { check(it.projectId == projectId) }
}

suspend fun ConnectionManager.createTerminalTab(
    connectionId: UUID,
    projectId: UUID,
    isCurrent: () -> Boolean = { true },
): Tab? {
    val session = status.value.connectedSession(connectionId) ?: return null

    fun canContinue(): Boolean = isCurrent() && status.value.connectedSession(connectionId) == session
    if (!canContinue()) return null
    var workspace = readWorkspace(connectionId, projectId)
    if (!canContinue()) return null
    if (workspace == null) {
        currentCoroutineContext().ensureActive()
        request(connectionId, Method.SELECT_PROJECT, SelectProjectParams(projectId.uuidString))
        if (!canContinue()) return null
        workspace = checkNotNull(readWorkspace(connectionId, projectId)) { "The project workspace is unavailable" }
        if (!canContinue()) return null
        WorkspaceFlattening
            .tabAreas(workspace)
            .flatMap(TabArea::tabs)
            .firstOrNull { it.kind == TabKind.Terminal }
            ?.let { return it }
    }
    currentCoroutineContext().ensureActive()
    val areaId = WorkspaceFlattening.focusedTabArea(workspace)?.id?.uuidString
    val result = request(connectionId, Method.CREATE_TAB, CreateTabParams(projectId.uuidString, areaId, TabKind.Terminal.rawValue))
    currentCoroutineContext().ensureActive()
    if (!canContinue()) return null
    check(result.type == ResultType.TAB) { "Unexpected result ${result.type}" }
    return result.decode(Tab.serializer())
}
