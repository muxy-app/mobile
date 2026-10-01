package com.muxy.app.features.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.rgbColor
import com.muxy.app.features.terminalkit.TerminalAccessoryBar
import com.muxy.app.features.terminalkit.TerminalAccessoryBarDefaults

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    controller: TerminalController,
    settings: TerminalSettings,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAppTheme.current.terminalPalette
    val background = rgbColor(palette.background)
    val foreground = rgbColor(palette.foreground)
    val handle = remember(controller) { TerminalSurfaceHandle(controller) }
    val imeVisible = WindowInsets.isImeVisible
    SideEffect { handle.keyboardVisible = imeVisible }
    val mode by controller.mode.collectAsStateWithLifecycle()
    val following by controller.isFollowing.collectAsStateWithLifecycle()
    val sticky by controller.sticky.collectAsStateWithLifecycle()
    val notice by controller.notice.collectAsStateWithLifecycle()
    val historyStart by controller.showsHistoryStart.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context -> TerminalSurfaceView(context).also(handle::attach) },
                modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { handle.surfacePlaced(it.boundsInWindow().bottom) },
                onRelease = handle::release,
                update = { view -> view.bind(controller, palette, settings.useNerdFont) },
            )
            Spacer(modifier = Modifier.height(TerminalAccessoryBarDefaults.height))
        }
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            if (mode == TerminalMode.HISTORY || !following) {
                BackToLiveButton(foreground, Modifier.align(Alignment.End), controller::returnToLive)
            }
            TerminalAccessoryBar(
                sticky = sticky,
                canCopy = handle.canCopy,
                keyboardVisible = imeVisible,
                background = background,
                foreground = foreground,
                actions = handle,
                modifier = Modifier.onGloballyPositioned { handle.barPlaced(it.boundsInWindow().top) },
            )
        }
        TerminalNotices(historyStart, notice, Modifier.align(Alignment.TopCenter))
    }

    LaunchedEffect(isActive, handle) {
        if (isActive) handle.view?.activate(settings.autoFocus)
    }
}

@Composable
private fun BackToLiveButton(
    foreground: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Surface(
        onClick = onClick,
        modifier = modifier.padding(16.dp).size(BUTTON_SIZE),
        shape = CircleShape,
        color = theme.secondaryGroupedBackground,
        border = BorderStroke(1.dp, foreground.copy(alpha = BORDER_ALPHA)),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = BACK_TO_LIVE,
                tint = foreground,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TerminalNotices(
    historyStart: Boolean,
    notice: String?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (historyStart) NoticeCapsule(START_OF_HISTORY)
        notice?.let { NoticeCapsule(it) }
    }
}

@Composable
private fun NoticeCapsule(text: String) {
    val theme = LocalAppTheme.current
    Surface(shape = CircleShape, color = theme.secondaryGroupedBackground, shadowElevation = 2.dp) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = theme.foreground,
        )
    }
}

private const val BACK_TO_LIVE = "Back to Live Output"
private const val START_OF_HISTORY = "Start of history"
private const val BORDER_ALPHA = 0.18f
private val BUTTON_SIZE = 48.dp
