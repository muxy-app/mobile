package com.muxy.app.features.git

import androidx.lifecycle.viewModelScope
import com.muxy.app.models.GitDiffKey
import com.muxy.app.models.VcsBranches
import com.muxy.app.models.VcsDiff
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.models.VcsStatus
import com.muxy.app.testing.DEMO_PROJECT_ID
import com.muxy.app.testing.DEMO_WEB_PROJECT_ID
import com.muxy.app.testing.DemoProjectChannel
import com.muxy.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val channel = DemoProjectChannel()
    private val backend = ChannelGitBackend(DEMO_PROJECT_ID, channel)
    private val models = mutableListOf<GitViewModel>()

    private fun model(source: GitBackend = backend) = GitViewModel(source).also { models += it }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun demoRefreshLoadsStatusBranchesAndDiff() =
        runTest {
            val model = model()
            model.refreshStatus()
            model.refreshBranches()
            val key = GitDiffKey("Sources/App.swift", true)
            model.loadDiff(key)
            assertEquals(
                "main",
                model.state.value.status
                    ?.branch,
            )
            assertEquals(2, model.state.value.totalChanges)
            assertEquals(
                "main",
                model.state.value.branches
                    ?.defaultBranch,
            )
            assertEquals(
                2L,
                model.state.value.diffs[key]
                    ?.additions,
            )
            assertEquals(
                1L,
                model.state.value.diffs[key]
                    ?.deletions,
            )
            assertNull(model.state.value.errorMessage)
        }

    @Test
    fun commitClearsChangesAndInvalidatesDiffCache() =
        runTest {
            val model = model()
            model.loadDiff(GitDiffKey("README.md", false))
            assertTrue(model.commit("  First commit  ", true))
            assertEquals(0, model.state.value.totalChanges)
            assertEquals(
                1L,
                model.state.value.status
                    ?.aheadCount,
            )
            assertTrue(
                model.state.value.diffs
                    .isEmpty(),
            )
            assertFalse(model.state.value.isBusy)
            assertEquals("Push 1", model.pushTitle(model.state.value.status!!))
            assertTrue(model.push())
            assertEquals(
                0L,
                model.state.value.status
                    ?.aheadCount,
            )
        }

    @Test
    fun unstagedChangesRemainWhenStageAllIsOff() =
        runTest {
            val model = model()
            assertTrue(model.commit("Staged only", false))
            assertEquals(1, model.state.value.totalChanges)
            assertEquals(
                "README.md",
                model.state.value.status
                    ?.changedFiles
                    ?.single()
                    ?.path,
            )
        }

    @Test
    fun createSwitchAndPullRequestMutateDemoState() =
        runTest {
            val model = model()
            assertTrue(model.createBranch("  feature/android  "))
            assertEquals(
                "feature/android",
                model.state.value.status
                    ?.branch,
            )
            assertTrue(
                model.state.value.branches!!
                    .locals
                    .contains("feature/android"),
            )
            val created = model.createPullRequest("  Android  ", "  Details  ", "main", true)
            assertEquals(43L, created?.number)
            val pullRequest =
                model.state.value.status!!
                    .pullRequest!!
            assertTrue(pullRequest.isDraft)
            assertTrue(model.mergePullRequest(pullRequest, VcsMergeMethod.SQUASH, true))
            assertEquals(
                "MERGED",
                model.state.value.status
                    ?.pullRequest
                    ?.state,
            )
            assertTrue(model.switchBranch("main"))
            assertEquals(
                "main",
                model.state.value.branches
                    ?.current,
            )
        }

    @Test
    fun webDemoStartsCleanAheadWithPassingPullRequest() =
        runTest {
            val model = model(ChannelGitBackend(DEMO_WEB_PROJECT_ID, channel))
            model.refreshStatus()
            val status = model.state.value.status!!
            assertEquals("feature/native-git", status.branch)
            assertEquals(0, model.state.value.totalChanges)
            assertEquals(2L, status.aheadCount)
            assertEquals(42L, status.pullRequest?.number)
            assertEquals("4/4 passing", status.pullRequest?.checks?.label)
        }

    @Test
    fun onlyMuxy2PublishesAZeroAheadBranchWithoutUpstream() =
        runTest {
            val status = backend.status().copy(aheadCount = 0, hasUpstream = false)
            val muxy1 = model()
            val muxy2 =
                model(
                    object : GitBackend by backend {
                        override val canPublishBranch = true
                    },
                )
            assertFalse(muxy1.canPush(status))
            assertTrue(muxy2.canPush(status))
            assertEquals("Publish Branch", muxy2.pushTitle(status))
            assertFalse(muxy2.canPush(status.copy(hasUpstream = true)))
        }

    @Test
    fun olderStatusAndBranchesCannotReplaceNewerResponses() =
        runTest {
            val original = backend.status()
            val oldStatus = CompletableDeferred<VcsStatus>()
            val oldBranches = CompletableDeferred<VcsBranches>()
            var statuses = 0
            var branches = 0
            val model =
                model(
                    object : GitBackend by backend {
                        override suspend fun status(): VcsStatus {
                            if (++statuses == 1) return oldStatus.await()
                            return original.copy(branch = "new")
                        }

                        override suspend fun branches(): VcsBranches =
                            if (++branches ==
                                1
                            ) {
                                oldBranches.await()
                            } else {
                                VcsBranches("new", listOf("new"))
                            }
                    },
                )
            val statusRequest = launch(start = CoroutineStart.UNDISPATCHED) { model.refreshStatus() }
            val branchRequest = launch(start = CoroutineStart.UNDISPATCHED) { model.refreshBranches() }
            model.refreshStatus()
            model.refreshBranches()
            oldStatus.complete(original)
            oldBranches.complete(VcsBranches("old", listOf("old")))
            statusRequest.join()
            branchRequest.join()
            assertEquals(
                "new",
                model.state.value.status
                    ?.branch,
            )
            assertEquals(
                "new",
                model.state.value.branches
                    ?.current,
            )
        }

    @Test
    fun fullDiffWinsOverOlderCappedResponseAndInvalidationDropsInflightDiffs() =
        runTest {
            val key = GitDiffKey("README.md", false)
            val original = backend.diff(key, false)
            var pending = CompletableDeferred<VcsDiff>()
            val model =
                model(
                    object : GitBackend by backend {
                        override suspend fun diff(
                            key: GitDiffKey,
                            full: Boolean,
                        ): VcsDiff = if (full) original else pending.await()
                    },
                )
            val first = launch(start = CoroutineStart.UNDISPATCHED) { model.loadDiff(key) }
            model.loadDiff(key, true)
            pending.complete(original.copy(truncated = true))
            first.join()
            assertFalse(
                model.state.value.diffs
                    .getValue(key)
                    .truncated,
            )
            pending = CompletableDeferred()
            val stale = launch(start = CoroutineStart.UNDISPATCHED) { model.loadDiff(key) }
            model.invalidateDiffs()
            pending.complete(original)
            stale.join()
            assertTrue(
                model.state.value.diffs
                    .isEmpty(),
            )
            assertTrue(
                model.state.value.loadingDiffs
                    .isEmpty(),
            )
        }

    @Test
    fun mutationsAreSerializedAndSurviveCancellationOfTheScreenCaller() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            var commits = 0
            val model =
                model(
                    object : GitBackend by backend {
                        override suspend fun commit(
                            message: String,
                            stageAll: Boolean,
                        ) {
                            commits += 1
                            entered.complete(Unit)
                            release.await()
                            backend.commit(message, stageAll)
                        }
                    },
                )
            val caller = launch { model.commit("first", true) }
            entered.await()
            assertFalse(model.commit("second", true))
            caller.cancel()
            assertTrue(model.state.value.isBusy)
            release.complete(Unit)
            model.state.first { !it.isBusy }
            assertEquals(1, commits)
            assertEquals(0, model.state.value.totalChanges)
            val completion = model.state.value.completedForm!!
            assertEquals(GitRoute.Commit, completion.route)
            assertNull(completion.url)
            assertTrue(model.acknowledgeFormCompletion(completion))
            assertNull(model.state.value.completedForm)
        }

    @Test
    fun failuresKeepStateAndReleaseBusyWhileNotRepositoryClearsStatus() =
        runTest {
            var missing = false
            val model =
                model(
                    object : GitBackend by backend {
                        override suspend fun status(): VcsStatus = if (missing) throw GitException.NotRepository() else backend.status()

                        override suspend fun pull() {
                            error("Cannot pull")
                        }
                    },
                )
            model.refreshStatus()
            assertFalse(model.pull())
            assertEquals("Cannot pull", model.state.value.errorMessage)
            assertFalse(model.state.value.isBusy)
            assertEquals(
                "main",
                model.state.value.status
                    ?.branch,
            )
            missing = true
            model.refreshStatus()
            assertNull(model.state.value.status)
            assertFalse(model.state.value.isLoadingStatus)
        }

    @Test
    fun pullRequestCompletionSurvivesCallerCancellationUntilAcknowledged() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val model =
                model(
                    object : GitBackend by backend {
                        override suspend fun createPullRequest(
                            title: String,
                            body: String,
                            baseBranch: String?,
                            draft: Boolean,
                        ): com.muxy.app.models.VcsPrCreated {
                            started.complete(Unit)
                            release.await()
                            return backend.createPullRequest(title, body, baseBranch, draft)
                        }
                    },
                )
            val caller = launch { model.createPullRequest("Android", "Details", "main", false) }
            started.await()
            caller.cancel()
            release.complete(Unit)
            val completion = model.state.first { !it.isBusy && it.completedForm != null }.completedForm!!
            assertEquals(GitRoute.NewPullRequest, completion.route)
            assertEquals(
                model.state.value.status
                    ?.pullRequest
                    ?.url,
                completion.url,
            )
            assertTrue(completion.url!!.startsWith("https://"))
            model.refreshStatus()
            assertEquals(completion, model.state.value.completedForm)
            val drafts = GitDrafts(title = "Android", body = "Details", base = "main", message = "Other draft")
            val cleared = drafts.clearing(completion.route)
            assertFalse(cleared.isDirty(GitRoute.NewPullRequest))
            assertEquals("Other draft", cleared.message)
            assertTrue(model.acknowledgeFormCompletion(completion))
            assertNull(model.state.value.completedForm)
            assertFalse(model.acknowledgeFormCompletion(completion))
        }

    @Test
    fun aStaleAcknowledgementCannotClearANewerFormCompletion() =
        runTest {
            val model = model()
            assertTrue(model.createBranch("first"))
            val first = model.state.value.completedForm!!
            assertEquals(GitRoute.NewBranch, first.route)
            assertNull(first.url)
            assertTrue(model.createBranch("second"))
            val second = model.state.value.completedForm!!
            assertFalse(first.id == second.id)
            assertFalse(model.acknowledgeFormCompletion(first))
            assertEquals(second, model.state.value.completedForm)
            assertTrue(model.acknowledgeFormCompletion(second))
            assertNull(model.state.value.completedForm)
        }

    @Test
    fun failedSubmissionsDoNotPublishFormCompletions() =
        runTest {
            val model =
                model(
                    object : GitBackend by backend {
                        override suspend fun commit(
                            message: String,
                            stageAll: Boolean,
                        ) {
                            error("Commit rejected")
                        }
                    },
                )
            assertFalse(model.commit("Message", true))
            assertNull(model.state.value.completedForm)
            assertEquals("Commit rejected", model.state.value.errorMessage)
        }

    @Test
    fun blankDraftsDoNotStartMutations() =
        runTest {
            val model = model()
            assertFalse(model.commit("  ", true))
            assertFalse(model.createBranch("\n"))
            assertNull(model.createPullRequest(" ", "body", null, false))
            assertNull(model.state.value.status)
        }
}
