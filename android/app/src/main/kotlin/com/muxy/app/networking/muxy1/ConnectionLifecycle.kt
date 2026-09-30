package com.muxy.app.networking.muxy1

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.persistence.connections.ConnectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ConnectionFocus {
    data class Device(
        val connectionId: UUID,
    ) : ConnectionFocus

    data object None : ConnectionFocus

    data object Hold : ConnectionFocus
}

class ConnectionLifecycle(
    private val manager: ConnectionManager,
    private val connections: ConnectionStore,
    scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val target = MutableStateFlow(Target(ConnectionFocus.Hold, isForeground = false))

    init {
        scope.launch { target.collectLatest(::reconcile) }
    }

    fun focus(focus: ConnectionFocus) {
        target.update { it.copy(focus = focus) }
    }

    override fun onStart(owner: LifecycleOwner) {
        target.update { it.copy(isForeground = true) }
    }

    override fun onStop(owner: LifecycleOwner) {
        target.update { it.copy(isForeground = false) }
    }

    private suspend fun reconcile(target: Target) {
        if (!target.isForeground) return manager.disconnectUnlessPairing()
        when (val focus = target.focus) {
            is ConnectionFocus.Device -> connections.load().firstOrNull { it.id == focus.connectionId }?.let { manager.ensureConnected(it) }
            ConnectionFocus.None -> manager.disconnect()
            ConnectionFocus.Hold -> Unit
        }
    }

    private data class Target(
        val focus: ConnectionFocus,
        val isForeground: Boolean,
    )
}
