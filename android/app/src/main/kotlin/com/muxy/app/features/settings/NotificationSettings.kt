package com.muxy.app.features.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalContext
import com.muxy.app.design.components.CardRow
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedSectionFooter
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.ThemedToggleRow
import com.muxy.app.features.push.PushAuthorization
import com.muxy.app.features.push.PushServerRow

data class NotificationSettings(
    val authorization: PushAuthorization,
    val servers: List<PushServerRow>,
    val onAllow: () -> Unit,
    val onToggle: (String, Boolean) -> Unit,
)

fun LazyListScope.notificationSettings(settings: NotificationSettings) {
    item(key = "notifications") { ThemedSectionHeader("Notifications") }
    val permissionRow = settings.authorization != PushAuthorization.ALLOWED
    val rows = settings.servers.size + if (permissionRow) 1 else 0
    if (permissionRow) {
        item(key = "notifications-permission") {
            val context = LocalContext.current
            ThemedCell(RowPosition.of(0, rows)) {
                if (settings.authorization == PushAuthorization.NOT_DETERMINED) {
                    CardRow(title = "Allow notifications", onClick = settings.onAllow)
                } else {
                    CardRow(title = "Turn on in Android settings", navigates = true, onClick = { context.openNotificationSettings() })
                }
            }
        }
    }
    if (settings.servers.isEmpty() && !permissionRow) {
        item(key = "notifications-empty") {
            ThemedCell(RowPosition.SINGLE) { CardRow(title = "Pair a computer running Muxy 2 to get notifications.") }
        }
    }
    itemsIndexed(settings.servers, key = { _, server -> "notifications-server:${server.serverId}" }) { index, server ->
        val position = index + if (permissionRow) 1 else 0
        ThemedCell(RowPosition.of(position, rows)) {
            ThemedToggleRow(
                title = server.name,
                supporting = server.summary,
                checked = server.isEnabled,
                onCheckedChange = { settings.onToggle(server.serverId, it) },
            )
        }
    }
    item(key = "notifications-footer") {
        ThemedSectionFooter(
            "Muxy tells you when an agent needs you or finishes while the app is closed. Notifications are end-to-end " +
                "encrypted, so muxy.app relays them without seeing what they say.",
        )
    }
}

private fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
