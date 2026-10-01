package com.muxy.app.features.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.concurrency.inViewModelScope
import com.muxy.app.core.logging.Log
import com.muxy.app.models.FileChange
import com.muxy.app.models.FileScope
import com.muxy.app.models.RemoteFileEntry
import com.muxy.app.models.RemoteTextFile
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FileManagerViewModel(
    location: FileLocation,
    backend: FileBackend,
    private val loadLocation: suspend () -> FileLocation = { location },
) : ViewModel() {
    private val client = FileClient(backend)
    private val mutableState = MutableStateFlow(FileManagerState(location))
    val state = mutableState.asStateFlow()
    private val current get() = mutableState.value
    private var activeScope: FileScope? = null
    private var contextRequest = 0L
    private var directoryRequest = 0L
    private var previewRequest = 0L
    private var moveRequest = 0L
    private var draftRevision = 0L
    private var directoryPath: String? = null
    private var moveDirectoryPath: String? = null
    private var refreshJob: Job? = null
    private var debounceJob: Job? = null
    private var pendingDirectory = false
    private var pendingPreview = false

    init {
        viewModelScope.launch {
            backend.events.catch { report(it) }.collect { event ->
                when (event) {
                    is FileBackendEvent.ScopeChanged -> {
                        if (activeScope != event.scope) contextRequest += 1
                        val changed = updateScope(event.scope)
                        if (!current.locationReady) {
                            refreshContext()
                        } else if (changed) {
                            refreshDirectory()
                        }
                    }

                    is FileBackendEvent.FilesChanged -> {
                        receive(event.change)
                    }
                }
            }
        }
        viewModelScope.launch {
            backend.connected.collectLatest { connected ->
                mutableState.update { it.copy(isConnected = connected) }
                if (!connected) {
                    mutableState.update { it.copy(scopeReady = false) }
                    invalidateContext()
                    return@collectLatest
                }
                refreshContext()
            }
        }
    }

    suspend fun goToDirectory(path: String) {
        if (!current.canMutate || current.isDirty) return
        directoryPath = null
        mutableState.update { it.copy(currentPath = path, entries = emptyList(), selectedPaths = emptySet(), selectionMode = false) }
        refreshDirectory()
    }

    suspend fun refreshDirectory(clearError: Boolean = true) {
        if (!current.isConnected || current.hasContextChanged) return
        val request = ++directoryRequest
        val context = current.contextId
        val path = current.currentPath
        mutableState.update { it.copy(isLoadingDirectory = true, errorMessage = if (clearError) null else it.errorMessage) }
        try {
            attempt { client.list(path) }
                .onSuccess { loaded ->
                    if (!isCurrent(context) || directoryRequest != request) return@onSuccess
                    directoryPath = loaded.path
                    mutableState.update {
                        it.copy(
                            entries = loaded.entries,
                            selectedPaths =
                                it.selectedPaths.intersect(
                                    loaded.entries
                                        .map { entry ->
                                            entry.path
                                        }.toSet(),
                                ),
                        )
                    }
                }.onFailure { if (isCurrent(context) && directoryRequest == request) report(it) }
        } finally {
            if (directoryRequest == request) mutableState.update { it.copy(isLoadingDirectory = false) }
        }
    }

    suspend fun open(entry: RemoteFileEntry) {
        if (!current.canMutate) return
        if (current.selectionMode) {
            toggleSelection(entry.path)
            return
        }
        if (entry.isDirectory) {
            goToDirectory(entry.path)
            return
        }
        mutableState.update { it.copy(preview = FilePreviewState(entry), routes = listOf(FileRoute.BROWSER, FileRoute.PREVIEW)) }
        reloadPreview()
    }

    fun toggleSelection(path: String) {
        if (!current.canMutate) return
        mutableState.update {
            it.copy(
                selectionMode = true,
                selectedPaths =
                    if (path in
                        it.selectedPaths
                    ) {
                        it.selectedPaths - path
                    } else {
                        it.selectedPaths + path
                    },
            )
        }
    }

    fun toggleSelectionMode() {
        if (current.isBusy || (!current.selectionMode && !current.canMutate)) return
        mutableState.update { it.copy(selectionMode = !it.selectionMode, selectedPaths = emptySet()) }
    }

    suspend fun reloadPreview(discard: Boolean = false) {
        val preview = current.preview ?: return
        if (!current.isConnected || current.hasContextChanged || current.isBusy) return
        if (preview.isDirty && !discard) {
            updatePreview { it.copy(hasExternalChanges = true) }
            return
        }
        val request = ++previewRequest
        val context = current.contextId
        val revision = draftRevision
        mutableState.update { it.copy(isLoadingPreview = true, errorMessage = null) }
        try {
            val result =
                attempt {
                    val stat = client.stat(preview.entry.path)
                    val loaded =
                        if (RemoteFilePath.isImage(preview.entry.path)) {
                            preview.copy(
                                image = client.readData(preview.entry.path),
                                text = null,
                                isUnsupported = false,
                                hasExternalChanges = false,
                            )
                        } else {
                            preview.showing(client.readText(preview.entry.path))
                        }
                    loaded.copy(stat = stat)
                }
            if (!isCurrent(context) || previewRequest != request) return
            if (revision != draftRevision) {
                updatePreview { it.copy(hasExternalChanges = true) }
                return
            }
            result.onSuccess { loaded -> mutableState.update { it.copy(preview = loaded) } }.onFailure { error ->
                if (error is FileException.NotText) {
                    updatePreview { it.copy(text = null, image = null, isUnsupported = true, isEditing = false, draft = "") }
                    return@onFailure
                }
                if (!current.isDirty) updatePreview { it.copy(text = null, image = null, isEditing = false) }
                report(error)
            }
        } finally {
            if (previewRequest == request) mutableState.update { it.copy(isLoadingPreview = false) }
        }
    }

    fun beginEditing() {
        val preview = current.preview ?: return
        if (!current.canMutate || current.isLoadingPreview || preview.text == null) return
        updatePreview { it.copy(isEditing = true, draft = it.text?.text.orEmpty()) }
    }

    fun updateDraft(text: String) {
        if (current.isBusy || current.preview?.isEditing != true || current.preview?.draft == text) return
        draftRevision += 1
        updatePreview { it.copy(draft = text) }
    }

    fun toggleWrap() = updatePreview { it.copy(wrapsLines = !it.wrapsLines) }

    fun keepEditing() = updatePreview { it.copy(hasExternalChanges = false) }

    suspend fun save(): Boolean =
        inViewModelScope {
            val preview = current.preview ?: return@inViewModelScope false
            if (!preview.isEditing || preview.text == null) return@inViewModelScope false
            val scope = activeScope ?: return@inViewModelScope false
            val context = current.contextId
            return@inViewModelScope mutate {
                client.write(preview.entry.path, preview.draft, scope)
                if (!isCurrent(context)) return@mutate
                updatePreview {
                    it.showing(
                        RemoteTextFile(
                            preview.entry.path,
                            preview.draft,
                            preview.draft
                                .toByteArray()
                                .size
                                .toLong(),
                        ),
                    )
                }
                refreshDirectory()
            }
        }

    suspend fun create(
        name: String,
        isDirectory: Boolean,
    ): Boolean =
        inViewModelScope {
            val context = current.contextId
            val scope = activeScope ?: return@inViewModelScope false
            var createdPath = ""
            val succeeded =
                mutate {
                    val valid = RemoteFilePath.validatedName(name)
                    if (!isDirectory && current.entries.any { it.name.equals(valid, ignoreCase = true) }) {
                        throw FileException.Message("“$valid” already exists in this folder.")
                    }
                    val path = RemoteFilePath.join(current.currentPath, valid)
                    createdPath = if (isDirectory) client.mkdir(path, scope) else client.create(path, scope)
                }
            if (!succeeded) return@inViewModelScope false
            refreshDirectory()
            if (!isCurrent(context)) return@inViewModelScope false
            if (isDirectory) return@inViewModelScope true
            mutableState.update { it.copy(selectionMode = false, selectedPaths = emptySet()) }
            open(RemoteFileEntry(RemoteFilePath.name(createdPath), createdPath, false, false))
            beginEditing()
            return@inViewModelScope true
        }

    suspend fun rename(
        entry: RemoteFileEntry,
        name: String,
    ): Boolean =
        inViewModelScope {
            val scope = activeScope ?: return@inViewModelScope false
            if (current.isDirty || !mutate { client.rename(entry.path, name, scope) }) return@inViewModelScope false
            returnToBrowser()
            return@inViewModelScope true
        }

    suspend fun delete(paths: List<String>): Unit =
        inViewModelScope {
            val scope = activeScope ?: return@inViewModelScope
            if (current.isDirty || !mutate { client.delete(paths, scope) }) return@inViewModelScope
            returnToBrowser()
        }

    suspend fun startMove(paths: List<String>) {
        if (!current.canMutate || paths.isEmpty() || current.preview?.isEditing == true) return
        mutableState.update {
            it.copy(
                movingPaths = paths,
                routes =
                    it.routes.filterNot { route -> route == FileRoute.MOVE } + FileRoute.MOVE,
            )
        }
        goToMoveDirectory("")
    }

    suspend fun goToMoveDirectory(path: String) {
        if (!current.canMutate || current.movingPaths.any { RemoteFilePath.contains(path, it) }) return
        moveDirectoryPath = null
        mutableState.update { it.copy(movePath = path, moveEntries = emptyList()) }
        refreshMoveDirectory()
    }

    suspend fun moveHere(): Unit =
        inViewModelScope {
            if (!current.canMoveHere) return@inViewModelScope
            val scope = activeScope ?: return@inViewModelScope
            val paths = current.movingPaths
            val destination = current.movePath
            if (!mutate { client.move(paths, destination, scope) }) return@inViewModelScope
            returnToBrowser()
        }

    suspend fun goBack() {
        if (current.isBusy) return
        if (current.route == FileRoute.MOVE) {
            moveRequest += 1
            mutableState.update { it.copy(routes = it.routes.dropLast(1), movingPaths = emptyList(), moveEntries = emptyList()) }
            return
        }
        if (current.route == FileRoute.PREVIEW) {
            returnToBrowser()
            return
        }
        if (current.selectionMode) {
            toggleSelectionMode()
            return
        }
        goToDirectory(RemoteFilePath.parent(current.currentPath))
    }

    suspend fun returnToBrowser() {
        if (current.isBusy) return
        previewRequest += 1
        moveRequest += 1
        val changed = current.hasContextChanged
        mutableState.update {
            it.copy(
                routes = listOf(FileRoute.BROWSER),
                preview = null,
                movingPaths = emptyList(),
                moveEntries = emptyList(),
                selectedPaths = emptySet(),
                selectionMode = false,
                isLoadingPreview = false,
                isLoadingMove = false,
                currentPath = if (changed) "" else it.currentPath,
                hasContextChanged = false,
            )
        }
        if (changed) refreshContext() else refreshDirectory()
    }

    suspend fun refresh() {
        if (current.isBusy) return
        when (current.route) {
            FileRoute.BROWSER -> {
                when {
                    current.hasContextChanged -> returnToBrowser()
                    !current.scopeReady || !current.locationReady -> refreshContext()
                    else -> refreshDirectory()
                }
            }

            FileRoute.PREVIEW -> {
                reloadPreview()
            }

            FileRoute.MOVE -> {
                refreshMoveDirectory()
            }
        }
    }

    private suspend fun refreshMoveDirectory() {
        if (current.route != FileRoute.MOVE || !current.isConnected || current.hasContextChanged) return
        val request = ++moveRequest
        val context = current.contextId
        val path = current.movePath
        mutableState.update { it.copy(isLoadingMove = true) }
        try {
            attempt { client.list(path) }
                .onSuccess { loaded ->
                    if (!isCurrent(context) || moveRequest != request) return@onSuccess
                    moveDirectoryPath = loaded.path
                    mutableState.update { it.copy(moveEntries = loaded.entries.filter { entry -> entry.isDirectory }) }
                }.onFailure { if (isCurrent(context) && moveRequest == request) report(it) }
        } finally {
            if (moveRequest == request) mutableState.update { it.copy(isLoadingMove = false) }
        }
    }

    private suspend fun mutate(operation: suspend () -> Unit): Boolean {
        if (!current.canMutate) return false
        val context = current.contextId
        mutableState.update { it.copy(isBusy = true, errorMessage = null) }
        try {
            val result = attempt { operation() }
            if (!isCurrent(context)) return false
            result
                .onFailure {
                    if (it is FileException.WorktreeChanged) {
                        mutableState.update { state -> state.copy(hasContextChanged = true) }
                    } else {
                        refreshDirectory(clearError = false)
                        refreshMoveDirectory()
                    }
                    if (isCurrent(context)) report(it)
                }.onSuccess { Log.files.debug("File operation completed") }
            return result.isSuccess
        } finally {
            mutableState.update { it.copy(isBusy = false) }
        }
    }

    private suspend fun refreshContext() {
        val request = ++contextRequest
        val context = current.contextId
        attempt { client.backend.currentScope() }
            .onSuccess { scope ->
                if (!isCurrent(context) || contextRequest != request) return@onSuccess
                updateScope(scope)
                val updatedContext = current.contextId
                val location = attempt { loadLocation() }
                if (!isCurrent(updatedContext) || contextRequest != request) return@onSuccess
                location.onFailure { report(it) }
                if (location.isFailure || current.hasContextChanged) return@onSuccess
                mutableState.update { it.copy(location = location.getOrThrow(), locationReady = true) }
                refreshDirectory()
                if (!isCurrent(updatedContext) || contextRequest != request) return@onSuccess
                if (current.isDirty) {
                    updatePreview { it.copy(hasExternalChanges = true) }
                } else if (current.route == FileRoute.PREVIEW) {
                    reloadPreview()
                }
                refreshMoveDirectory()
            }.onFailure { if (isCurrent(context) && contextRequest == request) report(it) }
    }

    private fun updateScope(scope: FileScope): Boolean {
        val previous = activeScope
        activeScope = scope
        mutableState.update { it.copy(scopeReady = true) }
        if (previous == null || previous == scope) return false
        invalidateContext()
        if (current.isDirty || (current.isBusy && current.preview != null)) {
            mutableState.update { it.copy(hasContextChanged = true) }
            return false
        }
        directoryPath = null
        moveDirectoryPath = null
        mutableState.update {
            it.copy(
                routes = listOf(FileRoute.BROWSER),
                currentPath = "",
                entries = emptyList(),
                preview = null,
                movingPaths = emptyList(),
                moveEntries = emptyList(),
                selectedPaths = emptySet(),
                selectionMode = false,
                hasContextChanged = false,
            )
        }
        return true
    }

    private fun receive(change: FileChange) {
        if (current.hasContextChanged || change.scope != activeScope) return
        val preview = current.preview
        if (preview != null) {
            pendingPreview = pendingPreview || change.requiresRescan ||
                change.paths.any {
                    RemoteFilePath.contains(preview.entry.path, it) ||
                        preview.text?.let { text -> RemoteFilePath.contains(text.path, it) } == true
                }
        }
        pendingDirectory =
            pendingDirectory || change.requiresRescan || current.isLoadingDirectory || current.isLoadingMove ||
            change.paths.any {
                affectsDirectory(it, current.currentPath, directoryPath) ||
                    (current.route == FileRoute.MOVE && affectsDirectory(it, current.movePath, moveDirectoryPath))
            }
        debounceJob?.cancel()
        debounceJob =
            viewModelScope.launch {
                delay(CHANGE_DEBOUNCE)
                refreshJob?.join()
                while (current.isBusy) delay(CHANGE_DEBOUNCE)
                if (!current.isConnected || current.hasContextChanged) return@launch
                refreshJob = viewModelScope.launch { refreshChanges() }
            }
    }

    private suspend fun refreshChanges() {
        val previewNeeded = pendingPreview
        val directoryNeeded = pendingDirectory
        pendingPreview = false
        pendingDirectory = false
        if (previewNeeded) reloadPreview()
        if (directoryNeeded) {
            refreshDirectory(clearError = false)
            refreshMoveDirectory()
        }
    }

    private fun affectsDirectory(
        path: String,
        directory: String,
        resolved: String?,
    ): Boolean = RemoteFilePath.affectsDirectory(path, directory) || resolved?.let { RemoteFilePath.affectsDirectory(path, it) } == true

    private fun invalidateContext() {
        directoryRequest += 1
        previewRequest += 1
        moveRequest += 1
        debounceJob?.cancel()
        debounceJob = null
        refreshJob?.cancel()
        refreshJob = null
        pendingPreview = false
        pendingDirectory = false
        mutableState.update {
            it.copy(
                contextId = it.contextId + 1,
                isLoadingDirectory = false,
                isLoadingPreview = false,
                isLoadingMove = false,
            )
        }
    }

    private fun isCurrent(context: Long): Boolean = current.isConnected && current.contextId == context

    private fun updatePreview(update: (FilePreviewState) -> FilePreviewState) {
        mutableState.update { it.copy(preview = it.preview?.let(update)) }
    }

    private fun report(error: Throwable) {
        val message = (error as? ProtocolException)?.body?.message ?: error.message ?: "Something went wrong. Try again."
        mutableState.update { it.copy(errorMessage = message) }
        Log.files.error("File operation failed: ${error.javaClass.simpleName}")
    }

    private companion object {
        const val CHANGE_DEBOUNCE = 180L
    }
}
