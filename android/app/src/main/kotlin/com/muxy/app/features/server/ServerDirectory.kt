package com.muxy.app.features.server

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.features.push.PushTarget
import com.muxy.app.networking.server.ServerConnector
import com.muxy.app.persistence.credentials.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ServerFocus(
    val serverId: String? = null,
    val projectId: String? = null,
    val session: PushTarget? = null,
)

class ServerDirectory(
    private val credentials: CredentialStore,
    private val connector: ServerConnector,
    private val scope: CoroutineScope,
    private val remoteProvider: ((UUID) -> ServerConnectionProvider)? = null,
    private val push: ServerPushContext? = null,
) : DefaultLifecycleObserver {
    private val controllers = mutableMapOf<String, ServerController>()
    private val pushCapableServers = MutableStateFlow(emptySet<String>())
    private val pushWatchers = mutableMapOf<String, Job>()

    val supportsPushNotifications: Flow<Boolean> = pushCapableServers.map { it.isNotEmpty() }.distinctUntilChanged()
    private var focus = ServerFocus()
    private var browsingServerIds = emptySet<String>()
    private var isForeground = false

    fun controller(serverId: String): ServerController {
        controllers[serverId]?.let { return it }
        val isRemote = serverId.startsWith("ssh:")
        val provider =
            if (isRemote) {
                checkNotNull(remoteProvider)(UUID.fromString(serverId.removePrefix("ssh:")))
            } else {
                PairedServerConnectionProvider(connector) { credential(serverId) }
            }
        val controller = ServerController(serverId, provider, scope, pushContext = push.takeUnless { isRemote })
        controllers[serverId] = controller
        pushWatchers[serverId] =
            scope.launch {
                controller.supportsPushNotifications.collect { supported ->
                    pushCapableServers.update { if (supported) it + serverId else it - serverId }
                }
            }
        update(serverId, controller)
        return controller
    }

    fun focus(focus: ServerFocus) {
        if (this.focus == focus) return
        this.focus = focus
        controllers.forEach { (serverId, controller) -> update(serverId, controller) }
    }

    fun browse(serverIds: Set<String>) {
        if (browsingServerIds == serverIds) return
        browsingServerIds = serverIds
        serverIds.forEach(::controller)
        controllers.forEach { (serverId, controller) -> update(serverId, controller) }
    }

    fun forget(serverId: String) {
        browsingServerIds -= serverId
        val controller = controllers.remove(serverId)
        pushWatchers.remove(serverId)?.cancel()
        pushCapableServers.update { it - serverId }
        val push = push
        if (push == null) {
            controller?.let(::retire)
            return
        }
        scope.launch {
            controller?.unregisterPush()
            controller?.let(::retire)
            push.forget(serverId)
        }
    }

    fun isConnected(serverId: String): Boolean = isForeground && controllers[serverId]?.phase?.value == ServerPhase.Connected

    fun pushContextDidChange() {
        controllers.values.forEach(ServerController::syncPushRegistration)
    }

    private fun retire(controller: ServerController) {
        controller.setBrowsing(false)
        controller.showSession(null)
        controller.showProject(null)
        controller.setWantsConnection(false)
    }

    fun credentialDidChange(serverId: String) {
        controllers[serverId]?.credentialDidChange()
    }

    override fun onStart(owner: LifecycleOwner) {
        setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        setForeground(false)
    }

    private fun setForeground(foreground: Boolean) {
        if (isForeground == foreground) return
        isForeground = foreground
        controllers.forEach { (serverId, controller) -> update(serverId, controller) }
    }

    private fun update(
        serverId: String,
        controller: ServerController,
    ) {
        val isActive = serverId == focus.serverId
        val isBrowsing = isForeground && serverId in browsingServerIds
        val session = focus.session.takeIf { isActive }
        controller.showSession(session)
        if (session == null) controller.showProject(focus.projectId.takeIf { isActive })
        controller.setBrowsing(isBrowsing)
        controller.setWantsConnection(isForeground && (isActive || isBrowsing))
    }

    private suspend fun credential(serverId: String) =
        attempt { credentials.credential(serverId) }
            .onFailure { Log.persistence.error("Reading a paired computer failed: ${it.javaClass.simpleName}") }
            .getOrNull()
}
