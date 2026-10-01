@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.git

import androidx.lifecycle.viewModelScope
import com.muxy.app.testing.DEMO_PROJECT_ID
import com.muxy.app.testing.DemoProjectChannel
import com.muxy.app.testing.InMemoryWorktreeCache
import com.muxy.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class WorktreesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val cache = InMemoryWorktreeCache()
    private val connectionId = UUID.randomUUID()
    private val backend = ChannelWorktreeBackend(connectionId, DEMO_PROJECT_ID, DemoProjectChannel(), cache)
    private val models = mutableListOf<WorktreesViewModel>()

    private fun model(source: WorktreeBackend = backend) = WorktreesViewModel(source).also { models += it }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun demoListsPrimaryCachesCreatesSwitchesAndRemovesWorktrees() =
        runTest {
            val model = model()
            model.refresh()
            val primary =
                model.state.value.rows!!
                    .single()
            assertTrue(primary.isCurrent)
            assertFalse(primary.isRemovable)
            assertEquals(1, cache.load(connectionId, DEMO_PROJECT_ID)?.size)
            assertTrue(model.create("  Android  ", "  feature/android  ", true))
            val created =
                model.state.value.rows!!
                    .first { it.id != primary.id }
            assertEquals("Android", created.name)
            assertEquals("feature/android", created.branch)
            assertEquals(2, cache.load(connectionId, DEMO_PROJECT_ID)?.size)
            model.open(created)
            assertTrue(
                model.state.value.rows!!
                    .first { it.id == created.id }
                    .isCurrent,
            )
            model.open(primary)
            model.prepareRemoval(created)
            val pending = model.state.value.pendingRemoval!!
            assertFalse(pending.endsTerminals)
            assertEquals("This removes the worktree and its files.", pending.confirmationMessage)
            assertEquals(
                created.id,
                model.state.value.pendingRemoval
                    ?.row
                    ?.id,
            )
            model.remove()
            assertNull(model.state.value.pendingRemoval)
            assertEquals(
                listOf(primary.id),
                model.state.value.rows!!
                    .map { it.id },
            )
            assertTrue(model.state.value.revision >= 4)
        }

    @Test
    fun cacheAppearsBeforeRefreshAndIsNotOverwrittenByLateCacheResponse() =
        runTest {
            val rows = backend.rows()
            val pending = CompletableDeferred<List<WorktreeRow>?>()
            val source =
                object : WorktreeBackend by backend {
                    override val changes = emptyFlow<Unit>()

                    override suspend fun cached(): List<WorktreeRow>? = pending.await()
                }
            val model = model(source)
            model.refresh()
            pending.complete(emptyList())
            runCurrent()
            assertEquals(rows, model.state.value.rows)
            val cached =
                model(
                    object : WorktreeBackend by backend {
                        override val changes = emptyFlow<Unit>()
                    },
                )
            assertEquals(rows.map { it.copy(isCurrent = false) }, cached.state.value.rows)
        }

    @Test
    fun oldRefreshCannotOverwriteNewerRows() =
        runTest {
            val rows = backend.rows()
            val pending = CompletableDeferred<List<WorktreeRow>>()
            var requests = 0
            val model =
                model(
                    object : WorktreeBackend by backend {
                        override val changes = emptyFlow<Unit>()

                        override suspend fun rows(): List<WorktreeRow> = if (++requests == 1) pending.await() else rows
                    },
                )
            val old = launch(start = CoroutineStart.UNDISPATCHED) { model.refresh() }
            model.refresh()
            pending.complete(emptyList())
            old.join()
            assertEquals(rows, model.state.value.rows)
        }

    @Test
    fun removalRequiresConfirmationAndCancelDoesNotMutateOrIncrementRevision() =
        runTest {
            var removed = false
            val row = backend.rows().single().copy(isRemovable = true)
            val model =
                model(
                    object : WorktreeBackend by backend {
                        override val changes = emptyFlow<Unit>()

                        override suspend fun prepareRemoval(row: WorktreeRow) = PendingWorktreeRemoval(row, true) { removed = true }
                    },
                )
            model.prepareRemoval(row.copy(isRemovable = false))
            assertNull(model.state.value.pendingRemoval)
            model.prepareRemoval(row)
            assertTrue(
                model.state.value.pendingRemoval!!
                    .hasUncommittedChanges,
            )
            assertFalse(removed)
            assertEquals(0L, model.state.value.revision)
            model.cancelRemoval()
            model.remove()
            assertFalse(removed)
        }

    @Test
    fun onlyOneChangeRunsAndCallerCancellationDoesNotCancelCreation() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var creates = 0
            val model =
                model(
                    object : WorktreeBackend by backend {
                        override val changes = emptyFlow<Unit>()

                        override suspend fun create(
                            name: String,
                            branch: String,
                            createsBranch: Boolean,
                        ) {
                            creates += 1
                            started.complete(Unit)
                            release.await()
                            backend.create(name, branch, createsBranch)
                        }
                    },
                )
            val caller = launch { model.create("Android", "feature/android", true) }
            started.await()
            assertFalse(model.create("Other", "other", true))
            caller.cancel()
            assertTrue(model.state.value.isBusy)
            release.complete(Unit)
            model.state.first { !it.isBusy }
            assertEquals(1, creates)
            assertEquals(
                2,
                model.state.value.rows
                    ?.size,
            )
            val completion = model.state.value.completedForm!!
            assertEquals(GitRoute.NewWorktree, completion.route)
            assertNull(completion.url)
            assertFalse(model.acknowledgeFormCompletion(completion.copy(id = UUID.randomUUID())))
            assertTrue(model.acknowledgeFormCompletion(completion))
            assertNull(model.state.value.completedForm)
            assertFalse(model.acknowledgeFormCompletion(completion))
        }

    @Test
    fun failedCreationDoesNotPublishAFormCompletion() =
        runTest {
            val model =
                model(
                    object : WorktreeBackend by backend {
                        override val changes = emptyFlow<Unit>()

                        override suspend fun create(
                            name: String,
                            branch: String,
                            createsBranch: Boolean,
                        ) {
                            error("Cannot create worktree")
                        }
                    },
                )
            assertFalse(model.create("Android", "feature/android", true))
            assertNull(model.state.value.completedForm)
            assertEquals("Cannot create worktree", model.state.value.errorMessage)
        }

    @Test
    fun invalidInputAndDisabledRowsDoNotRunOperations() =
        runTest {
            val model =
                model(
                    object : WorktreeBackend by backend {
                        override val changes = emptyFlow<Unit>()
                    },
                )
            assertFalse(model.create(" ", "branch", true))
            assertFalse(model.create("Name", " ", true))
            assertNull(model.open(backend.rows().single().copy(isOpenable = false)))
            assertEquals(0L, model.state.value.revision)
        }

    @Test
    fun changeEventsRefreshRowsAndFailuresKeepCachedRows() =
        runTest {
            val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
            var fails = false
            val model =
                model(
                    object : WorktreeBackend by backend {
                        override val changes = events

                        override suspend fun rows(): List<WorktreeRow> = if (fails) error("offline") else backend.rows()
                    },
                )
            events.emit(Unit)
            runCurrent()
            model.state.first { it.rows != null && !it.isLoading }
            val rows = model.state.value.rows
            fails = true
            model.refresh()
            assertEquals(rows, model.state.value.rows)
            assertEquals("offline", model.state.value.errorMessage)
            assertFalse(model.state.value.isBusy)
        }
}
