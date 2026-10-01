package com.muxy.app.features.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.concurrency.inViewModelScope
import com.muxy.app.core.logging.Log
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorktreesState(
    val rows: List<WorktreeRow>? = null,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val pendingRemoval: PendingWorktreeRemoval? = null,
    val errorMessage: String? = null,
    val revision: Long = 0,
    val completedForm: GitFormCompletion? = null,
)

class WorktreesViewModel(
    private val backend: WorktreeBackend,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WorktreesState())
    val state = mutableState.asStateFlow()
    val requiresName get() = backend.requiresName
    private var request = 0L

    fun acknowledgeFormCompletion(completion: GitFormCompletion): Boolean {
        if (mutableState.value.completedForm != completion) return false
        mutableState.update { it.copy(completedForm = null) }
        return true
    }

    init {
        viewModelScope.launch {
            attempt { backend.cached() }
                .onSuccess { rows ->
                    mutableState.update { if (it.rows == null) it.copy(rows = rows) else it }
                }.onFailure(::report)
            backend.changes.catch { report(it) }.collectLatest {
                while (mutableState.value.isBusy) delay(REFRESH_DELAY)
                refresh()
            }
        }
    }

    suspend fun refresh() {
        if (mutableState.value.isBusy) return
        load()
    }

    suspend fun open(row: WorktreeRow): String? {
        if (!row.isOpenable) return null
        var projectId: String? = null
        change {
            projectId = backend.open(row)
            load()
        }
        return projectId
    }

    suspend fun create(
        name: String,
        branch: String,
        createsBranch: Boolean,
    ): Boolean {
        if (branch.isBlank() || (requiresName && name.isBlank())) return false
        return change {
            backend.create(name.trim(), branch.trim(), createsBranch)
            load()
            mutableState.update { it.copy(completedForm = GitFormCompletion(GitRoute.NewWorktree)) }
        }
    }

    suspend fun prepareRemoval(row: WorktreeRow) {
        if (!row.isRemovable) return
        change(incrementsRevision = false) {
            val pending = backend.prepareRemoval(row)
            mutableState.update { it.copy(pendingRemoval = pending) }
        }
    }

    fun cancelRemoval() {
        if (mutableState.value.isBusy) return
        mutableState.update { it.copy(pendingRemoval = null) }
    }

    suspend fun remove() {
        val pending = mutableState.value.pendingRemoval ?: return
        change {
            mutableState.update { it.copy(pendingRemoval = null) }
            pending.remove()
            load()
        }
    }

    private suspend fun load() {
        val token = ++request
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            attempt { backend.rows() }
                .onSuccess { rows ->
                    if (request == token) mutableState.update { it.copy(rows = rows) }
                }.onFailure { if (request == token) report(it) }
        } finally {
            if (request == token) mutableState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun change(
        incrementsRevision: Boolean = true,
        operation: suspend () -> Unit,
    ): Boolean =
        inViewModelScope {
            if (mutableState.value.isBusy) return@inViewModelScope false
            request += 1
            mutableState.update { it.copy(isBusy = true, isLoading = false, errorMessage = null) }
            try {
                return@inViewModelScope attempt { operation() }
                    .onSuccess {
                        if (incrementsRevision) mutableState.update { it.copy(revision = it.revision + 1) }
                        Log.client.debug("Worktree operation completed")
                    }.onFailure(::report)
                    .isSuccess
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }

    private fun report(error: Throwable) {
        mutableState.update { it.copy(errorMessage = (error as? ProtocolException)?.body?.message ?: error.message) }
        Log.client.error("Worktree operation failed: ${error.javaClass.simpleName}")
    }

    private companion object {
        const val REFRESH_DELAY = 180L
    }
}
