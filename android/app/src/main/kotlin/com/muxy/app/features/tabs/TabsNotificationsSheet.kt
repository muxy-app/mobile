package com.muxy.app.features.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ListRow
import com.muxy.app.design.components.PageMargin
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.TabKindIcon
import com.muxy.app.design.components.ThemedBottomSheet
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedListItem
import java.time.Duration
import java.time.Instant

@Composable
internal fun TabsNotificationsSheet(
    items: List<TabsAttention>,
    onOpen: (TabsAttention) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalAppTheme.current
    ThemedBottomSheet(title = "Notifications", onClose = onClose) {
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PageMargin.horizontal, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("No notifications", style = MaterialTheme.typography.titleMedium, color = theme.foreground)
                Text(
                    "Agents waiting for you or finished show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.secondaryForeground,
                )
            }
            return@ThemedBottomSheet
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            itemsIndexed(items, key = { _, item -> item.tab.id }) { index, item ->
                ThemedCell(RowPosition.of(index, items.size), Modifier.padding(horizontal = PageMargin.horizontal)) {
                    NotificationRow(item) { onOpen(item) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: TabsAttention,
    onOpen: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val context =
        listOfNotNull(
            item.project.name,
            item.device.connection.name,
            item.tab.activity.since
                ?.let(::relativeAge),
        )
    ThemedListItem(
        headline = { Text(item.tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supporting = {
            Column {
                Text(
                    item.tab.activity.statusLabel
                        .orEmpty(),
                    color = activityColor(item.tab.activity) ?: theme.secondaryForeground,
                    maxLines = 1,
                )
                Text(
                    context.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leading = {
            Box(modifier = Modifier.size(ListRow.iconSize), contentAlignment = Alignment.Center) {
                TabKindIcon(kind = item.tab.tile, activity = item.tab.activity, size = ListRow.iconSize)
            }
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
    )
}

private fun relativeAge(since: Instant): String {
    val minutes = Duration.between(since, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
}
