package com.muxy.app.features.git

import kotlinx.coroutines.flow.Flow

data class WorktreeRow(
    val id: String,
    val name: String,
    val branch: String?,
    val projectId: String?,
    val isCurrent: Boolean,
    val isOpenable: Boolean,
    val isRemovable: Boolean,
    val isRegistered: Boolean = true,
)

class PendingWorktreeRemoval(
    val row: WorktreeRow,
    val hasUncommittedChanges: Boolean,
    val endsTerminals: Boolean = false,
    internal val remove: suspend () -> Unit,
) {
    val confirmationMessage: String get() {
        if (endsTerminals) {
            val consequence = "“${row.name}” and its folder will be deleted, and its terminals will end."
            if (!hasUncommittedChanges) return consequence
            return "$consequence It has uncommitted changes that will be lost."
        }
        if (hasUncommittedChanges) {
            return "This worktree has uncommitted changes. Removing it permanently deletes those changes and its files."
        }
        return "This removes the worktree and its files."
    }
}

interface WorktreeBackend {
    val requiresName: Boolean
    val changes: Flow<Unit>

    suspend fun cached(): List<WorktreeRow>?

    suspend fun rows(): List<WorktreeRow>

    suspend fun open(row: WorktreeRow): String?

    suspend fun create(
        name: String,
        branch: String,
        createsBranch: Boolean,
    )

    suspend fun prepareRemoval(row: WorktreeRow): PendingWorktreeRemoval
}
