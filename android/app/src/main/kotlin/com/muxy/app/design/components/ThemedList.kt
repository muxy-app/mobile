package com.muxy.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme

private val sectionCornerRadius = 16.dp
private val listHorizontalInset = 16.dp
private val listVerticalInset = 8.dp

enum class RowPosition(
    val shape: Shape,
    val hasSeparator: Boolean,
) {
    SINGLE(RoundedCornerShape(sectionCornerRadius), hasSeparator = false),
    FIRST(RoundedCornerShape(topStart = sectionCornerRadius, topEnd = sectionCornerRadius), hasSeparator = true),
    MIDDLE(RoundedCornerShape(0.dp), hasSeparator = true),
    LAST(RoundedCornerShape(bottomStart = sectionCornerRadius, bottomEnd = sectionCornerRadius), hasSeparator = false),
    ;

    companion object {
        fun of(
            index: Int,
            count: Int,
        ): RowPosition =
            when {
                count == 1 -> SINGLE
                index == 0 -> FIRST
                index == count - 1 -> LAST
                else -> MIDDLE
            }
    }
}

@Composable
fun ThemedList(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = modifier.fillMaxSize().background(LocalAppTheme.current.groupedBackground),
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + listHorizontalInset,
                top = contentPadding.calculateTopPadding() + listVerticalInset,
                end = contentPadding.calculateEndPadding(layoutDirection) + listHorizontalInset,
                bottom = contentPadding.calculateBottomPadding() + listVerticalInset,
            ),
        content = content,
    )
}

fun <T> LazyListScope.themedSection(
    items: List<T>,
    key: (T) -> Any,
    header: String? = null,
    separatorInset: Dp = listHorizontalInset,
    row: @Composable (T) -> Unit,
) {
    if (header != null) {
        item(key = "header:$header") { ThemedSectionHeader(header) }
    }
    itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
        ThemedCell(RowPosition.of(index, items.size), separatorInset) { row(item) }
    }
}

@Composable
fun ThemedCell(
    position: RowPosition,
    separatorInset: Dp = listHorizontalInset,
    content: @Composable () -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(position.shape)
                .background(theme.secondaryGroupedBackground),
    ) {
        content()
        if (position.hasSeparator) {
            HorizontalDivider(
                modifier = Modifier.padding(start = separatorInset),
                thickness = Dp.Hairline,
                color = theme.separator,
            )
        }
    }
}

@Composable
fun ThemedListItem(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    ListItem(
        headlineContent = headline,
        modifier = modifier,
        supportingContent = supporting,
        leadingContent = leading,
        trailingContent = trailing,
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = theme.foreground,
                supportingColor = theme.secondaryForeground,
                leadingIconColor = theme.secondaryForeground,
                trailingIconColor = theme.secondaryForeground,
            ),
    )
}

@Composable
fun ThemedSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier =
            modifier
                .padding(start = listHorizontalInset, top = 16.dp, end = listHorizontalInset, bottom = 8.dp)
                .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = LocalAppTheme.current.secondaryForeground,
    )
}

@Composable
fun ThemedSectionFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(start = listHorizontalInset, top = 8.dp, end = listHorizontalInset),
        style = MaterialTheme.typography.bodySmall,
        color = LocalAppTheme.current.secondaryForeground,
    )
}
