package com.muxy.app.features.projectdetail.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedBorderedButton
import com.muxy.app.design.components.ThemedEmptyState
import com.muxy.app.design.components.ThemedProminentButton
import com.muxy.app.design.components.blockingTouches
import com.muxy.app.features.terminal.TerminalScreen
import com.muxy.app.features.terminal.TerminalSettings

@Composable
fun TerminalTabPage(
    session: TerminalSession,
    settings: TerminalSettings,
    isActive: Boolean,
) {
    val ownership by session.ownership.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        TerminalScreen(controller = session.controller, settings = settings, isActive = isActive)
        OwnershipOverlay(ownership, session::takeControl)
    }
}

@Composable
private fun OwnershipOverlay(
    ownership: TerminalOwnership,
    onTakeControl: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val modifier = Modifier.fillMaxSize().background(theme.groupedBackground).blockingTouches()
    when (ownership) {
        is TerminalOwnership.ControlledElsewhere -> {
            ThemedEmptyState(
                title = "Controlled on ${ownership.deviceName}",
                icon = R.drawable.ic_desktop_mac,
                message = "This terminal is active on your Mac.",
                modifier = modifier,
            ) {
                ThemedProminentButton(text = "Take Control", onClick = onTakeControl)
            }
        }

        is TerminalOwnership.TakeoverFailed -> {
            ThemedEmptyState(
                title = TakeoverFailure.TITLE,
                icon = R.drawable.ic_warning,
                message = ownership.message,
                modifier = modifier,
            ) {
                ThemedBorderedButton(text = "Try Again", onClick = onTakeControl)
            }
        }

        TerminalOwnership.Disconnected -> {
            ThemedEmptyState(
                title = "Disconnected",
                icon = R.drawable.ic_wifi_off,
                message = "Reconnect to continue using this terminal.",
                modifier = modifier,
            )
        }

        TerminalOwnership.Idle, TerminalOwnership.TakingOver, TerminalOwnership.Owned -> {
            Unit
        }
    }
}
