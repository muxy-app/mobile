package com.muxy.app.features.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.ThemedBorderedButton
import com.muxy.app.design.components.ThemedEmptyState
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedProminentButton
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.design.components.themedSection
import com.muxy.app.models.ProjectWorkspace
import java.util.UUID

private val projectSeparatorInset = 16.dp + projectIconSize + 16.dp

@Composable
fun ProjectListScreen(
    title: String,
    connectionName: String,
    items: List<ProjectListItem>,
    hasProjects: Boolean,
    workspaces: List<ProjectWorkspace>,
    selectedWorkspaceId: UUID?,
    status: ProjectListStatus,
    onSelect: (ProjectListItem) -> Unit,
    onSelectWorkspace: (UUID?) -> Unit,
    onRetry: () -> Unit,
    onPairAgain: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = title,
                navigationIcon = { TopBarAction(R.drawable.ic_arrow_back, "Back", onBack) },
            )
        },
    ) { padding ->
        if (!hasProjects) {
            EmptyProjects(status, connectionName, onRetry, onPairAgain, Modifier.padding(padding))
            return@Scaffold
        }
        Column(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            if (workspaces.size > 1) {
                WorkspaceFilterBar(workspaces, selectedWorkspaceId, onSelectWorkspace)
            }
            ProjectList(items, onSelect, PaddingValues(bottom = padding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun ProjectList(
    items: List<ProjectListItem>,
    onSelect: (ProjectListItem) -> Unit,
    padding: PaddingValues,
) {
    if (items.isEmpty()) {
        ThemedEmptyState(
            title = "No Projects",
            icon = R.drawable.ic_folder,
            message = "This workspace has no projects.",
            modifier = Modifier.padding(padding),
        )
        return
    }
    ThemedList(contentPadding = padding) {
        themedSection(items = items, key = { it.id }, separatorInset = projectSeparatorInset) { item ->
            ProjectRow(item = item, onClick = { onSelect(item) })
        }
    }
}

@Composable
private fun EmptyProjects(
    status: ProjectListStatus,
    connectionName: String,
    onRetry: () -> Unit,
    onPairAgain: () -> Unit,
    modifier: Modifier,
) {
    when (status) {
        ProjectListStatus.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalAppTheme.current.accent)
            }
        }

        ProjectListStatus.LoadFailed -> {
            ThemedEmptyState(
                title = "Couldn't Load Projects",
                icon = R.drawable.ic_warning,
                message = "Something went wrong loading projects from $connectionName.",
                modifier = modifier,
            ) {
                ThemedBorderedButton(text = "Retry", onClick = onRetry)
            }
        }

        ProjectListStatus.Empty -> {
            ThemedEmptyState(
                title = "No Projects",
                icon = R.drawable.ic_folder,
                message = "Projects on $connectionName will appear here.",
                modifier = modifier,
            )
        }

        ProjectListStatus.NeedsPairing -> {
            ThemedEmptyState(
                title = "Pair Again",
                icon = R.drawable.ic_desktop_mac,
                message = "This phone no longer has the pairing for $connectionName. Pair it again to reconnect.",
                modifier = modifier,
            ) {
                ThemedProminentButton(text = "Pair Again", onClick = onPairAgain)
            }
        }

        is ProjectListStatus.Disconnected -> {
            ThemedEmptyState(
                title = "Not Connected",
                icon = R.drawable.ic_wifi_off,
                message = status.message ?: "Connect to $connectionName to see its projects.",
                modifier = modifier,
            ) {
                ThemedBorderedButton(text = "Retry", onClick = onRetry)
            }
        }
    }
}
