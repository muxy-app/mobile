package com.muxy.app.features.projectdetail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
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
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier.fillMaxSize().background(theme.groupedBackground).padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)).background(theme.secondaryGroupedBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_question_mark),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = theme.secondaryForeground,
            )
        }
        Text(
            text = "Unsupported tab",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = theme.foreground,
        )
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = theme.secondaryForeground)
        Text(
            text = "This tab type isn't supported in the mobile app. Update Muxy Mobile or use the desktop app.",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.secondaryForeground,
            textAlign = TextAlign.Center,
        )
    }
}
