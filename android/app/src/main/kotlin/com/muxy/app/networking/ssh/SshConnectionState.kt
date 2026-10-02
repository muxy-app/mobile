package com.muxy.app.networking.ssh

sealed interface SshConnectionState {
    data object Idle : SshConnectionState

    data object Connecting : SshConnectionState

    data object Connected : SshConnectionState

    data object Disconnected : SshConnectionState

    data object Exited : SshConnectionState

    data class Failed(
        val error: SshError,
    ) : SshConnectionState
}
