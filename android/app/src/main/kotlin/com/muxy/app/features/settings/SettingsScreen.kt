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

@Composable
fun SettingsScreen(
    themeName: String,
    demoMode: Boolean,
    onTheme: () -> Unit,
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
            item(key = "demo") { ThemedSectionHeader("Demo") }
            item(key = "demo-mode") {
                ThemedCell(RowPosition.SINGLE) {
                    ThemedListItem(
                        headline = { Text("Demo Mode") },
                        supporting = {
                            Text(
                                "Loads sample data so you can try the app without a desktop. Switching it off restores your real devices.",
                            )
                        },
                        trailing = { Switch(checked = demoMode, onCheckedChange = null) },
                        modifier = Modifier.toggleable(value = demoMode, role = Role.Switch, onValueChange = onDemoModeChange),
                    )
                }
            }
        }
    }
}
