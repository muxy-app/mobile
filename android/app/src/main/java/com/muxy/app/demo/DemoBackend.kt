package com.muxy.app.demo

import android.util.Base64
import com.muxy.app.data.SavedDevice
import com.muxy.app.data.SessionRepository
import com.muxy.app.model.CreateTabParams
import com.muxy.app.model.MuxyError
import com.muxy.app.model.MuxyEvent
import com.muxy.app.model.MuxyJson
import com.muxy.app.model.MuxyRequest
import com.muxy.app.model.MuxyResponse
import com.muxy.app.model.ProjectDTO
import com.muxy.app.model.ProjectIDParams
import com.muxy.app.model.ReleasePaneParams
import com.muxy.app.model.SelectWorktreeParams
import com.muxy.app.model.SplitNodeDTO
import com.muxy.app.model.TabAreaDTO
import com.muxy.app.model.TabDTO
import com.muxy.app.model.TabKindDTO
import com.muxy.app.model.TabRefParams
import com.muxy.app.model.TaggedValue
import com.muxy.app.model.TakeOverPaneParams
import com.muxy.app.model.TerminalInputParams
import com.muxy.app.model.TerminalOutputEventDTO
import com.muxy.app.model.VCSAddWorktreeParams
import com.muxy.app.model.VCSBranchesDTO
import com.muxy.app.model.VCSCreateBranchParams
import com.muxy.app.model.VCSRemoveWorktreeParams
import com.muxy.app.model.VCSSwitchBranchParams
import com.muxy.app.model.WorkspaceDTO
import com.muxy.app.model.WorktreeDTO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class DemoBackend {

    private val mutex = Mutex()

    private val seedDevice = SavedDevice(SEEDED_DEVICE_NAME, SEEDED_DEVICE_HOST, SEEDED_DEVICE_PORT)
    private var savedDevices: MutableList<SavedDevice> = mutableListOf(seedDevice)

    private val muxyID = "11111111-1111-1111-1111-111111111111"
    private val webID = "22222222-2222-2222-2222-222222222222"
    private val now = "2026-01-01T00:00:00Z"

    private val muxyMainWT = WorktreeDTO(
        id = "AAAA0001-0000-0000-0000-000000000001",
        name = "main",
        path = "/Users/demo/Projects/muxy",
        branch = "main",
        isPrimary = true,
        createdAt = now,
    )
    private val muxyFeatureWT = WorktreeDTO(
        id = "AAAA0001-0000-0000-0000-000000000002",
        name = "feature-search",
        path = "/Users/demo/Projects/muxy-worktrees/feature-search",
        branch = "feature/search",
        isPrimary = false,
        createdAt = now,
    )
    private val webMainWT = WorktreeDTO(
        id = "BBBB0001-0000-0000-0000-000000000001",
        name = "main",
        path = "/Users/demo/Projects/web-app",
        branch = "main",
        isPrimary = true,
        createdAt = now,
    )

    private val projects: List<ProjectDTO> = listOf(
        ProjectDTO(
            id = muxyID,
            name = "muxy",
            path = "/Users/demo/Projects/muxy",
            sortOrder = 0,
            createdAt = now,
            icon = "terminal",
            logo = null,
            iconColor = "blue",
        ),
        ProjectDTO(
            id = webID,
            name = "web-app",
            path = "/Users/demo/Projects/web-app",
            sortOrder = 1,
            createdAt = now,
            icon = "globe",
            logo = null,
            iconColor = "green",
        ),
    )

    private val worktreesByProject: MutableMap<String, MutableList<WorktreeDTO>> = mutableMapOf(
        muxyID to mutableListOf(muxyMainWT, muxyFeatureWT),
        webID to mutableListOf(webMainWT),
    )

    private val workspaces: MutableMap<String, WorkspaceDTO> = mutableMapOf(
        muxyID to makeWorkspace(muxyID, muxyMainWT.id, "/Users/demo/Projects/muxy"),
        webID to makeWorkspace(webID, webMainWT.id, "/Users/demo/Projects/web-app"),
    )

    private val branchesByProject: MutableMap<String, VCSBranchesDTO> = mutableMapOf(
        muxyID to VCSBranchesDTO(current = "main", locals = listOf("main", "feature/search", "fix/scrolling")),
        webID to VCSBranchesDTO(current = "main", locals = listOf("main")),
    )

    fun seededDevice(): SavedDevice = seedDevice
    fun initialTheme(): SessionRepository.DeviceTheme = THEME

    suspend fun listDevices(): List<SavedDevice> = mutex.withLock { savedDevices.toList() }

    suspend fun addDevice(device: SavedDevice) = mutex.withLock {
        savedDevices.removeAll { it.host == device.host && it.port == device.port }
        savedDevices.add(0, device)
    }

    suspend fun removeDevice(device: SavedDevice) = mutex.withLock {
        savedDevices.removeAll { it.id == device.id }
    }

    fun simulatedDelay(method: String): Duration = when (method) {
        "vcsPush", "vcsPull", "vcsCommit", "vcsCreatePR",
        "vcsAddWorktree", "vcsRemoveWorktree", "vcsSwitchBranch",
        -> 700.milliseconds
        "vcsCreateBranch", "vcsStageFiles", "vcsUnstageFiles", "vcsDiscardFiles",
        -> 250.milliseconds
        else -> Duration.ZERO
    }

    suspend fun handle(request: MuxyRequest, emitEvent: suspend (MuxyEvent) -> Unit): MuxyResponse {
        val emitPaneOwnership: suspend (String, Boolean) -> Unit = { paneID, remote ->
            emitEvent(buildPaneOwnershipEvent(paneID, remote))
        }
        var pendingGreetingPaneID: String? = null
        val response = mutex.withLock {
            val id = request.id
            val resp: MuxyResponse = when (request.method) {
                "listProjects" -> ok(id, "projects", listSerializer(ProjectDTO.serializer()).toElement(projects))

                "selectProject" -> {
                    val p = decode<ProjectIDParams>(request.params, ProjectIDParams.serializer()) ?: return notFound(id)
                    if (workspaces[p.projectID] == null) return notFound(id)
                    okEmpty(id)
                }

                "listWorktrees" -> {
                    val p = decode<ProjectIDParams>(request.params, ProjectIDParams.serializer())
                        ?: return invalidParams(id)
                    val list = worktreesByProject[p.projectID].orEmpty().toList()
                    ok(id, "worktrees", listSerializer(WorktreeDTO.serializer()).toElement(list))
                }

                "getWorkspace" -> {
                    val p = decode<ProjectIDParams>(request.params, ProjectIDParams.serializer()) ?: return notFound(id)
                    val ws = workspaces[p.projectID] ?: return notFound(id)
                    ok(id, "workspace", WorkspaceDTO.serializer().toElement(ws))
                }

                "selectWorktree" -> {
                    decode<SelectWorktreeParams>(request.params, SelectWorktreeParams.serializer()) ?: return invalidParams(id)
                    okEmpty(id)
                }

                "createTab" -> {
                    val p = decode<CreateTabParams>(request.params, CreateTabParams.serializer()) ?: return invalidParams(id)
                    val ws = workspaces[p.projectID] ?: return notFound(id)
                    workspaces[p.projectID] = ws.copy(root = appendTab(ws.root, ws.focusedAreaID))
                    okEmpty(id)
                }

                "closeTab" -> {
                    val p = decode<TabRefParams>(request.params, TabRefParams.serializer()) ?: return invalidParams(id)
                    val ws = workspaces[p.projectID] ?: return notFound(id)
                    workspaces[p.projectID] = ws.copy(root = removeTab(ws.root, p.areaID, p.tabID))
                    okEmpty(id)
                }

                "selectTab" -> {
                    val p = decode<TabRefParams>(request.params, TabRefParams.serializer()) ?: return invalidParams(id)
                    val ws = workspaces[p.projectID] ?: return notFound(id)
                    workspaces[p.projectID] = ws.copy(root = selectTab(ws.root, p.areaID, p.tabID))
                    okEmpty(id)
                }

                "vcsListBranches" -> {
                    val p = decode<ProjectIDParams>(request.params, ProjectIDParams.serializer()) ?: return notFound(id)
                    val branches = branchesByProject[p.projectID] ?: return notFound(id)
                    ok(id, "vcsBranches", VCSBranchesDTO.serializer().toElement(branches))
                }

                "vcsSwitchBranch" -> {
                    val p = decode<VCSSwitchBranchParams>(request.params, VCSSwitchBranchParams.serializer())
                        ?: return notFound(id)
                    val current = branchesByProject[p.projectID] ?: return notFound(id)
                    branchesByProject[p.projectID] = current.copy(current = p.branch)
                    okEmpty(id)
                }

                "vcsCreateBranch" -> {
                    val p = decode<VCSCreateBranchParams>(request.params, VCSCreateBranchParams.serializer())
                        ?: return notFound(id)
                    val current = branchesByProject[p.projectID] ?: return notFound(id)
                    val locals = if (current.locals.contains(p.name)) current.locals else current.locals + p.name
                    branchesByProject[p.projectID] = current.copy(current = p.name, locals = locals)
                    okEmpty(id)
                }

                "vcsAddWorktree" -> {
                    val p = decode<VCSAddWorktreeParams>(request.params, VCSAddWorktreeParams.serializer())
                        ?: return invalidParams(id)
                    val wt = WorktreeDTO(
                        id = UUID.randomUUID().toString(),
                        name = p.name,
                        path = "/Users/demo/Projects/${p.name}",
                        branch = p.branch,
                        isPrimary = false,
                        createdAt = now,
                    )
                    worktreesByProject.getOrPut(p.projectID) { mutableListOf() }.add(wt)
                    if (p.createBranch) {
                        branchesByProject[p.projectID]?.let { b ->
                            if (!b.locals.contains(p.branch)) {
                                branchesByProject[p.projectID] = b.copy(locals = b.locals + p.branch)
                            }
                        }
                    }
                    okEmpty(id)
                }

                "vcsRemoveWorktree" -> {
                    val p = decode<VCSRemoveWorktreeParams>(request.params, VCSRemoveWorktreeParams.serializer())
                        ?: return invalidParams(id)
                    worktreesByProject[p.projectID]?.removeAll { it.id == p.worktreeID }
                    okEmpty(id)
                }

                "takeOverPane" -> {
                    val p = decode<TakeOverPaneParams>(request.params, TakeOverPaneParams.serializer())
                        ?: return@withLock invalidParams(id)
                    emitPaneOwnership(p.paneID, true)
                    pendingGreetingPaneID = p.paneID
                    okEmpty(id)
                }

                "releasePane" -> {
                    val p = decode<ReleasePaneParams>(request.params, ReleasePaneParams.serializer())
                        ?: return invalidParams(id)
                    emitPaneOwnership(p.paneID, false)
                    okEmpty(id)
                }

                "terminalResize", "terminalScroll" -> okEmpty(id)

                "getProjectLogo" -> notFound(id)
                "getTerminalContent" -> notFound(id)

                "registerDevice", "pairDevice", "authenticateDevice", "terminalInput" -> invalidParams(id)

                else -> okEmpty(id)
            }
            resp
        }
        pendingGreetingPaneID?.let { paneID ->
            kotlinx.coroutines.delay(150)
            emitEvent(buildTerminalOutputEvent(paneID, GREETING_BYTES))
        }
        return response
    }

    suspend fun handleTerminalInput(
        request: MuxyRequest,
        emitEvent: suspend (MuxyEvent) -> Unit,
    ) {
        val emitTerminalOutput: suspend (String, ByteArray) -> Unit = { paneID, bytes ->
            emitEvent(buildTerminalOutputEvent(paneID, bytes))
        }
        val params = decode<TerminalInputParams>(request.params, TerminalInputParams.serializer()) ?: return
        val bytes = runCatching { Base64.decode(params.bytes, Base64.DEFAULT) }.getOrNull() ?: return
        val containsEnter = bytes.any { it == 0x0D.toByte() || it == 0x0A.toByte() }
        val payload = if (containsEnter) {
            val echo = bytes.filter { it != 0x0D.toByte() && it != 0x0A.toByte() }.toByteArray()
            echo + byteArrayOf(0x0D, 0x0A) + DEMO_NOTICE_BYTES + PROMPT_BYTES
        } else {
            bytes
        }
        emitTerminalOutput(params.paneID, payload)
    }

    private fun buildTerminalOutputEvent(paneID: String, bytes: ByteArray): MuxyEvent {
        val dto = TerminalOutputEventDTO(paneID = paneID, bytes = Base64.encodeToString(bytes, Base64.NO_WRAP))
        return MuxyEvent(
            event = "terminalOutput",
            data = TaggedValue(
                type = "terminalOutput",
                value = MuxyJson.encodeToJsonElement(TerminalOutputEventDTO.serializer(), dto),
            ),
        )
    }

    private fun buildPaneOwnershipEvent(paneID: String, remote: Boolean): MuxyEvent {
        val ownerJson = if (remote) {
            kotlinx.serialization.json.JsonObject(
                mapOf(
                    "remote" to kotlinx.serialization.json.JsonObject(
                        mapOf(
                            "deviceID" to kotlinx.serialization.json.JsonPrimitive(MY_CLIENT_ID),
                            "deviceName" to kotlinx.serialization.json.JsonPrimitive("Phone (Demo)"),
                        ),
                    ),
                ),
            )
        } else {
            kotlinx.serialization.json.JsonObject(
                mapOf(
                    "mac" to kotlinx.serialization.json.JsonObject(
                        mapOf("deviceName" to kotlinx.serialization.json.JsonPrimitive(SEEDED_DEVICE_NAME)),
                    ),
                ),
            )
        }
        val dto = kotlinx.serialization.json.JsonObject(
            mapOf(
                "paneID" to kotlinx.serialization.json.JsonPrimitive(paneID),
                "owner" to ownerJson,
            ),
        )
        return MuxyEvent(
            event = "paneOwnershipChanged",
            data = TaggedValue(type = "paneOwnership", value = dto),
        )
    }

    private fun appendTab(node: SplitNodeDTO, focusedAreaID: String?): SplitNodeDTO = when (node) {
        is SplitNodeDTO.TabArea -> {
            if (focusedAreaID != null && focusedAreaID != node.area.id) {
                node
            } else {
                val newTab = TabDTO(
                    id = UUID.randomUUID().toString(),
                    kind = TabKindDTO.TERMINAL,
                    title = "zsh",
                    isPinned = false,
                    paneID = UUID.randomUUID().toString(),
                )
                SplitNodeDTO.TabArea(
                    node.area.copy(tabs = node.area.tabs + newTab, activeTabID = newTab.id),
                )
            }
        }
        is SplitNodeDTO.Split -> SplitNodeDTO.Split(
            node.branch.copy(
                first = appendTab(node.branch.first, focusedAreaID),
                second = appendTab(node.branch.second, focusedAreaID),
            ),
        )
    }

    private fun removeTab(node: SplitNodeDTO, areaID: String, tabID: String): SplitNodeDTO = when (node) {
        is SplitNodeDTO.TabArea -> {
            if (node.area.id != areaID) {
                node
            } else {
                val remaining = node.area.tabs.filterNot { it.id == tabID }
                val active = if (node.area.activeTabID == tabID) remaining.firstOrNull()?.id else node.area.activeTabID
                SplitNodeDTO.TabArea(node.area.copy(tabs = remaining, activeTabID = active))
            }
        }
        is SplitNodeDTO.Split -> SplitNodeDTO.Split(
            node.branch.copy(
                first = removeTab(node.branch.first, areaID, tabID),
                second = removeTab(node.branch.second, areaID, tabID),
            ),
        )
    }

    private fun selectTab(node: SplitNodeDTO, areaID: String, tabID: String): SplitNodeDTO = when (node) {
        is SplitNodeDTO.TabArea -> {
            if (node.area.id != areaID) node
            else SplitNodeDTO.TabArea(node.area.copy(activeTabID = tabID))
        }
        is SplitNodeDTO.Split -> SplitNodeDTO.Split(
            node.branch.copy(
                first = selectTab(node.branch.first, areaID, tabID),
                second = selectTab(node.branch.second, areaID, tabID),
            ),
        )
    }

    private fun makeWorkspace(projectID: String, worktreeID: String, projectPath: String): WorkspaceDTO {
        val areaID = UUID.randomUUID().toString()
        val tab1 = TabDTO(UUID.randomUUID().toString(), TabKindDTO.TERMINAL, "zsh", false, UUID.randomUUID().toString())
        val tab2 = TabDTO(UUID.randomUUID().toString(), TabKindDTO.TERMINAL, "server", false, UUID.randomUUID().toString())
        val area = TabAreaDTO(areaID, projectPath, listOf(tab1, tab2), tab1.id)
        return WorkspaceDTO(projectID, worktreeID, areaID, SplitNodeDTO.TabArea(area))
    }

    private fun ok(id: String, type: String, value: JsonElement?): MuxyResponse =
        MuxyResponse(id = id, result = TaggedValue(type = type, value = value))

    private fun okEmpty(id: String): MuxyResponse =
        MuxyResponse(id = id, result = TaggedValue(type = "ok", value = null))

    private fun notFound(id: String): MuxyResponse =
        MuxyResponse(id = id, error = MuxyError(code = 404, message = "not found"))

    private fun invalidParams(id: String): MuxyResponse =
        MuxyResponse(id = id, error = MuxyError(code = 400, message = "invalid params"))

    private fun <T> decode(params: TaggedValue?, ser: kotlinx.serialization.KSerializer<T>): T? {
        val value = params?.value ?: return null
        return runCatching { MuxyJson.decodeFromJsonElement(ser, value) }.getOrNull()
    }

    private fun <T> kotlinx.serialization.KSerializer<T>.toElement(value: T): JsonElement =
        MuxyJson.encodeToJsonElement(this, value)

    private fun <T> listSerializer(s: kotlinx.serialization.KSerializer<T>) = ListSerializer(s)

    companion object {
        const val SEEDED_DEVICE_NAME = "Demo Mac"
        const val SEEDED_DEVICE_HOST = "192.168.1.42"
        const val SEEDED_DEVICE_PORT = 4865
        const val MY_CLIENT_ID = "00000000-0000-0000-0000-00000000C11D"

        val THEME = SessionRepository.DeviceTheme(
            fg = 0xC9C2D9L,
            bg = 0x19171FL,
            palette = listOf(
                0x141219L, 0xEC4899L, 0x34D399L, 0xE0AF68L,
                0xC370D3L, 0x6366F1L, 0x22D3EEL, 0xA9B1D6L,
                0x2E2B34L, 0xF472B6L, 0x6EE7B7L, 0xFBBF24L,
                0xD99BE5L, 0x818CF8L, 0x67E8F9L, 0xC9C2D9L,
            ),
        )

        private val GREETING_BYTES: ByteArray = (
            "[1;32mDemo Mode[0m — this terminal is simulated.\r\n" +
                "Type any command and press Enter to see the demo response.\r\n" +
                "demo@muxy ~ % "
            ).toByteArray(Charsets.UTF_8)

        private val DEMO_NOTICE_BYTES: ByteArray =
            "[33m[Demo Mode][0m Commands are not executed in demo mode.\r\n".toByteArray(Charsets.UTF_8)

        private val PROMPT_BYTES: ByteArray = "demo@muxy ~ % ".toByteArray(Charsets.UTF_8)
    }
}
