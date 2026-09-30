package com.muxy.app.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SshAuthMethod {
    @SerialName("password")
    PASSWORD,

    @SerialName("privateKey")
    PRIVATE_KEY,
}

@Serializable
data class SshConfig(
    val username: String,
    val authMethod: SshAuthMethod,
)
