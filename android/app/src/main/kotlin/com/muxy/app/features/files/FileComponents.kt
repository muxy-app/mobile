package com.muxy.app.features.files

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.models.RemoteFileEntry

@Composable
internal fun FileBreadcrumbs(
    path: String,
    rootName: String,
    enabled: Boolean,
    onOpen: (String) -> Unit,
) {
    val components = path.split('/').filter { it.isNotEmpty() }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        TextButton(onClick = { onOpen("") }, enabled = enabled) { Text(rootName) }
        components.forEachIndexed { index, name ->
            Text("/", Modifier.padding(vertical = 12.dp), color = LocalAppTheme.current.secondaryForeground)
            TextButton(onClick = { onOpen(components.take(index + 1).joinToString("/")) }, enabled = enabled) { Text(name) }
        }
    }
}

@Composable
internal fun FileEntryRow(
    entry: RemoteFileEntry,
    enabled: Boolean,
    selecting: Boolean = false,
    selected: Boolean = false,
    onOpen: () -> Unit,
    onSelect: () -> Unit = {},
) {
    ThemedListItem(
        headline = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.combinedClickable(enabled = enabled, onClick = onOpen, onLongClick = onSelect),
        supporting =
            if (entry.isIgnored) {
                { Text("Ignored by Git", style = MaterialTheme.typography.labelSmall) }
            } else {
                null
            },
        leading = { Icon(painterResource(fileIcon(entry)), null, tint = LocalAppTheme.current.accent) },
        trailing = {
            if (selecting) {
                Checkbox(selected, onCheckedChange = { onSelect() }, enabled = enabled)
            } else if (entry.isDirectory) {
                Icon(painterResource(R.drawable.ic_arrow_forward), null)
            }
        },
    )
}

private fun fileIcon(entry: RemoteFileEntry): Int =
    when {
        entry.isDirectory -> R.drawable.ic_folder

        RemoteFilePath.isImage(entry.path) -> R.drawable.ic_image

        RemoteFilePath.extension(
            entry.path,
        ) in setOf("swift", "kt", "java", "js", "ts", "tsx", "json", "rs", "py", "sh", "xml", "html", "css") -> R.drawable.ic_code

        else -> R.drawable.ic_description
    }
