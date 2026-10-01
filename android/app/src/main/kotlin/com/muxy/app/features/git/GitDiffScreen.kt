package com.muxy.app.features.git

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.models.VcsDiff
import com.muxy.app.models.VcsDiffRow
import com.muxy.app.models.VcsDiffRowKind

@Composable
internal fun GitDiffScreen(
    diff: VcsDiff?,
    staged: Boolean,
    loading: Boolean,
    onFull: () -> Unit,
) {
    var wraps by rememberSaveable { mutableStateOf(false) }
    val longest = remember(diff) { diff?.rows?.maxOfOrNull { it.text.length } ?: 0 }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = GitDiffLayout.measure(longest, density, constraints)
        val wrapsLines = wraps || layout.requiresWrap
        val rows =
            remember(diff, wrapsLines) {
                val rows = diff?.rows.orEmpty()
                if (wrapsLines) wrappedDiffRows(rows) else rows
            }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(if (staged) "Staged" else "Unstaged", Modifier.weight(1f).padding(vertical = 12.dp))
                TextButton(onClick = { wraps = !wraps }, enabled = !layout.requiresWrap) {
                    Text(if (wrapsLines) "Unwrap" else "Wrap")
                }
                if (diff?.truncated == true) TextButton(onClick = onFull, enabled = !loading) { Text("Full") }
            }
            if (diff == null) return@Column
            Row(Modifier.padding(horizontal = 16.dp)) {
                Text("+${diff.additions}", color = LocalAppTheme.current.green)
                Text("  −${diff.deletions}", color = LocalAppTheme.current.red)
            }
            if (diff.isBinary) {
                Text("Binary file", Modifier.padding(16.dp))
                return@Column
            }
            if (layout.requiresWrap) Text("Long lines are wrapped to fit this display.", Modifier.padding(horizontal = 16.dp))
            val scrolling = if (wrapsLines) Modifier else Modifier.horizontalScroll(rememberScrollState())
            Box(Modifier.fillMaxSize().then(scrolling)) {
                LazyColumn(Modifier.fillMaxHeight().then(if (wrapsLines) Modifier.fillMaxWidth() else Modifier.width(layout.width))) {
                    itemsIndexed(rows, key = { index, _ -> index }) { _, row -> DiffRow(row, wrapsLines) }
                }
            }
        }
    }
}

@Composable
private fun DiffRow(
    row: VcsDiffRow,
    wraps: Boolean,
) {
    val theme = LocalAppTheme.current
    val color =
        when (row.kind) {
            VcsDiffRowKind.ADDITION -> theme.green
            VcsDiffRowKind.DELETION -> theme.red
            VcsDiffRowKind.HUNK, VcsDiffRowKind.COLLAPSED -> theme.cyan
            VcsDiffRowKind.CONTEXT -> theme.foreground
        }
    val background = if (row.kind == VcsDiffRowKind.CONTEXT) Color.Transparent else color.copy(alpha = 0.12f)
    Row(Modifier.fillMaxWidth().background(background).padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            row.oldLineNumber?.toString().orEmpty(),
            Modifier.width(44.dp),
            color = theme.secondaryForeground,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Text(
            row.newLineNumber?.toString().orEmpty(),
            Modifier.width(44.dp),
            color = theme.secondaryForeground,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Text(row.text, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp, softWrap = wraps)
    }
}
