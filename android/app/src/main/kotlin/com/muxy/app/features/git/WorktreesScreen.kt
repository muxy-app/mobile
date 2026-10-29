package com.muxy.app.features.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.CardRow
import com.muxy.app.design.components.ListRow
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedAlertDialog
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedChevron
import com.muxy.app.design.components.ThemedDialogAction
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.ThemedSectionFooter
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
            item("new") {
                ThemedCell(RowPosition.SINGLE) {
                    CardRow("New Worktree", icon = R.drawable.ic_add, enabled = !state.isBusy, navigates = true, onClick = onNew)
                }
            }
            themedSection(state.rows.orEmpty(), key = { it.id }, header = "Worktrees") { row ->
                WorktreeListRow(row, state.isBusy, { onOpen(row) }, { onRemove(row) })
            }
            if (state.rows?.isEmpty() == true) item("empty") { ThemedSectionFooter("No worktrees") }
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
                Icon(painterResource(R.drawable.ic_delete), "Remove", tint = Color.White)
            }
        },
    ) {
        ThemedListItem(
            headline = { Text(row.name) },
            modifier =
                Modifier
                    .background(theme.secondaryGroupedBackground)
                    .clickable(enabled = !busy && row.isOpenable, role = Role.Button) { onOpen() }
                    .semantics {
                        if (row.isRemovable && !busy) {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Remove") {
                                        onRemove()
                                        true
                                    },
                                )
                        }
                    },
            supporting = { Text(listOfNotNull(row.branch, if (!row.isRegistered) "Not in Muxy" else null).joinToString(" · ")) },
            leading = { Icon(painterResource(R.drawable.ic_fork_right), null, Modifier.size(ListRow.iconSize)) },
            trailing = {
                if (row.isCurrent) {
                    Icon(painterResource(R.drawable.ic_check), "Current worktree", tint = theme.foreground)
                } else if (row.isOpenable) {
                    ThemedChevron()
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
    ThemedAlertDialog(
        title = "Remove ${pending.row.name}?",
        message = pending.confirmationMessage,
        onDismissRequest = onCancel,
        confirm = ThemedDialogAction("Remove", onRemove, isDestructive = true),
        dismiss = listOf(ThemedDialogAction("Cancel", onCancel)),
    )
}
