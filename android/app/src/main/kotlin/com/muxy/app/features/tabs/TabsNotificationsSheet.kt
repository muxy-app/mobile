package com.muxy.app.features.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.TabKindTile
import com.muxy.app.design.components.TopBarAction
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabsNotificationsSheet(
    items: List<TabsAttention>,
    onOpen: (TabsAttention) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalAppTheme.current
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = theme.groupedBackground,
        contentColor = theme.foreground,
    ) {
        Row(modifier = Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notifications",
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            TopBarAction(R.drawable.ic_close, "Close", onClose)
        }
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("No notifications", style = MaterialTheme.typography.titleMedium, color = theme.foreground)
                Text(
                    "Agents waiting for you or finished show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.secondaryForeground,
                )
            }
            return@ModalBottomSheet
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            itemsIndexed(items, key = { _, item -> item.tab.id }) { index, item ->
                NotificationRow(item, segmentShape(index, items.size)) { onOpen(item) }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: TabsAttention,
    shape: Shape,
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 1.dp)
                .clip(shape)
                .background(theme.secondaryGroupedBackground)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabKindTile(kind = item.tab.tile, activity = item.tab.activity)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.tab.title,
                color = theme.foreground,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.tab.activity.statusLabel
                    .orEmpty(),
                color = activityColor(item.tab.activity) ?: theme.secondaryForeground,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                context.joinToString(" · "),
                color = theme.secondaryForeground,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
