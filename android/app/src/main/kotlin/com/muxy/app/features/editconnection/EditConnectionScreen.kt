package com.muxy.app.features.editconnection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.muxy.app.R
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.ThemedSectionFooter
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.ThemedTextField
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.design.components.TopBarTextAction
import com.muxy.app.features.addconnection.sshAuthenticationFields
import com.muxy.app.models.ConnectionKind

@Composable
fun EditConnectionScreen(
    viewModel: EditConnectionViewModel,
    onClose: () -> Unit,
) {
    LaunchedEffect(viewModel.saved) { if (viewModel.saved) onClose() }
    BackHandler(enabled = viewModel.isSaving) {}
    val enabled = !viewModel.isSaving && !viewModel.saved
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = "Edit Connection",
                navigationIcon = {
                    TopBarAction(
                        R.drawable.ic_close,
                        "Cancel",
                        onClick = { if (!viewModel.isSaving && !viewModel.saved) onClose() },
                        enabled = enabled,
                    )
                },
                actions = {
                    TopBarTextAction(
                        text = if (viewModel.isSaving) "Saving…" else "Save",
                        onClick = viewModel::save,
                        enabled = viewModel.canSave,
                    )
                },
            )
        },
    ) { padding ->
        ThemedList(contentPadding = padding, modifier = Modifier.consumeWindowInsets(padding).imePadding()) {
            val connection = viewModel.connection
            if (connection != null) {
                item(key = "details-header") {
                    ThemedSectionHeader(
                        when (connection.kind) {
                            ConnectionKind.DEVICE -> "Muxy 1"
                            ConnectionKind.SERVER -> "Muxy 2"
                            ConnectionKind.SSH -> "SSH"
                        },
                    )
                }
                detailFields(viewModel, enabled)
                if (connection.usesSsh) sshFields(viewModel, enabled)
                item(key = "save-footer") {
                    ThemedSectionFooter(
                        "Changes are saved on this phone without connecting. Existing pairing and host verification are kept.",
                    )
                }
            }
            viewModel.failure?.let { message ->
                item(key = "failure") {
                    ThemedSectionFooter(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
    }
}

private fun LazyListScope.detailFields(
    model: EditConnectionViewModel,
    enabled: Boolean,
) {
    item(key = "name") {
        ThemedCell(RowPosition.FIRST) {
            ThemedTextField(
                value = model.name,
                onValueChange = { model.name = it },
                label = "Name",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
        }
    }
    item(key = "host") {
        ThemedCell(RowPosition.MIDDLE) {
            ThemedTextField(
                value = model.host,
                onValueChange = { model.host = it },
                label = "IP / Hostname",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
        }
    }
    item(key = "port") {
        ThemedCell(RowPosition.LAST) {
            ThemedTextField(
                value = model.portText,
                onValueChange = { model.portText = it },
                label = "Port (1–65535)",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
        }
    }
}

private fun LazyListScope.sshFields(
    model: EditConnectionViewModel,
    enabled: Boolean,
) {
    item(key = "ssh-header") { ThemedSectionHeader("SSH Account") }
    item(key = "username") {
        ThemedCell(RowPosition.FIRST) {
            ThemedTextField(
                value = model.username,
                onValueChange = { model.username = it },
                label = "Username",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            )
        }
    }
    item(key = "replace-credentials") {
        ThemedCell(RowPosition.LAST) {
            ThemedListItem(
                headline = { Text("Replace saved credentials") },
                supporting = { Text("Off keeps saved secrets. Turn on to replace them or change authentication method.") },
                trailing = { Switch(checked = model.replaceCredentials, onCheckedChange = null, enabled = enabled) },
                modifier =
                    Modifier.toggleable(
                        value = model.replaceCredentials,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = model::changeCredentialReplacement,
                    ),
            )
        }
    }
    sshAuthenticationFields(
        authMethod = model.authMethod,
        onAuthMethodChange = model::selectAuthMethod,
        password = model.password,
        onPasswordChange = { model.password = it },
        privateKey = model.privateKey,
        onPrivateKeyChange = { model.privateKey = it },
        passphrase = model.passphrase,
        onPassphraseChange = { model.passphrase = it },
        enabled = enabled,
        showCredentials = model.replaceCredentials,
    )
    if (!model.replaceCredentials) return
    item(key = "credentials-footer") {
        ThemedSectionFooter("Enter a new password or private key. An empty passphrase means no passphrase for the replacement key.")
    }
}
