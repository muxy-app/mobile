package com.muxy.app.features.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.design.components.TransientNotice
import com.muxy.app.features.push.PushTarget
import java.util.UUID

const val SESSION_ENDED_NOTICE = "This terminal has ended."

@Composable
fun ServerSessionScreen(
    target: PushTarget,
    server: ServerController,
    project: @Composable (projectId: String, tabId: UUID?) -> Unit,
) {
    val focus by server.sessionFocus.collectAsStateWithLifecycle()
    val opening = focus?.takeIf { it.target == target }?.opening
    val projectId = opening?.projectId ?: target.projectId
    Box(modifier = Modifier.fillMaxSize()) {
        key(projectId) { project(projectId, (opening as? SessionOpening.Opened)?.tabId) }
        TransientNotice(SESSION_ENDED_NOTICE.takeIf { opening is SessionOpening.Ended })
    }
}
