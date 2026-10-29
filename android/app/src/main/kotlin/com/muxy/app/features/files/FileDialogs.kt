package com.muxy.app.features.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.SurfaceCorners
import com.muxy.app.design.components.ThemedAlertDialog
import com.muxy.app.design.components.ThemedDialogAction
import com.muxy.app.design.components.ThemedTextField
import com.muxy.app.models.RemoteFileEntry

internal data class FileNamePrompt(
    val isDirectory: Boolean,
    val entry: RemoteFileEntry? = null,
)

@Composable
internal fun FileNameDialog(
    prompt: FileNamePrompt,
    state: FileManagerState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(prompt.entry?.path, prompt.isDirectory) { mutableStateOf(prompt.entry?.name.orEmpty()) }
    val validation =
        try {
            val valid = RemoteFilePath.validatedName(name)
            if (state.entries.any {
                    it.path != prompt.entry?.path && it.name.equals(valid, ignoreCase = true)
                }
            ) {
                "An item with this name already exists."
            } else {
                null
            }
        } catch (error: FileException) {
            error.message
        }
    ThemedAlertDialog(
        title =
            when {
                prompt.entry != null -> "Rename"
                prompt.isDirectory -> "New folder"
                else -> "New file"
            },
        onDismissRequest = { if (!state.isBusy) onDismiss() },
        confirm =
            ThemedDialogAction(
                label = if (prompt.entry != null) "Rename" else "Create",
                onClick = { onConfirm(name) },
                enabled = state.canMutate && validation == null,
            ),
        dismiss = listOf(ThemedDialogAction("Cancel", onDismiss, enabled = !state.isBusy)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemedTextField(
                name,
                { name = it },
                "Name",
                modifier =
                    Modifier
                        .clip(
                            RoundedCornerShape(SurfaceCorners.row),
                        ).background(LocalAppTheme.current.tertiaryGroupedBackground),
                enabled = !state.isBusy,
            )
            if (name.isNotEmpty() && validation != null) Text(validation, color = LocalAppTheme.current.red)
            state.errorMessage?.let { Text(it, color = LocalAppTheme.current.red) }
        }
    }
}

@Composable
internal fun FileUnsavedDialog(
    canSave: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
) {
    ThemedAlertDialog(
        title = "Unsaved changes",
        message = "Save your changes before leaving this file?",
        onDismissRequest = onKeep,
        confirm = ThemedDialogAction("Save", onSave, enabled = canSave),
        dismiss =
            listOf(
                ThemedDialogAction("Discard", onDiscard, isDestructive = true),
                ThemedDialogAction("Keep editing", onKeep),
            ),
    )
}
