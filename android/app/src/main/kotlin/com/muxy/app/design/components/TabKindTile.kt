package com.muxy.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.models.AgentProvider
import com.muxy.app.models.TabActivity
import com.muxy.app.models.TabTileKind

object TabTileColors {
    val agent = Color(0xFFE2683C)
    val git = Color(0xFF5856D6)
    val other = Color(0xFF8E8E93)
    val shellGlyph = Color(0xFF69DB8E)
    val waiting = Color(0xFFFF9500)
    val finished = Color(0xFF0A84FF)
}

@Composable
fun TabKindTile(
    kind: TabTileKind,
    activity: TabActivity,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val theme = LocalAppTheme.current
    val provider = activity.agentProvider.takeIf { kind == TabTileKind.AGENT }
    val neutralFill = if (theme.isDark) Color(0xFF2B2A2F) else Color(0xFF1C1B1F)
    val fill =
        when (kind) {
            TabTileKind.AGENT -> if (provider == null) TabTileColors.agent else neutralFill
            TabTileKind.SHELL -> neutralFill
            TabTileKind.GIT -> TabTileColors.git
            TabTileKind.OTHER -> TabTileColors.other
        }
    val icon =
        when (kind) {
            TabTileKind.AGENT -> provider?.icon ?: R.drawable.ic_auto_awesome
            TabTileKind.SHELL -> R.drawable.ic_terminal
            TabTileKind.GIT -> R.drawable.ic_fork_right
            TabTileKind.OTHER -> R.drawable.ic_square
        }
    val dot =
        when (activity.state) {
            TabActivity.State.WAITING -> TabTileColors.waiting
            TabActivity.State.FINISHED -> TabTileColors.finished
            TabActivity.State.IDLE, TabActivity.State.WORKING -> null
        }
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier.size(size).background(fill, RoundedCornerShape(size * 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(if (provider == null) size * 0.5f else size * 0.54f),
                tint =
                    when {
                        provider == AgentProvider.CLAUDE -> Color.Unspecified
                        kind == TabTileKind.SHELL -> TabTileColors.shellGlyph
                        else -> Color.White
                    },
            )
        }
        if (dot != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = size * 0.1f, y = -size * 0.1f)
                        .size(size * 0.32f)
                        .border(2.dp, theme.secondaryGroupedBackground, CircleShape)
                        .background(dot, CircleShape),
            )
        }
    }
}

private val AgentProvider.icon: Int
    get() =
        when (this) {
            AgentProvider.CLAUDE -> R.drawable.ic_provider_claude
            AgentProvider.CODEX -> R.drawable.ic_provider_codex
            AgentProvider.OPEN_CODE -> R.drawable.ic_provider_opencode
            AgentProvider.CURSOR -> R.drawable.ic_provider_cursor
            AgentProvider.COPILOT -> R.drawable.ic_provider_copilot
            AgentProvider.DROID -> R.drawable.ic_provider_factory
            AgentProvider.PI -> R.drawable.ic_provider_pi
            AgentProvider.GROK -> R.drawable.ic_provider_grok
            AgentProvider.KIRO -> R.drawable.ic_provider_kiro
            AgentProvider.XAL -> R.drawable.ic_provider_xal
            AgentProvider.ANTIGRAVITY -> R.drawable.ic_provider_antigravity
            AgentProvider.OH_MY_PI -> R.drawable.ic_provider_omp
        }
