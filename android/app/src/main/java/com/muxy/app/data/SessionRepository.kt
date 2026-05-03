package com.muxy.app.data

import android.util.Log
import com.muxy.app.model.CreateTabParams
import com.muxy.app.model.MuxyEventKind
import com.muxy.app.model.NotificationDTO
import com.muxy.app.model.ProjectDTO
import com.muxy.app.model.ProjectLogoDTO
import com.muxy.app.model.TabRefParams
import com.muxy.app.model.VCSAddWorktreeParams
import com.muxy.app.model.VCSBranchesDTO
import com.muxy.app.model.VCSCreateBranchParams
import com.muxy.app.model.VCSRemoveWorktreeParams
import com.muxy.app.model.VCSSwitchBranchParams
import com.muxy.app.model.WorkspaceDTO
import com.muxy.app.model.WorktreeDTO
import com.muxy.app.model.closeTabRequest
import com.muxy.app.model.createTabRequest
import com.muxy.app.model.decodeBranches
import com.muxy.app.model.PaneOwner
import com.muxy.app.model.decodeProjectLogo
import com.muxy.app.model.decodeProjects
import com.muxy.app.model.toKind
import com.muxy.app.model.decodeWorkspace
import com.muxy.app.model.decodeWorktrees
import com.muxy.app.model.getProjectLogoRequest
import com.muxy.app.model.getWorkspaceRequest
import com.muxy.app.model.isOk
import com.muxy.app.model.listProjectsRequest
import com.muxy.app.model.listWorktreesRequest
import com.muxy.app.model.selectProjectRequest
import com.muxy.app.model.selectTabRequest
import com.muxy.app.model.selectWorktreeRequest
import com.muxy.app.model.vcsAddWorktreeRequest
import com.muxy.app.model.vcsCreateBranchRequest
import com.muxy.app.model.vcsListBranchesRequest
import com.muxy.app.model.vcsRemoveWorktreeRequest
import com.muxy.app.model.vcsSwitchBranchRequest
import com.muxy.app.net.MuxyClient
import com.muxy.app.net.TransportEvent
import com.muxy.app.net.newRequestId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val TAG = "Session"

/**
 * Mirrors ConnectionManager.swift's post-auth state and operations:
 * lists projects, workspaces, worktrees; reacts to server events.
 */
class SessionRepository(
    private val client: MuxyClient,
    private val scope: CoroutineScope,
) {
    data class DeviceTheme(val fg: Long, val bg: Long, val palette: List<Long>) {
        val isDark: Boolean
            get() {
                val r = ((bg shr 16) and 0xFF) / 255.0
                val g = ((bg shr 8) and 0xFF) / 255.0
                val b = (bg and 0xFF) / 255.0
                return (0.2126 * r + 0.7152 * g + 0.0722 * b) < 0.5
            }
    }

    private val _projects = MutableStateFlow<List<ProjectDTO>>(emptyList())
    val projects: StateFlow<List<ProjectDTO>> = _projects.asStateFlow()

    private val _projectLogos = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val projectLogos: StateFlow<Map<String, ByteArray>> = _projectLogos.asStateFlow()

    private val _projectWorktrees = MutableStateFlow<Map<String, List<WorktreeDTO>>>(emptyMap())
    val projectWorktrees: StateFlow<Map<String, List<WorktreeDTO>>> = _projectWorktrees.asStateFlow()

    private val _activeProjectID = MutableStateFlow<String?>(null)
    val activeProjectID: StateFlow<String?> = _activeProjectID.asStateFlow()

    private val _workspace = MutableStateFlow<WorkspaceDTO?>(null)
    val workspace: StateFlow<WorkspaceDTO?> = _workspace.asStateFlow()

    private val _deviceTheme = MutableStateFlow<DeviceTheme?>(null)
    val deviceTheme: StateFlow<DeviceTheme?> = _deviceTheme.asStateFlow()

    private val _myClientID = MutableStateFlow<String?>(null)
    val myClientID: StateFlow<String?> = _myClientID.asStateFlow()

    /**
     * One-shot signal: the next [TerminalView] mount is allowed to silently
     * auto-takeover the pane regardless of who currently owns it. Set by
     * deliberate user actions (opening a project, switching tabs); consumed
     * by the first TerminalView that mounts after the action.
     */
    private val _consumeAutoTakeover = java.util.concurrent.atomic.AtomicBoolean(false)
    fun armAutoTakeover() { _consumeAutoTakeover.set(true) }
    fun consumeAutoTakeover(): Boolean = _consumeAutoTakeover.getAndSet(false)

    private val _paneOwners = MutableStateFlow<Map<String, PaneOwner>>(emptyMap())
    val paneOwners: StateFlow<Map<String, PaneOwner>> = _paneOwners.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationDTO>>(emptyList())
    val notifications: StateFlow<List<NotificationDTO>> = _notifications.asStateFlow()

    fun paneIsOwnedBySelf(paneID: String): Boolean {
        val mine = _myClientID.value ?: return false
        val owner = _paneOwners.value[paneID] as? PaneOwner.Remote ?: return false
        return owner.deviceID == mine
    }

    private var eventJob: Job? = null

    fun startObserving() {
        eventJob?.cancel()
        eventJob = scope.launch {
            client.events.collect { evt ->
                when (evt) {
                    is TransportEvent.EventReceived -> handleEvent(evt.event)
                    else -> Unit
                }
            }
        }
    }

    fun stopObserving() {
        eventJob?.cancel()
        eventJob = null
        closeAllPanes()
        _projects.value = emptyList()
        _projectLogos.value = emptyMap()
        _projectWorktrees.value = emptyMap()
        _activeProjectID.value = null
        _workspace.value = null
        _paneOwners.value = emptyMap()
        _myClientID.value = null
        _notifications.value = emptyList()
    }

    fun markNotificationRead(id: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    private fun handleEvent(event: com.muxy.app.model.MuxyEvent) {
        // Routed through the sealed MuxyEventKind so adding a new server event
        // forces a compile-time decision here rather than silently dropping.
        // PaneSession owns terminalOutput/Snapshot — they are intentional no-ops
        // at the repo level.
        when (val kind = event.toKind()) {
            is MuxyEventKind.ProjectsChanged -> _projects.value = kind.projects
            is MuxyEventKind.WorkspaceChanged -> _workspace.value = kind.workspace
            is MuxyEventKind.ThemeChanged -> {
                val t = kind.theme
                _deviceTheme.value = DeviceTheme(t.fg, t.bg, t.palette ?: emptyList())
            }
            is MuxyEventKind.PaneOwnershipChanged -> {
                val dto = kind.ownership
                val owner = PaneOwner.fromJson(dto.owner) ?: run {
                    Log.w(TAG, "paneOwnershipChanged: failed to parse owner=${dto.owner}")
                    return
                }
                _paneOwners.value = _paneOwners.value + (dto.paneID to owner)
            }
            is MuxyEventKind.TabChanged -> {
                // Mac authoritatively pushes a workspaceChanged after tab edits,
                // so the tab event itself doesn't need state mutation. iOS also
                // no-ops on .tab (ConnectionManager.swift handleEvent).
            }
            is MuxyEventKind.NotificationReceived -> {
                _notifications.value = listOf(kind.notification) + _notifications.value
            }
            is MuxyEventKind.TerminalOutput,
            is MuxyEventKind.TerminalSnapshot -> Unit // routed by PaneSession
            is MuxyEventKind.Unknown -> Unit // already logged in toKind()
        }
    }

    fun setMyClientID(id: String) { _myClientID.value = id }

    /** Drop stale pane-ownership state so a reconnecting client will re-take its panes. */
    fun clearPaneOwners() { _paneOwners.value = emptyMap() }

    suspend fun refreshProjects() {
        val resp = runCatching { client.send(listProjectsRequest(newRequestId()), 10.seconds) }
            .getOrElse { return setError("listProjects: ${it.message}") }
        val list = decodeProjects(resp.result) ?: return setError(resp.error?.message ?: "listProjects: unexpected response")
        _projects.value = list
        list.forEach { project ->
            if (project.logo != null) fetchLogo(project.id)
            refreshWorktrees(project.id)
        }
    }

    private suspend fun fetchLogo(projectID: String) {
        if (_projectLogos.value.containsKey(projectID)) return
        val resp = runCatching { client.send(getProjectLogoRequest(newRequestId(), projectID), 15.seconds) }
            .getOrNull() ?: return
        val logo: ProjectLogoDTO = decodeProjectLogo(resp.result) ?: return
        val bytes = runCatching { android.util.Base64.decode(logo.pngData, android.util.Base64.DEFAULT) }
            .getOrNull() ?: return
        _projectLogos.value = _projectLogos.value + (projectID to bytes)
    }

    suspend fun refreshWorktrees(projectID: String) {
        val resp = runCatching { client.send(listWorktreesRequest(newRequestId(), projectID), 10.seconds) }
            .getOrNull() ?: return
        val list = decodeWorktrees(resp.result) ?: return
        _projectWorktrees.value = _projectWorktrees.value + (projectID to list)
    }

    suspend fun selectProject(projectID: String) {
        _activeProjectID.value = projectID
        _workspace.value = null
        armAutoTakeover()
        val resp = runCatching { client.send(selectProjectRequest(newRequestId(), projectID), 10.seconds) }
            .getOrElse { return setError("selectProject: ${it.message}") }
        if (!isOk(resp.result) && resp.error != null) return setError(resp.error.message)
        refreshWorkspace(projectID)
    }

    fun clearActiveProject() {
        _activeProjectID.value = null
        _workspace.value = null
        consumeAutoTakeover() // discard any unconsumed arm
    }

    suspend fun refreshWorkspace(projectID: String) {
        val resp = runCatching { client.send(getWorkspaceRequest(newRequestId(), projectID), 10.seconds) }
            .getOrElse { return setError("getWorkspace: ${it.message}") }
        val ws = decodeWorkspace(resp.result) ?: return setError(resp.error?.message ?: "getWorkspace: unexpected response")
        _workspace.value = ws
    }

    suspend fun selectWorktree(projectID: String, worktreeID: String) {
        val resp = client.send(selectWorktreeRequest(newRequestId(), projectID, worktreeID), 15.seconds)
        if (resp.error != null) return setError(resp.error.message)
        refreshWorkspace(projectID)
    }

    suspend fun createTab(projectID: String, areaID: String? = null) {
        client.send(createTabRequest(newRequestId(), CreateTabParams(projectID, areaID)), 10.seconds)
        refreshWorkspace(projectID)
    }

    suspend fun selectTab(projectID: String, areaID: String, tabID: String) {
        armAutoTakeover()
        client.send(selectTabRequest(newRequestId(), TabRefParams(projectID, areaID, tabID)), 10.seconds)
        refreshWorkspace(projectID)
    }

    suspend fun closeTab(projectID: String, areaID: String, tabID: String) {
        client.send(closeTabRequest(newRequestId(), TabRefParams(projectID, areaID, tabID)), 10.seconds)
        refreshWorkspace(projectID)
    }

    suspend fun listBranches(projectID: String): VCSBranchesDTO {
        val resp = client.send(vcsListBranchesRequest(newRequestId(), projectID), 15.seconds)
        if (resp.error != null) error(resp.error.message)
        return decodeBranches(resp.result) ?: error("Unexpected response for vcsListBranches")
    }

    suspend fun switchBranch(projectID: String, branch: String) {
        val resp = client.send(vcsSwitchBranchRequest(newRequestId(), VCSSwitchBranchParams(projectID, branch)), 30.seconds)
        if (resp.error != null) error(resp.error.message)
        refreshWorkspace(projectID)
    }

    suspend fun createBranch(projectID: String, name: String) {
        val resp = client.send(vcsCreateBranchRequest(newRequestId(), VCSCreateBranchParams(projectID, name)), 30.seconds)
        if (resp.error != null) error(resp.error.message)
        refreshWorkspace(projectID)
    }

    suspend fun addWorktree(projectID: String, name: String, branch: String, createBranch: Boolean) {
        val resp = client.send(
            vcsAddWorktreeRequest(newRequestId(), VCSAddWorktreeParams(projectID, name, branch, createBranch)),
            60.seconds,
        )
        if (resp.error != null) error(resp.error.message)
        refreshWorktrees(projectID)
    }

    suspend fun removeWorktree(projectID: String, worktreeID: String) {
        val resp = client.send(
            vcsRemoveWorktreeRequest(newRequestId(), VCSRemoveWorktreeParams(projectID, worktreeID)),
            60.seconds,
        )
        if (resp.error != null) error(resp.error.message)
        refreshWorktrees(projectID)
    }

    private fun setError(message: String) {
        Log.w(TAG, "session error: $message")
        _lastError.value = message
    }

    fun clearError() { _lastError.value = null }

    fun applyInitialTheme(fg: Long, bg: Long, palette: List<Long>) {
        _deviceTheme.value = DeviceTheme(fg = fg, bg = bg, palette = palette)
    }

    private val panes = mutableMapOf<String, PaneSession>()

    /** Get-or-create the pane session for a given paneID. */
    fun openPane(paneID: String, cols: Int, rows: Int): PaneSession {
        panes[paneID]?.let { return it }
        val pane = PaneSession(
            paneID = paneID,
            initialCols = cols,
            initialRows = rows,
            client = client,
            scope = scope,
        )
        panes[paneID] = pane
        _deviceTheme.value?.let { t -> pane.applyTheme(t.fg, t.bg, t.palette) }
        pane.start()
        return pane
    }

    fun closePane(paneID: String) {
        panes.remove(paneID)?.stop()
    }

    fun closeAllPanes() {
        val all = panes.values.toList()
        panes.clear()
        all.forEach { it.stop() }
    }
}
