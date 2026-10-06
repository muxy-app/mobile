package com.muxy.app.features.editconnection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.core.validation.ConnectionInputValidator
import com.muxy.app.core.validation.InputValidation
import com.muxy.app.core.validation.ValidatedConnectionInput
import com.muxy.app.core.validation.ValidatedSshInput
import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.models.Connection
import com.muxy.app.models.SshAuthMethod
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.secrets.SecretUpdateRecoveryException
import kotlinx.coroutines.launch
import java.util.UUID

class EditConnectionViewModel(
    connectionId: UUID,
    store: ConnectionStore,
    private val editor: ConnectionEditor,
    private val validator: ConnectionInputValidator,
) : ViewModel() {
    var connection by mutableStateOf<Connection?>(null)
        private set
    var name by mutableStateOf("")
    var host by mutableStateOf("")
    var portText by mutableStateOf("")
    var username by mutableStateOf("")
    var authMethod by mutableStateOf(SshAuthMethod.PASSWORD)
        private set
    var replaceCredentials by mutableStateOf(false)
        private set
    var password by mutableStateOf("")
    var privateKey by mutableStateOf("")
    var passphrase by mutableStateOf("")
    var isSaving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    private var recoveryFailed = false

    val canSave: Boolean
        get() {
            val current = connection ?: return false
            if (isSaving || saved || recoveryFailed || validatedInput() == null) return false
            if (!current.usesSsh) return true
            if (current.sshConfig == null || username.isBlank()) return false
            if (!replaceCredentials) return authMethod == current.sshConfig.authMethod
            return validatedReplacement() != null
        }

    init {
        viewModelScope.launch {
            attempt {
                val current = checkNotNull(store.load().firstOrNull { it.id == connectionId && it.id != DemoConnection.id })
                name = current.name
                host = current.host
                portText = current.port.toString()
                username = current.sshConfig?.username.orEmpty()
                authMethod = current.sshConfig?.authMethod ?: SshAuthMethod.PASSWORD
                connection = current
            }.onFailure {
                Log.persistence.error("Loading a connection for editing failed: ${it.javaClass.simpleName}")
                failure = "Couldn't load this saved connection. Close and try again."
            }
        }
    }

    fun changeCredentialReplacement(replace: Boolean) {
        if (isSaving || saved) return
        replaceCredentials = replace
        if (replace) return
        authMethod = connection?.sshConfig?.authMethod ?: SshAuthMethod.PASSWORD
        clearSecrets()
    }

    fun selectAuthMethod(method: SshAuthMethod) {
        if (isSaving || saved || !replaceCredentials) return
        authMethod = method
        clearSecrets()
    }

    fun save() {
        if (!canSave) return
        val previous = connection ?: return
        val input = validatedInput() ?: return
        val replacement = validatedReplacement().takeIf { replaceCredentials }
        val editedUsername = username
        isSaving = true
        failure = null
        viewModelScope.launch {
            try {
                attempt { editor.save(previous, input, editedUsername, replacement) }
                    .onSuccess {
                        clearSecrets()
                        saved = true
                    }.onFailure {
                        Log.persistence.error("Editing a connection failed: ${it.javaClass.simpleName}")
                        recoveryFailed = it is SecretUpdateRecoveryException
                        failure =
                            if (recoveryFailed) {
                                "Couldn't restore the previous credentials. Close and check the connection settings before connecting."
                            } else {
                                "Couldn't save the connection. Your previous settings were kept. Close and try again."
                            }
                    }
            } finally {
                isSaving = false
            }
        }
    }

    private fun clearSecrets() {
        password = ""
        privateKey = ""
        passphrase = ""
    }

    private fun validatedInput(): ValidatedConnectionInput? = (validator.validate(name, host, portText) as? InputValidation.Valid)?.value

    private fun validatedReplacement(): ValidatedSshInput? =
        (
            validator.validateSsh(
                name,
                host,
                portText,
                username,
                authMethod,
                if (authMethod == SshAuthMethod.PASSWORD) password else privateKey,
                passphrase,
            ) as? InputValidation.Valid
        )?.value
}
