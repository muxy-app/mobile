package com.muxy.app.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme
import kotlinx.coroutines.delay

private const val NOTICE_MILLIS = 4_000L

@Composable
fun TransientNotice(
    message: String?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        shown = message ?: return@LaunchedEffect
        delay(NOTICE_MILLIS)
        shown = null
    }
    Box(modifier = modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = shown != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Surface(
                shape = CircleShape,
                color = theme.secondaryGroupedBackground,
                contentColor = theme.foreground,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(bottom = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = shown.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
