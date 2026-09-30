package com.muxy.app.features.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.models.ProjectWorkspace
import java.util.UUID

@Composable
fun WorkspaceFilterBar(
    workspaces: List<ProjectWorkspace>,
    selectedWorkspaceId: UUID?,
    onSelect: (UUID?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            WorkspaceChip(title = "All", isSelected = selectedWorkspaceId == null) { onSelect(null) }
        }
        items(workspaces, key = { it.id }) { workspace ->
            WorkspaceChip(title = workspace.name, isSelected = selectedWorkspaceId == workspace.id) { onSelect(workspace.id) }
        }
    }
}

@Composable
private fun WorkspaceChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Text(
        text = title,
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(if (isSelected) theme.accent else theme.secondaryGroupedBackground)
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (isSelected) theme.onAccent else theme.foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
