package com.muxy.app.features.server.git

import com.muxy.app.features.git.GitException
import com.muxy.app.models.GitDiffKey
import com.muxy.app.models.VcsMergeMethod
import com.muxy.app.networking.server.ServerRequestError
import com.muxy.app.testing.FakeGitRepository
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.expectFailure
import com.muxy.app.testing.gitPullRequest
import com.muxy.app.testing.toolServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uniffi.muxy_mobile.MobileException

class ServerGitBackendTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeGitRepository()
    private val connection = FakeServerConnection().apply { repositories = { repository } }

    @Test
    fun successfulStatusIncludesPullRequestWithoutSummaryProbe() =
        runTest {
            val backend = ServerGitBackend("muxy", toolServer(connection))
            assertEquals("main", backend.status().branch)
            assertTrue(backend.canPublishBranch)
            assertEquals(listOf("status:true"), repository.calls)
            assertEquals(1, repository.closeCount)
        }

    @Test
    fun nullSummaryAfterServerErrorMeansNotARepository() =
        runTest {
            repository.onStatus = { throw MobileException.Server("not a repository") }
            repository.onSummary = { null }
            expectFailure<GitException.NotRepository> { ServerGitBackend("muxy", toolServer(connection)).status() }
            assertEquals(listOf("status:true", "summary"), repository.calls)
            assertEquals(1, repository.closeCount)
        }

    @Test
    fun repositoryStatusFailureIsPreservedWhenSummaryExistsOrFails() =
        runTest {
            val backend = ServerGitBackend("muxy", toolServer(connection))
            repository.onStatus = { throw MobileException.Server("git failed") }
            assertTrue(expectFailure<ServerRequestError> { backend.status() }.message.orEmpty().contains("git failed"))
            repository.onSummary = { throw MobileException.Timeout() }
            assertTrue(expectFailure<ServerRequestError> { backend.status() }.message.orEmpty().contains("git failed"))
        }

    @Test
    fun transportFailuresAndCancellationDoNotProbeForRepository() =
        runTest {
            val backend = ServerGitBackend("muxy", toolServer(connection))
            repository.onStatus = { throw MobileException.Timeout() }
            expectFailure<ServerRequestError> { backend.status() }
            repository.onStatus = { throw CancellationException() }
            expectFailure<CancellationException> { backend.status() }
            assertEquals(listOf("status:true", "status:true"), repository.calls)
        }

    @Test
    fun diffForwardsStagingAndUses800LinesOrNoLimit() =
        runTest {
            val backend = ServerGitBackend("muxy", toolServer(connection))
            assertTrue(backend.diff(GitDiffKey("README.md", true), false).truncated)
            backend.diff(GitDiffKey("README.md", false), true)
            assertEquals(listOf("diff:README.md:true:800", "diff:README.md:false:null"), repository.calls)
            assertEquals(2, repository.closeCount)
        }

    @Test
    fun mutationsForwardArgumentsAndCreatingBranchDoesNotSwitchTwice() =
        runTest {
            val backend = ServerGitBackend("muxy", toolServer(connection))
            backend.commit("message", true)
            backend.pull()
            backend.push()
            backend.createBranch("feature")
            backend.switchBranch("main")
            backend.createPullRequest("title", "body", "main", true)
            backend.mergePullRequest(gitPullRequest().toVcsPullRequest(), VcsMergeMethod.SQUASH, true)
            assertEquals(
                listOf(
                    "commit:message:true",
                    "pull",
                    "push:false",
                    "create:feature",
                    "switch:main",
                    "pr:title:body:main:true",
                    "merge:42:SQUASH:true:head-oid",
                ),
                repository.calls,
            )
            assertEquals(7, repository.closeCount)
        }
}
