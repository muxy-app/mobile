package com.muxy.app.features.connections

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muxy.app.R
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.ThemedEmptyState
import com.muxy.app.design.components.ThemedProminentButton
import com.muxy.app.design.components.TopBarAction

@Composable
fun ConnectionsListScreen(
    onAddConnection: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = "Connections",
                navigationIcon = { TopBarAction(R.drawable.ic_settings, "Settings", onSettings) },
                actions = { TopBarAction(R.drawable.ic_add, "Add Connection", onAddConnection) },
            )
        },
    ) { padding ->
        ThemedEmptyState(
            title = "No Connections",
            icon = R.drawable.ic_terminal,
            message = "Add a Mac running Muxy or an SSH server to get started.",
            modifier = Modifier.padding(padding),
        ) {
            ThemedProminentButton(text = "Add Connection", onClick = onAddConnection)
        }
    }
}
