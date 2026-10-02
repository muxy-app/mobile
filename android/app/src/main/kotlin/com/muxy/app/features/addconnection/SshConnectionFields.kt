package com.muxy.app.features.addconnection

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedSectionHeader
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
    sshAuthenticationFields(model, enabled)
}

private fun LazyListScope.sshAuthenticationFields(
    model: AddConnectionViewModel,
    enabled: Boolean,
) {
    item(key = "ssh-authentication-header") { ThemedSectionHeader("Authentication") }
    item(key = "ssh-authentication") {
        ThemedCell(RowPosition.FIRST) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SshAuthMethod.entries.forEachIndexed { index, method ->
                    SegmentedButton(
                        selected = model.authMethod == method,
                        onClick = { model.authMethod = method },
                        shape = SegmentedButtonDefaults.itemShape(index, SshAuthMethod.entries.size),
                        enabled = enabled,
                        icon = {},
                    ) {
                        Text(if (method == SshAuthMethod.PASSWORD) "Password" else "Private Key")
                    }
                }
            }
        }
    }
    if (model.authMethod == SshAuthMethod.PASSWORD) {
        item(key = "ssh-password") {
            ThemedCell(RowPosition.LAST) {
                ThemedTextField(
                    value = model.password,
                    onValueChange = { model.password = it },
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
                value = model.privateKey,
                onValueChange = { model.privateKey = it },
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
                value = model.passphrase,
                onValueChange = { model.passphrase = it },
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
