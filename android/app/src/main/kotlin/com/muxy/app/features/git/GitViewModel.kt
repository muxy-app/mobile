package com.muxy.app.features.git

import androidx.lifecycle.ViewModel
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.concurrency.inViewModelScope
import com.muxy.app.core.logging.Log
import com.muxy.app.models.GitDiffKey
import com.muxy.app.models.VcsBranches
import com.muxy.app.models.VcsDiff
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.models.VcsPrCreated
import com.muxy.app.models.VcsPullRequest
import com.muxy.app.models.VcsStatus
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GitState(
    val status: VcsStatus? = null,
    val branches: VcsBranches? = null,
    val diffs: Map<GitDiffKey, VcsDiff> = emptyMap(),
    val isLoadingStatus: Boolean = false,
    val isLoadingBranches: Boolean = false,
    val loadingDiffs: Set<GitDiffKey> = emptySet(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val completedForm: GitFormCompletion? = null,
) {
    val totalChanges: Int get() = status?.let { (it.stagedFiles + it.changedFiles).map { file -> file.path }.toSet().size } ?: 0
}

class GitViewModel(
    private val backend: GitBackend,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GitState())
    val state = mutableState.asStateFlow()
    private var statusRequest = 0L
    private var branchesRequest = 0L
    private var diffGeneration = 0L
    private val diffRequests = mutableMapOf<GitDiffKey, Long>()

    fun acknowledgeFormCompletion(completion: GitFormCompletion): Boolean {
        if (mutableState.value.completedForm != completion) return false
        mutableState.update { it.copy(completedForm = null) }
        return true
    }

    fun canPush(status: VcsStatus): Boolean = status.aheadCount > 0 || publishes(status)

    fun pushTitle(status: VcsStatus): String =
        when {
            publishes(status) -> "Publish Branch"
            status.aheadCount > 0 -> "Push ${status.aheadCount}"
            else -> "Push"
        }

    suspend fun refreshStatus() {
        val request = ++statusRequest
        mutableState.update { it.copy(isLoadingStatus = true, errorMessage = null) }
        try {
            attempt { backend.status() }
                .onSuccess { status ->
                    if (request == statusRequest) mutableState.update { it.copy(status = status) }
                }.onFailure {
                    if (request != statusRequest) return@onFailure
                    if (it is GitException.NotRepository) mutableState.update { state -> state.copy(status = null) }
                    report(it)
                }
        } finally {
            if (request == statusRequest) mutableState.update { it.copy(isLoadingStatus = false) }
        }
    }

    suspend fun refreshBranches() {
        val request = ++branchesRequest
        mutableState.update { it.copy(isLoadingBranches = true, errorMessage = null) }
        try {
            attempt { backend.branches() }
                .onSuccess { branches ->
                    if (request == branchesRequest) mutableState.update { it.copy(branches = branches) }
                }.onFailure { if (request == branchesRequest) report(it) }
        } finally {
            if (request == branchesRequest) mutableState.update { it.copy(isLoadingBranches = false) }
        }
    }

    suspend fun loadDiff(
        key: GitDiffKey,
        full: Boolean = false,
    ) {
        val generation = diffGeneration
        val request = (diffRequests[key] ?: 0) + 1
        diffRequests[key] = request
        mutableState.update { it.copy(loadingDiffs = it.loadingDiffs + key, errorMessage = null) }
        try {
            attempt { backend.diff(key, full) }
                .onSuccess { diff ->
                    if (generation == diffGeneration &&
                        diffRequests[key] == request
                    ) {
                        mutableState.update { it.copy(diffs = it.diffs + (key to diff)) }
                    }
                }.onFailure { if (generation == diffGeneration && diffRequests[key] == request) report(it) }
        } finally {
            if (diffRequests[key] == request) mutableState.update { it.copy(loadingDiffs = it.loadingDiffs - key) }
        }
    }

    fun invalidateDiffs() {
        diffGeneration += 1
        mutableState.update { it.copy(diffs = emptyMap()) }
    }

    suspend fun commit(
        message: String,
        stageAll: Boolean,
    ): Boolean {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return false
        return change {
            backend.commit(trimmed, stageAll)
            invalidateDiffs()
            refreshStatus()
            mutableState.update { it.copy(completedForm = GitFormCompletion(GitRoute.Commit)) }
        }
    }

    suspend fun pull(): Boolean =
        change {
            backend.pull()
            invalidateDiffs()
            refreshStatus()
        }

    suspend fun push(): Boolean =
        change {
            backend.push()
            refreshStatus()
        }

    suspend fun switchBranch(branch: String): Boolean =
        change {
            backend.switchBranch(branch)
            invalidateDiffs()
            refreshStatus()
            refreshBranches()
        }

    suspend fun createBranch(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return change {
            backend.createBranch(trimmed)
            invalidateDiffs()
            refreshStatus()
            refreshBranches()
            mutableState.update { it.copy(completedForm = GitFormCompletion(GitRoute.NewBranch)) }
        }
    }

    suspend fun createPullRequest(
        title: String,
        body: String,
        baseBranch: String?,
        draft: Boolean,
    ): VcsPrCreated? {
        if (title.isBlank()) return null
        var created: VcsPrCreated? = null
        change {
            val result = backend.createPullRequest(title.trim(), body.trim(), baseBranch?.trim()?.takeIf { it.isNotEmpty() }, draft)
            created = result
            refreshStatus()
            mutableState.update { it.copy(completedForm = GitFormCompletion(GitRoute.NewPullRequest, result.url)) }
        }
        return created
    }

    suspend fun mergePullRequest(
        pullRequest: VcsPullRequest,
        method: VcsMergeMethod,
        deleteBranch: Boolean,
    ): Boolean =
        change {
            backend.mergePullRequest(pullRequest, method, deleteBranch)
            invalidateDiffs()
            refreshStatus()
        }

    private suspend fun change(operation: suspend () -> Unit): Boolean =
        inViewModelScope {
            if (mutableState.value.isBusy) return@inViewModelScope false
            statusRequest += 1
            branchesRequest += 1
            mutableState.update { it.copy(isBusy = true, errorMessage = null, isLoadingStatus = false, isLoadingBranches = false) }
            try {
                return@inViewModelScope attempt { operation() }.onFailure(::report).isSuccess
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }

    private fun publishes(status: VcsStatus): Boolean = backend.canPublishBranch && !status.hasUpstream

    private fun report(error: Throwable) {
        mutableState.update { it.copy(errorMessage = (error as? ProtocolException)?.body?.message ?: error.message) }
        Log.client.error("Git operation failed: ${error.javaClass.simpleName}")
    }
}
