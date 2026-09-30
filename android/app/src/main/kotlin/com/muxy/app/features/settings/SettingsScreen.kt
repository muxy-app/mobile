package com.muxy.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onTheme: () -> Unit,
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
        }
    }
}
