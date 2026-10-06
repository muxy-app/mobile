package com.muxy.app.features.server

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.networking.server.ServerConnector
import com.muxy.app.persistence.credentials.CredentialStore
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

data class ServerFocus(
    val serverId: String? = null,
    val projectId: String? = null,
)

class ServerDirectory(
    private val credentials: CredentialStore,
    private val connector: ServerConnector,
    private val scope: CoroutineScope,
    private val remoteProvider: ((UUID) -> ServerConnectionProvider)? = null,
) : DefaultLifecycleObserver {
    private val controllers = mutableMapOf<String, ServerController>()
    private var focus = ServerFocus()
    private var isForeground = false

    fun controller(serverId: String): ServerController {
        controllers[serverId]?.let { return it }
        val provider =
            if (serverId.startsWith("ssh:")) {
                checkNotNull(remoteProvider)(UUID.fromString(serverId.removePrefix("ssh:")))
            } else {
                PairedServerConnectionProvider(connector) { credential(serverId) }
            }
        val controller = ServerController(serverId, provider, scope)
        controllers[serverId] = controller
        update(serverId, controller)
        return controller
    }

    fun focus(focus: ServerFocus) {
        if (this.focus == focus) return
        this.focus = focus
        controllers.forEach { (serverId, controller) -> update(serverId, controller) }
    }

    fun forget(serverId: String) {
        controllers.remove(serverId)?.setWantsConnection(false)
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
        controller.showProject(focus.projectId.takeIf { isActive })
        controller.setWantsConnection(isForeground && isActive)
    }

    private suspend fun credential(serverId: String) =
        attempt { credentials.credential(serverId) }
            .onFailure { Log.persistence.error("Reading a paired computer failed: ${it.javaClass.simpleName}") }
            .getOrNull()
}
