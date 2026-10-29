package com.muxy.app.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val shellGlyph = Color(0xFF69DB8E)
    val waiting = Color(0xFFFF9500)
}

@Composable
fun TabKindIcon(
    kind: TabTileKind,
    activity: TabActivity,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    onTile: Boolean = false,
) {
    val theme = LocalAppTheme.current
    val provider = activity.agentProvider.takeIf { kind == TabTileKind.AGENT }
    val icon =
        when (kind) {
            TabTileKind.AGENT -> provider?.icon ?: R.drawable.ic_auto_awesome
            TabTileKind.SHELL -> R.drawable.ic_terminal
            TabTileKind.GIT -> R.drawable.ic_fork_right
            TabTileKind.OTHER -> R.drawable.ic_square
        }
    val tint =
        when {
            provider == AgentProvider.CLAUDE -> Color.Unspecified
            kind == TabTileKind.SHELL -> TabTileColors.shellGlyph
            onTile -> Color.White
            provider != null -> theme.foreground
            kind == TabTileKind.AGENT -> TabTileColors.agent
            kind == TabTileKind.GIT -> TabTileColors.git
            else -> theme.secondaryForeground
        }
    Icon(painter = painterResource(icon), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
fun TabActivityBadge(
    activity: TabActivity,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Box(modifier = modifier.size(18.dp), contentAlignment = Alignment.Center) {
        when (activity.state) {
            TabActivity.State.WAITING -> {
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .background(TabTileColors.waiting.copy(alpha = 0.28f), CircleShape)
                            .padding(3.5.dp)
                            .background(TabTileColors.waiting, CircleShape),
                )
            }

            TabActivity.State.WORKING -> {
                WorkingDots(theme.secondaryForeground)
            }

            TabActivity.State.FINISHED -> {
                Box(modifier = Modifier.size(8.dp).background(theme.green, CircleShape))
            }

            TabActivity.State.IDLE -> {}
        }
    }
}

@Composable
private fun WorkingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "working")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "phase",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val active = phase.toInt() == index
            Box(modifier = Modifier.size(4.dp).background(color.copy(alpha = if (active) 1f else 0.35f), CircleShape))
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
