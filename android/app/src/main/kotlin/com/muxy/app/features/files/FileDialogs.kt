package com.muxy.app.features.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.muxy.app.design.LocalAppTheme
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
    AlertDialog(
        onDismissRequest = { if (!state.isBusy) onDismiss() },
        title = {
            Text(
                if (prompt.entry != null) {
                    "Rename"
                } else if (prompt.isDirectory) {
                    "New folder"
                } else {
                    "New file"
                },
            )
        },
        text = {
            Column {
                ThemedTextField(name, { name = it }, "Name", enabled = !state.isBusy)
                if (name.isNotEmpty() && validation != null) Text(validation, color = LocalAppTheme.current.red)
                state.errorMessage?.let { Text(it, color = LocalAppTheme.current.red) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = state.canMutate && validation == null) {
                Text(
                    if (prompt.entry !=
                        null
                    ) {
                        "Rename"
                    } else {
                        "Create"
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isBusy) { Text("Cancel") } },
    )
}

@Composable
internal fun FileUnsavedDialog(
    canSave: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Unsaved changes") },
        text = { Text("Save your changes before leaving this file?") },
        confirmButton = { TextButton(onClick = onSave, enabled = canSave) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("Discard") }
                TextButton(onClick = onKeep) { Text("Keep editing") }
            }
        },
    )
}
