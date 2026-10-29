package com.muxy.app.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme

private const val DISABLED_ALPHA = 0.38f

@Composable
fun ThemedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SurfaceCorners.card))
                .background(LocalAppTheme.current.secondaryGroupedBackground),
        content = content,
    )
}

@Composable
fun ThemedNotice(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tint: Color = LocalAppTheme.current.secondaryForeground,
    action: (@Composable () -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    ThemedCard(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Column(
            modifier = Modifier.padding(horizontal = ListRow.horizontalPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (title != null) {
                Text(title, color = tint, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                message,
                color = if (title == null) tint else theme.secondaryForeground,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (action != null) {
                Row(modifier = Modifier.padding(top = 6.dp)) { action() }
            }
        }
    }
}

@Composable
fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    iconTint: Color? = null,
    supporting: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    navigates: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    val clickModifier =
        if (onClick == null) Modifier else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    val hasTrailing = value != null || trailing != null || navigates
    ThemedListItem(
        headline = {
            Text(
                title,
                color = if (enabled) theme.foreground else theme.secondaryForeground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier.then(clickModifier),
        supporting = supporting?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        leading =
            icon?.let {
                {
                    val tint = iconTint ?: theme.secondaryForeground
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        modifier = Modifier.size(ListRow.iconSize),
                        tint = if (enabled) tint else tint.copy(alpha = DISABLED_ALPHA),
                    )
                }
            },
        trailing =
            if (hasTrailing) {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        value?.let { Text(it, modifier = Modifier.widthIn(max = 180.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        trailing?.invoke()
                        if (navigates) ThemedChevron()
                    }
                }
            } else {
                null
            },
    )
}

@Composable
fun ThemedChevron(modifier: Modifier = Modifier) {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        modifier = modifier.size(20.dp),
        tint = LocalAppTheme.current.secondaryForeground,
    )
}
