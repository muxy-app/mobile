package com.muxy.app.features.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.themedSection

@Composable
internal fun WorktreesScreen(
    state: WorktreesState,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onOpen: (WorktreeRow) -> Unit,
    onRemove: (WorktreeRow) -> Unit,
) {
    PullToRefreshBox(state.isLoading, onRefresh) {
        ThemedList(PaddingValues(0.dp)) {
            item("new") { GitActionRow("New Worktree", R.drawable.ic_add, !state.isBusy, onNew) }
            themedSection(state.rows.orEmpty(), key = { it.id }, header = "Worktrees") { row ->
                WorktreeListRow(row, state.isBusy, { onOpen(row) }, { onRemove(row) })
            }
            if (state.rows?.isEmpty() == true) item("empty") { Text("No worktrees") }
        }
    }
}

@Composable
private fun WorktreeListRow(
    row: WorktreeRow,
    busy: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.settledValue) {
        if (swipe.settledValue != SwipeToDismissBoxValue.EndToStart) return@LaunchedEffect
        if (row.isRemovable && !busy) onRemove()
        swipe.reset()
    }
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = row.isRemovable && !busy,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(theme.red).padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(painterResource(R.drawable.ic_delete), "Remove", tint = theme.onAccent)
            }
        },
    ) {
        ThemedListItem(
            headline = { Text(row.name) },
            modifier = Modifier.background(theme.secondaryGroupedBackground).clickable(enabled = !busy && row.isOpenable) { onOpen() },
            supporting = { Text(listOfNotNull(row.branch, if (!row.isRegistered) "Not in Muxy" else null).joinToString(" · ")) },
            leading = { Icon(painterResource(R.drawable.ic_fork_right), null) },
            trailing = {
                if (row.isCurrent) {
                    Icon(painterResource(R.drawable.ic_check), "Current worktree")
                } else if (row.isRemovable) {
                    TextButton(onClick = onRemove, enabled = !busy) { Text("Remove") }
                }
            },
        )
    }
}

@Composable
internal fun WorktreeRemovalDialog(
    pending: PendingWorktreeRemoval,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Remove ${pending.row.name}?") },
        text = { Text(pending.confirmationMessage) },
        confirmButton = { TextButton(onClick = onRemove) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
