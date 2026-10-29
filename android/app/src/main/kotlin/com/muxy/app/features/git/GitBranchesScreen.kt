package com.muxy.app.features.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.CardRow
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.themedSection

@Composable
internal fun GitBranchesScreen(
    state: GitState,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
) {
    PullToRefreshBox(state.isLoadingBranches, onRefresh) {
        ThemedList(PaddingValues(0.dp)) {
            item("new") {
                ThemedCell(RowPosition.SINGLE) {
                    CardRow("New Branch", icon = R.drawable.ic_add, enabled = !state.isBusy, navigates = true, onClick = onNew)
                }
            }
            val branches = state.branches ?: return@ThemedList
            themedSection(branches.locals, key = { "branch:$it" }, header = "Local branches") { branch ->
                ThemedListItem(
                    headline = { Text(branch) },
                    modifier =
                        Modifier.clickable(enabled = !state.isBusy && branch != branches.current, role = Role.Button) { onSelect(branch) },
                    supporting =
                        if (branch == branches.defaultBranch) {
                            { Text("Default branch") }
                        } else {
                            null
                        },
                    trailing = {
                        if (branch == branches.current) {
                            Icon(painterResource(R.drawable.ic_check), "Current branch", tint = LocalAppTheme.current.foreground)
                        }
                    },
                )
            }
        }
    }
}
