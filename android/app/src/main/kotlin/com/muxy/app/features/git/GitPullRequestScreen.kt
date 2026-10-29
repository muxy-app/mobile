package com.muxy.app.features.git

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.CardRow
import com.muxy.app.design.components.ListRow
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedBorderedButton
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.ThemedProminentButton
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.ThemedToggleRow
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.models.VcsPullRequest

@Composable
internal fun GitPullRequestScreen(
    pr: VcsPullRequest?,
    busy: Boolean,
    onLink: (String) -> Unit,
    onMerge: (VcsMergeMethod, Boolean) -> Unit,
) {
    var method by rememberSaveable { mutableStateOf(VcsMergeMethod.SQUASH) }
    var deleteBranch by rememberSaveable { mutableStateOf(true) }
    if (pr == null) return
    val details =
        listOfNotNull(
            "Pull Request #${pr.number}" to "${if (pr.isDraft) "Draft · " else ""}${pr.state} → ${pr.baseBranch}",
            pr.checks?.let { "Checks" to it.label },
            pr.mergeStateStatus?.let { "Merge status" to it },
        )
    ThemedList(PaddingValues(0.dp)) {
        itemsIndexed(details, key = { _, (title, _) -> "detail:$title" }) { index, (title, value) ->
            ThemedCell(RowPosition.of(index, details.size)) { CardRow(title = title, supporting = value) }
        }
        item("open") {
            ThemedBorderedButton("Open Pull Request", { onLink(pr.url) }, Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        if (!pr.state.equals("OPEN", ignoreCase = true)) return@ThemedList
        item("method-header") { ThemedSectionHeader("Merge method") }
        item("methods") {
            Column(Modifier.selectableGroup()) {
                VcsMergeMethod.entries.forEachIndexed { index, option ->
                    ThemedCell(RowPosition.of(index, VcsMergeMethod.entries.size)) {
                        MergeMethodRow(option, isSelected = method == option, enabled = !busy) { method = option }
                    }
                }
            }
        }
        item("options-header") { ThemedSectionHeader("Options") }
        item("delete") {
            ThemedCell(RowPosition.SINGLE) {
                ThemedToggleRow("Delete branch", deleteBranch, { deleteBranch = it }, enabled = !busy)
            }
        }
        item("merge") {
            ThemedProminentButton(
                "Merge Pull Request",
                { onMerge(method, deleteBranch) },
                Modifier.fillMaxWidth().padding(top = 20.dp),
                enabled = !busy && !pr.isDraft && pr.mergeable != false,
            )
        }
    }
}

@Composable
private fun MergeMethodRow(
    option: VcsMergeMethod,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    ThemedListItem(
        headline = { Text(option.title) },
        modifier = Modifier.selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        trailing = {
            if (isSelected) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(ListRow.iconSize),
                    tint = LocalAppTheme.current.foreground,
                )
            }
        },
    )
}
