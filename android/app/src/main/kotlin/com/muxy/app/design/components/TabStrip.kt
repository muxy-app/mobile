package com.muxy.app.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme

private const val SELECTED_SECONDARY_ALPHA = 0.7f
private val touchTarget = 48.dp
private val pillHeight = 36.dp
private val pillMaxWidth = 200.dp
private val closeClearance = 36.dp
private val closeRippleRadius = 16.dp

data class TabStripItem(
    val id: Any,
    val title: String,
    @param:DrawableRes val icon: Int,
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    val foreground = if (isSelected) theme.onAccent else theme.foreground
    val secondary = if (isSelected) theme.onAccent.copy(alpha = SELECTED_SECONDARY_ALPHA) else theme.secondaryForeground
    val selection = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .widthIn(max = pillMaxWidth)
                .height(touchTarget)
                .semantics { selected = isSelected }
                .clickable(interactionSource = selection, indication = null, role = Role.Tab, onClick = onSelect),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier =
                Modifier
                    .height(pillHeight)
                    .clip(CircleShape)
                    .background(if (isSelected) theme.accent else theme.secondaryGroupedBackground)
                    .indication(selection, ripple())
                    .padding(start = 12.dp, end = closeClearance),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painter = painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(14.dp), tint = secondary)
            Text(
                text = tab.title,
                style = MaterialTheme.typography.bodyMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(touchTarget)
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
                tint = secondary,
            )
        }
    }
}

@Composable
private fun NewTabButton(onCreate: () -> Unit) {
    val theme = LocalAppTheme.current
    IconButton(onClick = onCreate) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(theme.secondaryGroupedBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "New Tab",
                modifier = Modifier.size(18.dp),
                tint = theme.foreground,
            )
        }
    }
}
