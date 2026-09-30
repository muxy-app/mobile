package com.muxy.app.features.connections

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind

val connectionIconSize = 32.dp

@Composable
fun ConnectionRow(
    connection: Connection,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = LocalAppTheme.current
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(theme.red).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null, tint = Color.White)
            }
        },
    ) {
        ThemedListItem(
            headline = { Text(connection.name, fontWeight = FontWeight.SemiBold) },
            supporting = { Text(connection.subtitle) },
            leading = {
                Box(modifier = Modifier.size(connectionIconSize), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(connection.kind.icon),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = theme.foreground,
                    )
                }
            },
            modifier =
                Modifier
                    .background(theme.secondaryGroupedBackground)
                    .clickable(onClick = onSelect)
                    .semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Delete") {
                                    onDelete()
                                    true
                                },
                            )
                    },
        )
    }
}

val Connection.subtitle: String
    get() {
        val username = sshConfig?.username
        if (kind != ConnectionKind.SSH || username == null) return "$host:$port"
        return "$username@$host:$port"
    }

@get:DrawableRes
private val ConnectionKind.icon: Int
    get() =
        when (this) {
            ConnectionKind.DEVICE -> R.drawable.ic_desktop_mac
            ConnectionKind.SERVER -> R.drawable.ic_dns
            ConnectionKind.SSH -> R.drawable.ic_terminal
        }
