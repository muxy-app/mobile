package com.muxy.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxy.app.data.TerminalPreferences
import com.muxy.app.data.TerminalPreferencesStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: TerminalPreferencesStore,
    appVersion: String,
    appBuild: String,
    onBack: () -> Unit,
) {
    val prefs by store.flow.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(horizontal = 4.dp),
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SectionHeader("Terminal")
            SettingsCard {
                ToggleRow(
                    label = "Use NerdFont",
                    checked = prefs.useNerdFont,
                    onCheckedChange = { store.setUseNerdFont(it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FontSizeRow(
                    size = prefs.fontSize,
                    onChange = { store.setFontSize(it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreviewRow(prefs = prefs)
            }

            Spacer(Modifier.size(24.dp))
            SectionHeader("About")
            SettingsCard {
                LabeledRow("Version", appVersion)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LabeledRow("Build", appBuild)
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column { content() }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FontSizeRow(size: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Font Size", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Text(
            size.toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = { onChange(size - 1) },
            enabled = size > TerminalPreferences.MIN_FONT_SIZE,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease font size")
        }
        Spacer(Modifier.width(4.dp))
        FilledTonalIconButton(
            onClick = { onChange(size + 1) },
            enabled = size < TerminalPreferences.MAX_FONT_SIZE,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase font size")
        }
    }
}

@Composable
private fun PreviewRow(prefs: TerminalPreferences) {
    Text(
        "The quick brown fox",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = prefs.fontSize.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
