package com.muxy.app.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import com.muxy.app.model.ProjectDTO
import com.muxy.app.model.ProjectIconColor
import com.muxy.app.ui.connect.ConnectionViewModel
import com.muxy.app.ui.theme.MuxyTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(viewModel: ConnectionViewModel) {
    val projects by viewModel.session.projects.collectAsState()
    val logos by viewModel.session.projectLogos.collectAsState()
    val worktrees by viewModel.session.projectWorktrees.collectAsState()
    val theme by viewModel.session.deviceTheme.collectAsState()
    val palette = MuxyTheme.from(theme)

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                title = { Text("Projects", color = palette.foreground) },
                actions = {
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Disconnect",
                            tint = palette.foreground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.foreground,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(padding),
        ) {
            if (projects.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No projects yet", color = palette.foreground.copy(alpha = 0.7f))
                    Text(
                        "Add a project on your Mac to see it here.",
                        color = palette.foreground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                val scope = rememberScopeForViewModel()
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(projects, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            logoBytes = logos[project.id],
                            subtitle = subtitleFor(project.id, worktrees),
                            foreground = palette.foreground,
                            onClick = {
                                scope.launch { viewModel.session.selectProject(project.id) }
                            },
                        )
                        HorizontalDivider(color = palette.foreground.copy(alpha = 0.12f))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberScopeForViewModel() = remember {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
}

private fun subtitleFor(projectID: String, worktrees: Map<String, List<com.muxy.app.model.WorktreeDTO>>): String {
    val list = worktrees[projectID] ?: return "default"
    val primary = list.firstOrNull { it.isPrimary } ?: list.firstOrNull() ?: return "default"
    return primary.branch ?: primary.name
}

@Composable
private fun ProjectRow(
    project: ProjectDTO,
    logoBytes: ByteArray?,
    subtitle: String,
    foreground: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectIcon(project, logoBytes)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                project.name,
                color = foreground,
                fontWeight = FontWeight.Medium,
            )
            Text(
                subtitle,
                color = foreground.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProjectIcon(project: ProjectDTO, logoBytes: ByteArray?) {
    val size = 40.dp
    val corner = RoundedCornerShape(9.dp)
    if (logoBytes != null) {
        val bitmap = remember(logoBytes) {
            BitmapFactory.decodeByteArray(logoBytes, 0, logoBytes.size)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(corner),
            )
            return
        }
    }
    val swatch = ProjectIconColor.swatch(project.iconColor)
    val (bgColor, fgColor) = if (swatch != null) {
        val (r, g, b) = ProjectIconColor.rgbFromHex(swatch.hex) ?: Triple(0.5, 0.5, 0.5)
        Color(r.toFloat(), g.toFloat(), b.toFloat()) to
                if (swatch.prefersDarkForeground) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier.size(size).clip(corner).background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            project.name.take(1).uppercase(),
            color = fgColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
