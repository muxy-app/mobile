package com.muxy.app.features.demo

import com.muxy.app.core.serialization.parseUuid
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Project
import com.muxy.app.models.Tab
import com.muxy.app.models.TabArea
import com.muxy.app.models.TabKind
import com.muxy.app.models.Workspace
import com.muxy.app.models.WorkspaceNode
import com.muxy.app.networking.muxy1.protocol.CloseTabParams
import com.muxy.app.networking.muxy1.protocol.CreateTabParams
import com.muxy.app.networking.muxy1.protocol.EmptyResult
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.EventEnvelope
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.GetWorkspaceParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.PairingResult
import com.muxy.app.networking.muxy1.protocol.PaneOwner
import com.muxy.app.networking.muxy1.protocol.PaneOwnershipEvent
import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.RawTagged
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.protocol.SelectTabParams
import com.muxy.app.networking.muxy1.protocol.TakeOverPaneParams
import com.muxy.app.networking.muxy1.protocol.TerminalBytesEvent
import com.muxy.app.networking.muxy1.protocol.TerminalInputParams
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import java.util.UUID

data class DemoReply(
    val result: RawTagged,
    val events: List<EventEnvelope> = emptyList(),
)

class DemoBackend {
    private val mutex = Mutex()
    private val workspaces =
        mutableMapOf(
            MUXY_PROJECT_ID to
                initialWorkspace(
                    MUXY_PROJECT_ID,
                    MUXY_WORKTREE_ID,
                    MUXY_AREA_ID,
                    MUXY_PATH,
                    Tab(MUXY_TAB_ID, TabKind.Terminal, "zsh", false, MUXY_PANE_ID),
                ),
            WEB_PROJECT_ID to
                initialWorkspace(
                    WEB_PROJECT_ID,
                    WEB_WORKTREE_ID,
                    WEB_AREA_ID,
                    WEB_PATH,
                    Tab(WEB_TAB_ID, TabKind.Terminal, "dev", false, WEB_PANE_ID),
                ),
        )
    private var tabCounter = 2
    private val shell = DemoShell()

    val clientId: UUID = CLIENT_ID

    fun authenticate(): RawTagged = RawTagged.of(ResultType.PAIRING, PairingResult(clientId.uuidString, DemoConnection.NAME))

    suspend fun handle(
        method: Method,
        params: JsonElement?,
    ): DemoReply =
        mutex.withLock {
            when (method) {
                Method.AUTHENTICATE_DEVICE -> {
                    DemoReply(authenticate())
                }

                Method.LIST_PROJECTS -> {
                    DemoReply(RawTagged.of(ResultType.PROJECTS, ProjectsResult(projects)))
                }

                Method.SELECT_PROJECT -> {
                    DemoReply(ok())
                }

                Method.GET_WORKSPACE -> {
                    DemoReply(
                        RawTagged.of(ResultType.WORKSPACE, workspace(decode(GetWorkspaceParams.serializer(), params).projectId)),
                    )
                }

                Method.CREATE_TAB -> {
                    createTab(decode(CreateTabParams.serializer(), params))
                }

                Method.CLOSE_TAB -> {
                    closeTab(decode(CloseTabParams.serializer(), params))
                }

                Method.SELECT_TAB -> {
                    selectTab(decode(SelectTabParams.serializer(), params))
                }

                Method.TAKE_OVER_PANE -> {
                    takeOverPane(decode(TakeOverPaneParams.serializer(), params))
                }

                Method.TERMINAL_INPUT -> {
                    terminalInput(decode(TerminalInputParams.serializer(), params))
                }

                Method.RELEASE_PANE, Method.SET_CLIENT_THEME, Method.TERMINAL_RESIZE, Method.TERMINAL_SCROLL -> {
                    DemoReply(ok())
                }

                Method.PAIR_DEVICE, Method.GET_PROJECT_LOGO -> {
                    throw notFound()
                }
            }
        }

    private fun takeOverPane(params: TakeOverPaneParams): DemoReply {
        val paneId = uuid(params.paneId)
        val ownership = PaneOwnershipEvent(paneId, PaneOwner.Remote(clientId, DEMO_DEVICE_NAME))
        val snapshot = TerminalBytesEvent(paneId, shell.open(paneId).toByteArray())
        return DemoReply(
            ok(),
            listOf(
                EventEnvelope(EventName.PANE_OWNERSHIP_CHANGED, RawTagged.of(EventType.PANE_OWNERSHIP, ownership)),
                EventEnvelope(EventName.TERMINAL_SNAPSHOT, RawTagged.of(EventType.TERMINAL_SNAPSHOT, snapshot)),
            ),
        )
    }

    private fun terminalInput(params: TerminalInputParams): DemoReply {
        val paneId = uuid(params.paneId)
        val output = shell.input(paneId, params.bytes.decodeToString())
        if (output.isEmpty()) return DemoReply(ok())
        val event = TerminalBytesEvent(paneId, output.toByteArray())
        return DemoReply(ok(), listOf(EventEnvelope(EventName.TERMINAL_OUTPUT, RawTagged.of(EventType.TERMINAL_OUTPUT, event))))
    }

    private fun createTab(params: CreateTabParams): DemoReply {
        val projectId = uuid(params.projectId)
        val workspace = workspace(params.projectId)
        val area = rootArea(workspace)
        if (params.areaId != null && parseUuid(params.areaId) != area.id) throw notFound()
        tabCounter += 1
        val tab = Tab(UUID.randomUUID(), TabKind.Terminal, "zsh $tabCounter", false, UUID.randomUUID())
        val updated = area.copy(tabs = area.tabs + tab, activeTabId = tab.id)
        workspaces[projectId] = workspace.copy(focusedAreaId = updated.id, root = WorkspaceNode.Area(updated))
        return DemoReply(RawTagged.of(ResultType.TAB, tab), workspaceEvents(projectId))
    }

    private fun closeTab(params: CloseTabParams): DemoReply {
        val projectId = uuid(params.projectId)
        val workspace = workspace(params.projectId)
        val area = rootArea(workspace)
        val tabId = uuid(params.tabId)
        if (area.id != uuid(params.areaId)) throw notFound()
        val tabs = area.tabs.filterNot { it.id == tabId }
        val activeTabId = if (area.activeTabId == tabId) tabs.firstOrNull()?.id else area.activeTabId
        val updated = area.copy(tabs = tabs, activeTabId = activeTabId)
        workspaces[projectId] = workspace.copy(focusedAreaId = area.id, root = WorkspaceNode.Area(updated))
        return DemoReply(ok(), workspaceEvents(projectId))
    }

    private fun selectTab(params: SelectTabParams): DemoReply {
        val projectId = uuid(params.projectId)
        val workspace = workspace(params.projectId)
        val area = rootArea(workspace)
        val tabId = uuid(params.tabId)
        if (area.id != uuid(params.areaId) || area.tabs.none { it.id == tabId }) throw notFound()
        workspaces[projectId] = workspace.copy(focusedAreaId = area.id, root = WorkspaceNode.Area(area.copy(activeTabId = tabId)))
        return DemoReply(ok(), workspaceEvents(projectId))
    }

    private fun workspace(projectId: String): Workspace = workspaces[uuid(projectId)] ?: throw notFound()

    private fun rootArea(workspace: Workspace): TabArea = (workspace.root as? WorkspaceNode.Area)?.area ?: throw notFound()

    private fun workspaceEvents(projectId: UUID): List<EventEnvelope> {
        val workspace = workspaces[projectId] ?: throw notFound()
        return listOf(EventEnvelope(EventName.WORKSPACE_CHANGED, RawTagged.of(EventType.WORKSPACE, workspace)))
    }

    private fun ok(): RawTagged = RawTagged.of(ResultType.OK, EmptyResult())

    private fun <T> decode(
        deserializer: DeserializationStrategy<T>,
        params: JsonElement?,
    ): T {
        params ?: throw invalidParams()
        return try {
            ProtocolJson.decodeFromJsonElement(deserializer, params)
        } catch (error: SerializationException) {
            throw invalidParams()
        } catch (error: IllegalArgumentException) {
            throw invalidParams()
        }
    }

    private fun uuid(value: String): UUID = parseUuid(value) ?: throw invalidParams()

    private fun notFound() = ProtocolException(ErrorCode.NOT_FOUND.body("Not available in demo mode"))

    private fun invalidParams() = ProtocolException(ErrorCode.INVALID_PARAMS.body("Invalid demo request"))

    private companion object {
        val CLIENT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000101")
        val MUXY_PROJECT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000201")
        val WEB_PROJECT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000202")
        val MUXY_WORKTREE_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000301")
        val WEB_WORKTREE_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000302")
        val MUXY_AREA_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000401")
        val WEB_AREA_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000402")
        val MUXY_TAB_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000501")
        val WEB_TAB_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000502")
        val MUXY_PANE_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000601")
        val WEB_PANE_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000602")
        val WORK_WORKSPACE_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000701")
        val PERSONAL_WORKSPACE_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000702")
        const val MUXY_PATH = "/Users/demo/Projects/muxy"
        const val WEB_PATH = "/Users/demo/Projects/web-app"
        const val CREATED_AT = "2026-06-08T00:00:00.000Z"
        const val DEMO_DEVICE_NAME = "Android (Demo)"
        const val PROJECTS_PARENT = "/Users/demo/Projects"

        val projects =
            listOf(
                Project(
                    id = MUXY_PROJECT_ID,
                    name = "Muxy",
                    path = MUXY_PATH,
                    sortOrder = 0.0,
                    createdAt = CREATED_AT,
                    icon = "terminal",
                    iconColor = "#22c55e",
                    preferredWorktreeParentPath = PROJECTS_PARENT,
                    worktreesEnabled = false,
                    workspaceKind = "local",
                    workspaceId = WORK_WORKSPACE_ID,
                    workspaceName = "Work",
                ),
                Project(
                    id = WEB_PROJECT_ID,
                    name = "Web App",
                    path = WEB_PATH,
                    sortOrder = 1.0,
                    createdAt = CREATED_AT,
                    icon = "globe",
                    iconColor = "#3b82f6",
                    preferredWorktreeParentPath = PROJECTS_PARENT,
                    worktreesEnabled = false,
                    workspaceKind = "local",
                    workspaceId = PERSONAL_WORKSPACE_ID,
                    workspaceName = "Personal",
                ),
            )

        fun initialWorkspace(
            projectId: UUID,
            worktreeId: UUID,
            areaId: UUID,
            path: String,
            tab: Tab,
        ): Workspace =
            Workspace(
                projectId = projectId,
                worktreeId = worktreeId,
                focusedAreaId = areaId,
                root = WorkspaceNode.Area(TabArea(areaId, path, listOf(tab), tab.id)),
            )
    }
}
