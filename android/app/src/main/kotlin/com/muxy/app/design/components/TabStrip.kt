package com.muxy.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.models.TabActivity
import com.muxy.app.models.TabTileKind

private val touchTarget = 48.dp
private val chipHeight = 40.dp
private val chipMaxWidth = 220.dp
private val closeRippleRadius = 16.dp

data class TabStripItem(
    val id: Any,
    val title: String,
    val tile: TabTileKind = TabTileKind.SHELL,
    val activity: TabActivity = TabActivity.None,
)

@Composable
fun TabStrip(
    tabs: List<TabStripItem>,
    selectedTabId: Any?,
    onSelect: (TabStripItem) -> Unit,
    onClose: (TabStripItem) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().background(LocalAppTheme.current.groupedBackground),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs, key = { it.id }) { tab ->
            TabPill(
                tab = tab,
                isSelected = tab.id == selectedTabId,
                onSelect = { onSelect(tab) },
                onClose = { onClose(tab) },
            )
        }
        item(key = "new-tab") { NewTabButton(onCreate) }
    }
}

@Composable
fun TabPill(
    tab: TabStripItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            modifier
                .widthIn(max = chipMaxWidth)
                .height(touchTarget)
                .semantics { selected = isSelected }
                .clip(shape)
                .clickable(role = Role.Tab, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .height(chipHeight)
                    .background(if (isSelected) theme.secondaryGroupedBackground else Color.Transparent, shape)
                    .padding(start = 6.dp, end = if (isSelected) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabKindTile(kind = tab.tile, activity = tab.activity, size = 28.dp)
            Text(
                text = tab.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) theme.foreground else theme.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clickable(
                                role = Role.Button,
                                indication = ripple(bounded = false, radius = closeRippleRadius),
                                interactionSource = null,
                                onClick = onClose,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Close ${tab.title}",
                        modifier = Modifier.size(14.dp),
                        tint = theme.secondaryForeground,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewTabButton(onCreate: () -> Unit) {
    val theme = LocalAppTheme.current
    IconButton(onClick = onCreate) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = "New Tab",
            modifier = Modifier.size(22.dp),
            tint = theme.secondaryForeground,
        )
    }
}
