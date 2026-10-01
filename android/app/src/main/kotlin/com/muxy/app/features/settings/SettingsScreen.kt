package com.muxy.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.muxy.app.R
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.persistence.settings.AppSettings

@Composable
fun SettingsScreen(
    themeName: String,
    settings: AppSettings,
    onTheme: () -> Unit,
    onUseNerdFontChange: (Boolean) -> Unit,
    onAutoFocusTerminalChange: (Boolean) -> Unit,
    onDemoModeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = "Settings",
                navigationIcon = { TopBarAction(R.drawable.ic_close, "Close", onClose) },
            )
        },
    ) { padding ->
        ThemedList(contentPadding = padding) {
            item(key = "appearance") { ThemedSectionHeader("Appearance") }
            item(key = "theme") {
                ThemedCell(RowPosition.SINGLE) {
                    ThemedListItem(
                        headline = { Text("Theme") },
                        supporting = { Text(themeName) },
                        modifier = Modifier.clickable(onClick = onTheme),
                    )
                }
            }
            item(key = "terminal") { ThemedSectionHeader("Terminal") }
            item(key = "nerd-font") {
                ToggleRow(
                    position = RowPosition.FIRST,
                    title = "Use Nerd Font",
                    caption = "Caskaydia Mono with powerline and icon glyphs.",
                    checked = settings.useNerdFont,
                    onCheckedChange = onUseNerdFontChange,
                )
            }
            item(key = "auto-focus") {
                ToggleRow(
                    position = RowPosition.LAST,
                    title = "Auto-focus terminal",
                    caption = "Focus the terminal automatically when switching or creating tabs. May open the on-screen keyboard.",
                    checked = settings.autoFocusTerminal,
                    onCheckedChange = onAutoFocusTerminalChange,
                )
            }
            item(key = "demo") { ThemedSectionHeader("Demo") }
            item(key = "demo-mode") {
                ToggleRow(
                    position = RowPosition.SINGLE,
                    title = "Demo Mode",
                    caption = "Loads sample data so you can try the app without a desktop. Switching it off restores your real devices.",
                    checked = settings.demoMode,
                    onCheckedChange = onDemoModeChange,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    position: RowPosition,
    title: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ThemedCell(position) {
        ThemedListItem(
            headline = { Text(title) },
            supporting = { Text(caption) },
            trailing = { Switch(checked = checked, onCheckedChange = null) },
            modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        )
    }
}
