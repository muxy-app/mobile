package com.muxy.app.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme

@Composable
fun ThemedEmptyState(
    title: String,
    @DrawableRes icon: Int,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val theme = LocalAppTheme.current
    ScrollableCenteredColumn(spacing = 12.dp, modifier = modifier.padding(32.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = theme.secondaryForeground,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = theme.foreground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.secondaryForeground,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier.padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = actions,
        )
    }
}
