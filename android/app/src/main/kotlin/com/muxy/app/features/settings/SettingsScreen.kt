package com.muxy.app.features.settings

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.muxy.app.R
import com.muxy.app.design.components.CardRow
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedSectionFooter
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.ThemedToggleRow
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
                    CardRow(title = "Terminal theme", value = themeName, navigates = true, onClick = onTheme)
                }
            }
            item(key = "appearance-footer") { ThemedSectionFooter("App appearance follows your system.") }
            item(key = "terminal") { ThemedSectionHeader("Terminal") }
            item(key = "nerd-font") {
                ThemedCell(RowPosition.FIRST) {
                    ThemedToggleRow(
                        title = "Use Nerd Font",
                        supporting = "Caskaydia Mono with powerline and icon glyphs.",
                        checked = settings.useNerdFont,
                        onCheckedChange = onUseNerdFontChange,
                    )
                }
            }
            item(key = "auto-focus") {
                ThemedCell(RowPosition.LAST) {
                    ThemedToggleRow(
                        title = "Auto-focus terminal",
                        supporting = "Focus the terminal automatically when switching or creating tabs. May open the on-screen keyboard.",
                        checked = settings.autoFocusTerminal,
                        onCheckedChange = onAutoFocusTerminalChange,
                    )
                }
            }
            item(key = "demo") { ThemedSectionHeader("Demo") }
            item(key = "demo-mode") {
                ThemedCell(RowPosition.SINGLE) {
                    ThemedToggleRow(
                        title = "Demo Mode",
                        supporting =
                            "Loads sample data so you can try the app without a desktop. Switching it off restores your real devices.",
                        checked = settings.demoMode,
                        onCheckedChange = onDemoModeChange,
                    )
                }
            }
        }
    }
}
