package com.muxy.app.features.projectdetail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedEmptyState
import com.muxy.app.models.TabKind

@DrawableRes
fun TabKind.icon(): Int =
    when (this) {
        TabKind.Terminal -> R.drawable.ic_terminal
        TabKind.Vcs -> R.drawable.ic_fork_right
        is TabKind.Unsupported -> R.drawable.ic_help
    }

@Composable
fun UnsupportedTabPage(title: String) {
    ThemedEmptyState(
        title = "Unsupported tab",
        icon = R.drawable.ic_question_mark,
        message = "$title isn't supported in the mobile app. Update Muxy Mobile or use the desktop app.",
        modifier = Modifier.fillMaxSize().background(LocalAppTheme.current.groupedBackground),
    )
}
