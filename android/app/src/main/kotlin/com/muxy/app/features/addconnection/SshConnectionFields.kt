package com.muxy.app.features.addconnection

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.ThemedSegmentedPicker
import com.muxy.app.design.components.ThemedTextField
import com.muxy.app.models.SshAuthMethod

fun LazyListScope.sshSections(
    model: AddConnectionViewModel,
    enabled: Boolean,
) {
    item(key = "ssh-server-header") { ThemedSectionHeader("Server") }
    item(key = "ssh-name") {
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
    item(key = "ssh-host") {
        ThemedCell(RowPosition.MIDDLE) {
            ThemedTextField(
                value = model.host,
                onValueChange = { model.host = it },
                label = "Host",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
        }
    }
    item(key = "ssh-port") {
        ThemedCell(RowPosition.MIDDLE) {
            ThemedTextField(
                value = model.portText,
                onValueChange = { model.portText = it },
                label = "Port",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
        }
    }
    item(key = "ssh-username") {
        ThemedCell(RowPosition.LAST) {
            ThemedTextField(
                value = model.username,
                onValueChange = { model.username = it },
                label = "Username",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            )
        }
    }
    sshAuthenticationFields(
        authMethod = model.authMethod,
        onAuthMethodChange = { model.authMethod = it },
        password = model.password,
        onPasswordChange = { model.password = it },
        privateKey = model.privateKey,
        onPrivateKeyChange = { model.privateKey = it },
        passphrase = model.passphrase,
        onPassphraseChange = { model.passphrase = it },
        enabled = enabled,
    )
}

fun LazyListScope.sshAuthenticationFields(
    authMethod: SshAuthMethod,
    onAuthMethodChange: (SshAuthMethod) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    privateKey: String,
    onPrivateKeyChange: (String) -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    enabled: Boolean,
    showCredentials: Boolean = true,
) {
    item(key = "ssh-authentication-header") { ThemedSectionHeader("Authentication") }
    item(key = "ssh-authentication") {
        ThemedCell(if (showCredentials) RowPosition.FIRST else RowPosition.SINGLE) {
            ThemedSegmentedPicker(
                options = SshAuthMethod.entries,
                selected = authMethod,
                label = { if (it == SshAuthMethod.PASSWORD) "Password" else "Private Key" },
                onSelect = onAuthMethodChange,
                enabled = enabled && showCredentials,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
    if (!showCredentials) return
    if (authMethod == SshAuthMethod.PASSWORD) {
        item(key = "ssh-password") {
            ThemedCell(RowPosition.LAST) {
                ThemedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = "Password",
                    enabled = enabled,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                )
            }
        }
        return
    }
    item(key = "ssh-private-key") {
        ThemedCell(RowPosition.MIDDLE) {
            ThemedTextField(
                value = privateKey,
                onValueChange = onPrivateKeyChange,
                label = "Private Key",
                enabled = enabled,
                singleLine = false,
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            )
        }
    }
    item(key = "ssh-passphrase") {
        ThemedCell(RowPosition.LAST) {
            ThemedTextField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                label = "Passphrase (optional)",
                enabled = enabled,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
            )
        }
    }
}
