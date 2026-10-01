package com.muxy.app.features.git

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxy.app.core.text.Graphemes
import com.muxy.app.models.VcsDiffRow
import kotlin.math.roundToInt

internal data class GitDiffLayout(
    val width: Dp,
    val requiresWrap: Boolean,
) {
    companion object {
        fun measure(
            longestLine: Int,
            density: Density,
            viewport: Constraints,
        ): GitDiffLayout =
            with(density) {
                val textWidth = longestLine * 13.sp.toPx() * 0.6f + 104.dp.toPx()
                val requested =
                    maxOf(viewport.maxWidth.toFloat(), 760.dp.toPx(), textWidth)
                        .roundToInt()
                        .coerceAtMost(Constraints.Infinity - 1)
                val width =
                    Constraints
                        .fitPrioritizingHeight(
                            minWidth = 0,
                            maxWidth = requested,
                            minHeight = 0,
                            maxHeight = viewport.maxHeight,
                        ).maxWidth
                GitDiffLayout(width.toDp(), requested > width)
            }
    }
}

internal fun wrappedDiffRows(rows: List<VcsDiffRow>): List<VcsDiffRow> =
    rows.flatMap { row ->
        Graphemes.chunks(row.text, 512).mapIndexed { index, text ->
            row.copy(
                text = text,
                oldLineNumber = row.oldLineNumber.takeIf { index == 0 },
                newLineNumber = row.newLineNumber.takeIf { index == 0 },
            )
        }
    }
