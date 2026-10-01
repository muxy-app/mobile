package com.muxy.app.features.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.ThemedSectionHeader
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
    ThemedList(PaddingValues(0.dp)) {
        item("summary") {
            ThemedListItem(headline = {
                Text("Pull Request #${pr.number}")
            }, supporting = { Text("${if (pr.isDraft) "Draft · " else ""}${pr.state} → ${pr.baseBranch}") })
            pr.checks?.let { ThemedListItem(headline = { Text("Checks") }, supporting = { Text(it.label) }) }
            pr.mergeStateStatus?.let { ThemedListItem(headline = { Text("Merge status") }, supporting = { Text(it) }) }
            TextButton(onClick = { onLink(pr.url) }) { Text("Open Pull Request") }
        }
        if (!pr.state.equals("OPEN", ignoreCase = true)) return@ThemedList
        item("method") { ThemedSectionHeader("Merge method") }
        VcsMergeMethod.entries.forEach { option ->
            item(option.name) {
                ThemedListItem(
                    headline = { Text(option.title) },
                    modifier = Modifier.clickable(enabled = !busy) { method = option },
                    trailing = { RadioButton(method == option, { method = option }, enabled = !busy) },
                )
            }
        }
        item("delete") { GitToggle("Delete branch", deleteBranch, !busy) { deleteBranch = it } }
        item("merge") {
            TextButton(onClick = {
                onMerge(method, deleteBranch)
            }, enabled = !busy && !pr.isDraft && pr.mergeable != false) { Text("Merge Pull Request") }
        }
    }
}
