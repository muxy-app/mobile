package com.muxy.app.features.connections

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.R
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.ThemedEmptyState
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedProminentButton
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.design.components.themedSection
import com.muxy.app.models.Connection

private val connectionSeparatorInset = 16.dp + connectionIconSize + 16.dp

@Composable
fun ConnectionsListScreen(
    viewModel: ConnectionsListViewModel,
    onSelect: (Connection) -> Unit,
    onAddConnection: () -> Unit,
    onSettings: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = "Connections",
                navigationIcon = { TopBarAction(R.drawable.ic_settings, "Settings", onSettings) },
                actions = { TopBarAction(R.drawable.ic_add, "Add Connection", onAddConnection) },
            )
        },
        bottomBar = footer,
    ) { padding ->
        val loaded = connections ?: return@Scaffold
        if (loaded.isEmpty()) {
            ThemedEmptyState(
                title = "No Connections",
                icon = R.drawable.ic_terminal,
                message = "Add a Mac running Muxy or an SSH server to get started.",
                modifier = Modifier.padding(padding),
            ) {
                ThemedProminentButton(text = "Add Connection", onClick = onAddConnection)
            }
            return@Scaffold
        }
        ThemedList(contentPadding = padding) {
            themedSection(items = loaded, key = { it.id }, separatorInset = connectionSeparatorInset) { connection ->
                ConnectionRow(
                    connection = connection,
                    onSelect = { onSelect(connection) },
                    onDelete = { viewModel.delete(connection) },
                )
            }
        }
    }
}
