package com.muxy.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme

private val listVerticalInset = 8.dp
private val headerInset = 16.dp

enum class RowPosition(
    val shape: Shape,
    val hasGapBelow: Boolean,
) {
    SINGLE(segment(isFirst = true, isLast = true), hasGapBelow = false),
    FIRST(segment(isFirst = true, isLast = false), hasGapBelow = true),
    MIDDLE(segment(isFirst = false, isLast = false), hasGapBelow = true),
    LAST(segment(isFirst = false, isLast = true), hasGapBelow = false),
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

fun segmentShape(
    index: Int,
    count: Int,
): Shape = RowPosition.of(index, count).shape

private fun segment(
    isFirst: Boolean,
    isLast: Boolean,
): Shape {
    val top = if (isFirst) SurfaceCorners.card else SurfaceCorners.segment
    val bottom = if (isLast) SurfaceCorners.card else SurfaceCorners.segment
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
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
                start = contentPadding.calculateStartPadding(layoutDirection) + PageMargin.horizontal,
                top = contentPadding.calculateTopPadding() + listVerticalInset,
                end = contentPadding.calculateEndPadding(layoutDirection) + PageMargin.horizontal,
                bottom = contentPadding.calculateBottomPadding() + listVerticalInset,
            ),
        content = content,
    )
}

fun <T> LazyListScope.themedSection(
    items: List<T>,
    key: (T) -> Any,
    header: String? = null,
    row: @Composable (T) -> Unit,
) {
    if (header != null) {
        item(key = "header:$header") { ThemedSectionHeader(header) }
    }
    itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
        ThemedCell(RowPosition.of(index, items.size)) { row(item) }
    }
}

@Composable
fun ThemedCell(
    position: RowPosition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = if (position.hasGapBelow) SurfaceCorners.segmentGap else 0.dp)
                .clip(position.shape)
                .background(LocalAppTheme.current.secondaryGroupedBackground),
    ) {
        content()
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
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ListRow.minHeight)
                .padding(horizontal = ListRow.horizontalPadding, vertical = ListRow.verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(ListRow.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides theme.secondaryForeground, content = leading)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompositionLocalProvider(LocalContentColor provides theme.foreground) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge, headline)
            }
            if (supporting != null) {
                CompositionLocalProvider(LocalContentColor provides theme.secondaryForeground) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium, supporting)
                }
            }
        }
        if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides theme.secondaryForeground) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium, trailing)
            }
        }
    }
}

object ListRow {
    val minHeight = 48.dp
    val horizontalPadding = 16.dp
    val verticalPadding = 10.dp
    val spacing = 14.dp
    val iconSize = 22.dp
    val nestedCorner = SurfaceCorners.card - horizontalPadding
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
                .padding(start = headerInset, top = 16.dp, end = headerInset, bottom = 8.dp)
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
        modifier = modifier.padding(start = headerInset, top = 8.dp, end = headerInset),
        style = MaterialTheme.typography.bodySmall,
        color = LocalAppTheme.current.secondaryForeground,
    )
}
