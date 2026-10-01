package com.muxy.app.features.server

import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class FileWatches(
    private val scope: CoroutineScope,
) {
    private data class Subscriber(
        val projectId: String,
        val channel: Channel<List<String>>,
    )

    private val subscribers = linkedMapOf<UUID, Subscriber>()
    private val watched = mutableSetOf<String>()
    private val refresh = CoalescedRefresh(scope)
    private var connection: ServerConnection? = null
    private val ownerContext = scope.coroutineContext.minusKey(Job)

    fun changes(projectId: String): Flow<List<String>> =
        flow {
            val id = UUID.randomUUID()
            val channel = Channel<List<String>>(EVENT_BUFFER)
            try {
                withContext(ownerContext) {
                    val projects = subscribers.values.map { it.projectId }.toSet()
                    check(projectId in projects || projects.size < MAXIMUM_WATCHES) { "At most 32 projects can be watched at once." }
                    subscribers[id] = Subscriber(projectId, channel)
                    refresh.request(::reconcile)
                }
                emitAll(channel.receiveAsFlow())
            } finally {
                withContext(NonCancellable + ownerContext) {
                    subscribers.remove(id)
                    channel.close()
                    refresh.request(::reconcile)
                }
            }
        }

    fun connectionChanged(connection: ServerConnection?) {
        refresh.cancel()
        this.connection = connection
        watched.clear()
        if (connection != null) refresh.request(::reconcile)
    }

    fun deliver(
        projectId: String,
        paths: List<String>,
    ) {
        subscribers.values.filter { it.projectId == projectId }.forEach { subscriber ->
            if (subscriber.channel.trySend(paths).isSuccess) return@forEach
            while (subscriber.channel.tryReceive().isSuccess) {}
            subscriber.channel.trySend(emptyList())
        }
    }

    private suspend fun reconcile() {
        val current = connection ?: return
        val desired = subscribers.values.map { it.projectId }.toSet()
        for (projectId in watched.toSet() - desired) update(current, projectId, watching = false)
        for (projectId in desired - watched) update(current, projectId, watching = true)
    }

    private suspend fun update(
        current: ServerConnection,
        projectId: String,
        watching: Boolean,
    ) {
        if (current !== connection) return
        attempt {
            current.files(projectId).use { if (watching) it.watch() else it.unwatch() }
        }.onSuccess {
            if (current !== connection) return@onSuccess
            if (watching) watched.add(projectId) else watched.remove(projectId)
        }.onFailure {
            Log.files.error("Updating a file watch failed: ${ServerFailure.from(it)}")
        }
    }

    private companion object {
        const val MAXIMUM_WATCHES = 32
        const val EVENT_BUFFER = 64
    }
}
