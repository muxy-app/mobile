@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.files

import androidx.lifecycle.viewModelScope
import com.muxy.app.features.projects.ProjectIcon
import com.muxy.app.models.FileChange
import com.muxy.app.models.FileScope
import com.muxy.app.testing.FakeFileBackend
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.fileEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FileManagerViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val backend = FakeFileBackend()
    private val location = FileLocation("Project", "/project", ProjectIcon.Symbol("folder"), FileHost.Mac)
    private val models = mutableListOf<FileManagerViewModel>()

    @After
    fun cancelModels() = models.forEach { it.viewModelScope.cancel() }

    private suspend fun model(load: suspend () -> FileLocation = { location }): FileManagerViewModel =
        FileManagerViewModel(location, backend, load).also {
            models += it
            it.refresh()
        }

    private suspend fun edit(model: FileManagerViewModel) {
        model.open(fileEntry("README.md"))
        model.beginEditing()
        model.updateDraft("draft")
    }

    private suspend fun changed(vararg paths: String) {
        backend.events.emit(FileBackendEvent.FilesChanged(FileChange(backend.scope, paths.toList(), paths.isEmpty())))
    }

    @Test
    fun draftsSurviveDisconnectAndReconnectWithoutBeingOverwritten() =
        runTest {
            val model = model()
            edit(model)
            backend.connected.value = false
            runCurrent()
            assertFalse(model.state.value.canMutate)
            assertEquals(
                "draft",
                model.state.value.preview
                    ?.draft,
            )
            backend.texts["README.md"] = "remote"
            backend.connected.value = true
            model.state.first { it.preview?.hasExternalChanges == true }
            assertEquals(
                "draft",
                model.state.value.preview
                    ?.draft,
            )
            assertTrue(model.state.value.isDirty)
        }

    @Test
    fun cleanPreviewsReloadAfterReconnect() =
        runTest {
            val model = model()
            model.open(fileEntry("README.md"))
            backend.connected.value = false
            runCurrent()
            backend.texts["README.md"] = "remote"
            backend.connected.value = true
            model.state.first { it.preview?.text?.text == "remote" }
            assertFalse(model.state.value.isDirty)
        }

    @Test
    fun dirtyPreviewsAreFlaggedRatherThanReloadedAfterAnEvent() =
        runTest {
            val model = model()
            edit(model)
            val reads = backend.calls.count { it.startsWith("read:") }
            changed("README.md")
            advanceTimeBy(180)
            runCurrent()
            assertTrue(
                model.state.value.preview
                    ?.hasExternalChanges == true,
            )
            assertEquals(
                "draft",
                model.state.value.preview
                    ?.draft,
            )
            assertEquals(reads, backend.calls.count { it.startsWith("read:") })
        }

    @Test
    fun burstsOfEventsAreDebouncedFor180Milliseconds() =
        runTest {
            val model = model()
            val listings = backend.calls.count { it.startsWith("list:") }
            changed("README.md")
            advanceTimeBy(100)
            changed("README.md")
            advanceTimeBy(179)
            runCurrent()
            assertEquals(listings, backend.calls.count { it.startsWith("list:") })
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listings + 1, backend.calls.count { it.startsWith("list:") })
        }

    @Test
    fun eventsForOtherScopesDoNotRefresh() =
        runTest {
            model()
            val calls = backend.calls.toList()
            backend.events.emit(FileBackendEvent.FilesChanged(FileChange(FileScope(UUID.randomUUID()), emptyList(), true)))
            advanceTimeBy(500)
            runCurrent()
            assertEquals(calls, backend.calls)
        }

    @Test
    fun saveAndExitRemainBlockedUntilThePostSaveListingCompletes() =
        runTest {
            val model = model()
            edit(model)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            backend.onList = {
                entered.complete(Unit)
                release.await()
                backend.listing
            }
            val caller = launch { if (model.save()) model.goBack() }
            entered.await()
            assertEquals("draft", backend.texts["README.md"])
            assertTrue(model.state.value.isBusy)
            assertFalse(model.state.value.canMutate)
            model.beginEditing()
            model.updateDraft("new draft")
            model.goBack()
            assertEquals(FileRoute.PREVIEW, model.state.value.route)
            assertEquals(
                "draft",
                model.state.value.preview
                    ?.draft,
            )
            assertFalse(
                model.state.value.preview
                    ?.isEditing == true,
            )
            release.complete(Unit)
            caller.join()
            assertEquals(FileRoute.BROWSER, model.state.value.route)
            assertFalse(model.state.value.isBusy)
        }

    @Test
    fun disconnectDuringThePostSaveListingDoesNotAuthorizeExit() =
        runTest {
            val model = model()
            edit(model)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            backend.onList = {
                entered.complete(Unit)
                release.await()
                backend.listing
            }
            val saving = async { model.save() }
            entered.await()
            backend.connected.value = false
            runCurrent()
            release.complete(Unit)
            assertFalse(saving.await())
            assertEquals(FileRoute.PREVIEW, model.state.value.route)
            assertEquals(
                "draft",
                model.state.value.preview
                    ?.draft,
            )
            assertFalse(model.state.value.isBusy)
        }

    @Test
    fun unrelatedEventsDoNotCancelAnInFlightPreviewRefresh() =
        runTest {
            val model = model()
            model.open(fileEntry("README.md"))
            val listings = backend.calls.count { it.startsWith("list:") }
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<String>()
            var reads = 0
            backend.onRead = {
                reads += 1
                entered.complete(Unit)
                release.await()
            }
            changed("README.md")
            advanceTimeBy(180)
            runCurrent()
            entered.await()
            changed("other.txt")
            advanceTimeBy(180)
            runCurrent()
            assertTrue(model.state.value.isLoadingPreview)
            release.complete("remote change")
            model.state.first { !it.isLoadingDirectory && backend.calls.count { call -> call.startsWith("list:") } == listings + 2 }
            assertEquals(1, reads)
            assertEquals(
                "remote change",
                model.state.value.preview
                    ?.text
                    ?.text,
            )
            assertFalse(model.state.value.isLoadingPreview)
            assertFalse(model.state.value.isLoadingDirectory)
        }

    @Test
    fun matchingEventsDuringAReadAreRefreshedAfterThatReadCompletes() =
        runTest {
            val model = model()
            model.open(fileEntry("README.md"))
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<String>()
            var reads = 0
            backend.onRead = {
                if (++reads == 1) {
                    entered.complete(Unit)
                    release.await()
                } else {
                    "latest change"
                }
            }
            changed("README.md")
            advanceTimeBy(180)
            runCurrent()
            entered.await()
            changed("README.md")
            advanceTimeBy(180)
            runCurrent()
            assertEquals(1, reads)
            release.complete("first change")
            model.state.first { it.preview?.text?.text == "latest change" && !it.isLoadingPreview }
            assertEquals(2, reads)
            assertEquals(
                "latest change",
                model.state.value.preview
                    ?.text
                    ?.text,
            )
            assertFalse(model.state.value.isLoadingPreview)
        }

    @Test
    fun onlyOneMutationRunsAndEventRefreshesWaitForIt() =
        runTest {
            val model = model()
            edit(model)
            val released = CompletableDeferred<Unit>()
            backend.onWrite = { released.await() }
            val save = async { model.save() }
            runCurrent()
            assertTrue(model.state.value.isBusy)
            val listings = backend.calls.count { it.startsWith("list:") }
            assertFalse(model.save())
            assertFalse(model.create("other", false))
            changed("README.md")
            advanceTimeBy(500)
            runCurrent()
            assertEquals(listings, backend.calls.count { it.startsWith("list:") })
            assertEquals(1, backend.calls.count { it.startsWith("write:") })
            released.complete(Unit)
            assertTrue(save.await())
            assertFalse(model.state.value.isDirty)
        }

    @Test
    fun aMutationContinuesWhenItsUiCallerIsCancelled() =
        runTest {
            val model = model()
            edit(model)
            val released = CompletableDeferred<Unit>()
            backend.onWrite = { released.await() }
            val caller = launch { model.save() }
            runCurrent()
            caller.cancel()
            runCurrent()
            assertTrue(model.state.value.isBusy)
            released.complete(Unit)
            model.state.first { !it.isBusy && it.preview?.isEditing == false }
            assertEquals("draft", backend.texts["README.md"])
            assertFalse(model.state.value.isDirty)
        }

    @Test
    fun staleDirectoryResponsesCannotReplaceTheNewerDirectory() =
        runTest {
            val model = model()
            val old = CompletableDeferred<List<com.muxy.app.models.RemoteFileEntry>>()
            backend.onList = { if (it.isEmpty()) old.await() else listOf(fileEntry("Sources/new")) }
            val refresh = async { model.refreshDirectory() }
            runCurrent()
            model.goToDirectory("Sources")
            old.complete(listOf(fileEntry("old")))
            refresh.await()
            assertEquals("Sources", model.state.value.currentPath)
            assertEquals(
                listOf("Sources/new"),
                model.state.value.entries
                    .map { it.path },
            )
        }

    @Test
    fun stalePreviewResponsesCannotReplaceTheNewerFile() =
        runTest {
            val model = model()
            val old = CompletableDeferred<String>()
            backend.onRead = { if (it == "README.md") old.await() else "new content" }
            val opening = async { model.open(fileEntry("README.md")) }
            runCurrent()
            model.open(fileEntry("new.md"))
            old.complete("old content")
            opening.await()
            assertEquals(
                "new.md",
                model.state.value.preview
                    ?.entry
                    ?.path,
            )
            assertEquals(
                "new content",
                model.state.value.preview
                    ?.text
                    ?.text,
            )
        }

    @Test
    fun disconnectInvalidatesAnInFlightPreview() =
        runTest {
            val model = model()
            val content = CompletableDeferred<String>()
            backend.onRead = { content.await() }
            val opening = async { model.open(fileEntry("README.md")) }
            runCurrent()
            backend.connected.value = false
            runCurrent()
            content.complete("stale")
            opening.await()
            assertEquals(
                null,
                model.state.value.preview
                    ?.text,
            )
            assertFalse(model.state.value.isLoadingPreview)
        }

    @Test
    fun aWorktreeChangePreservesTheDraftAndBlocksSavingToTheNewScope() =
        runTest {
            val model = model()
            edit(model)
            backend.scope = FileScope(UUID.randomUUID())
            backend.events.emit(FileBackendEvent.ScopeChanged(backend.scope))
            runCurrent()
            assertTrue(model.state.value.hasContextChanged)
            assertFalse(model.save())
            assertEquals(
                "draft",
                model.state.value.preview
                    ?.draft,
            )
            assertTrue(backend.calls.none { it.startsWith("write:") })
            model.returnToBrowser()
            assertEquals(FileRoute.BROWSER, model.state.value.route)
            assertTrue(model.state.value.canMutate)
        }

    @Test
    fun failedSavesKeepTheDraftAndReleaseTheMutationGuard() =
        runTest {
            val model = model()
            edit(model)
            backend.onWrite = { error("disk full") }
            assertFalse(model.save())
            assertEquals(
                "draft",
                model.state.value.preview
                    ?.draft,
            )
            assertEquals("disk full", model.state.value.errorMessage)
            assertFalse(model.state.value.isBusy)
        }

    @Test
    fun metadataFailureStaysVisibleAndRefreshRecovers() =
        runTest {
            var failing = true
            val model = model { if (failing) error("metadata unavailable") else location }
            assertEquals("metadata unavailable", model.state.value.errorMessage)
            assertFalse(model.state.value.canMutate)
            failing = false
            model.refresh()
            assertTrue(model.state.value.canMutate)
            assertEquals(null, model.state.value.errorMessage)
        }

    @Test
    fun newFilesOpenInEditModeAndNameCollisionsDoNotWrite() =
        runTest {
            val model = model()
            assertFalse(model.create("README.md", false))
            assertTrue(backend.calls.none { it.startsWith("write:") })
            assertTrue(model.create("new.md", false))
            assertEquals(FileRoute.PREVIEW, model.state.value.route)
            assertTrue(
                model.state.value.preview
                    ?.isEditing == true,
            )
            assertNotNull(
                model.state.value.preview
                    ?.text,
            )
        }

    @Test
    fun movingIntoTheSourceOrItsCurrentParentIsDisabled() =
        runTest {
            val model = model()
            model.startMove(listOf("Sources"))
            assertFalse(model.state.value.canMoveHere)
            model.goToMoveDirectory("Sources")
            assertEquals("", model.state.value.movePath)
            model.goBack()
            model.startMove(listOf("README.md"))
            model.goToMoveDirectory("Sources")
            assertTrue(model.state.value.canMoveHere)
        }
}
