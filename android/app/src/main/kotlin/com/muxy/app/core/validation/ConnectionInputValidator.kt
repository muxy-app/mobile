package com.muxy.app.core.validation

import com.muxy.app.models.SshAuthMethod

enum class ConnectionInputError {
    EMPTY_NAME,
    EMPTY_HOST,
    INVALID_HOST,
    MISSING_PORT,
    INVALID_PORT,
    EMPTY_USERNAME,
    EMPTY_PASSWORD,
    EMPTY_PRIVATE_KEY,
}

sealed interface InputValidation<out T> {
    data class Valid<T>(
        val value: T,
    ) : InputValidation<T>

    data class Invalid(
        val error: ConnectionInputError,
    ) : InputValidation<Nothing>
}

data class ValidatedConnectionInput(
    val name: String,
    val host: String,
    val port: Int,
)

data class ValidatedSshInput(
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authMethod: SshAuthMethod,
    val secret: String,
    val passphrase: String?,
)

class ConnectionInputValidator {
    fun validate(
        name: String,
        host: String,
        portText: String,
    ): InputValidation<ValidatedConnectionInput> {
        val endpoint = validEndpoint(name, host, portText) ?: return InputValidation.Invalid(endpointError(name, host, portText))
        return InputValidation.Valid(endpoint)
    }

    fun validateSsh(
        name: String,
        host: String,
        portText: String,
        username: String,
        authMethod: SshAuthMethod,
        secret: String,
        passphrase: String,
    ): InputValidation<ValidatedSshInput> {
        val endpoint = validEndpoint(name, host, portText) ?: return InputValidation.Invalid(endpointError(name, host, portText))
        val trimmedUsername = username.trim()
        if (trimmedUsername.isEmpty()) return InputValidation.Invalid(ConnectionInputError.EMPTY_USERNAME)
        if (secret.isBlank()) return InputValidation.Invalid(missingSecretError(authMethod))
        return InputValidation.Valid(
            ValidatedSshInput(
                name = endpoint.name,
                host = endpoint.host,
                port = endpoint.port,
                username = trimmedUsername,
                authMethod = authMethod,
                secret = secret,
                passphrase = passphrase.takeIf { it.isNotBlank() },
            ),
        )
    }

    fun isValidHost(host: String): Boolean {
        if (host.isEmpty()) return false
        return host.all { it in ALLOWED_HOST_CHARACTERS }
    }

    private fun missingSecretError(authMethod: SshAuthMethod): ConnectionInputError =
        when (authMethod) {
            SshAuthMethod.PASSWORD -> ConnectionInputError.EMPTY_PASSWORD
            SshAuthMethod.PRIVATE_KEY -> ConnectionInputError.EMPTY_PRIVATE_KEY
        }

    private fun validEndpoint(
        name: String,
        host: String,
        portText: String,
    ): ValidatedConnectionInput? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null
        val trimmedHost = host.trim()
        if (!isValidHost(trimmedHost)) return null
        val port = portText.trim().toIntOrNull() ?: return null
        if (port !in PORT_RANGE) return null
        return ValidatedConnectionInput(trimmedName, trimmedHost, port)
    }

    private fun endpointError(
        name: String,
        host: String,
        portText: String,
    ): ConnectionInputError {
        if (name.isBlank()) return ConnectionInputError.EMPTY_NAME
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) return ConnectionInputError.EMPTY_HOST
        if (!isValidHost(trimmedHost)) return ConnectionInputError.INVALID_HOST
        if (portText.isBlank()) return ConnectionInputError.MISSING_PORT
        return ConnectionInputError.INVALID_PORT
    }

    private companion object {
        val PORT_RANGE = 1..65535
        val ALLOWED_HOST_CHARACTERS: Set<Char> = (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('.', '-', ':')).toSet()
    }
}
