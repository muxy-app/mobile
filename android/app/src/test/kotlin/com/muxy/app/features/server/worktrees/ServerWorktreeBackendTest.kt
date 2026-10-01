@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.muxy.app.features.server.worktrees

import com.muxy.app.networking.server.ServerRequestError
import com.muxy.app.testing.FakeGitRepository
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.expectFailure
import com.muxy.app.testing.gitWorktree
import com.muxy.app.testing.serverProject
import com.muxy.app.testing.toolServer
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServerWorktreeBackendTest {
    @get:Rule val main = MainDispatcherRule()
    private val root = FakeGitRepository()
    private val child = FakeGitRepository()
    private val openedRepositories = mutableListOf<String>()
    private val connection =
        FakeServerConnection(
            projects = listOf(serverProject("muxy", "Muxy"), serverProject("feature", "Feature", parentId = "muxy", isWorktree = true)),
        ).apply {
            repositories = { id ->
                openedRepositories += id
                if (id == "muxy") root else child
            }
        }

    @Test
    fun primaryMapsToParentWhenViewingAChildProject() =
        runTest {
            val backend = ServerWorktreeBackend("feature", toolServer(connection))
            val primary = backend.row(gitWorktree("/work/main", null).copy(primary = true))
            assertEquals("muxy", primary.projectId)
            assertEquals("Muxy", primary.name)
            assertFalse(primary.isCurrent)
            assertFalse(primary.isRemovable)
            val current = backend.row(gitWorktree())
            assertTrue(current.isCurrent)
            assertFalse(current.isRemovable)
        }

    @Test
    fun unregisteredLockedBareAndPrunableRowsHaveExpectedCapabilities() =
        runTest {
            val backend = ServerWorktreeBackend("muxy", toolServer(connection))
            val unregistered = backend.row(gitWorktree(registered = null))
            assertEquals("/work/feature", unregistered.id)
            assertFalse(unregistered.isRegistered)
            assertTrue(unregistered.isOpenable)
            assertFalse(unregistered.isRemovable)
            assertFalse(backend.row(gitWorktree().copy(locked = true)).isRemovable)
            assertFalse(backend.row(gitWorktree().copy(bare = true)).isOpenable)
            assertFalse(backend.row(gitWorktree().copy(prunable = true)).isOpenable)
            assertTrue(backend.row(gitWorktree()).isRemovable)
        }

    @Test
    fun registeredOpenDoesNotRegisterAndNewRegistrationReloadsCatalog() =
        runTest {
            val server = toolServer(connection)
            val backend = ServerWorktreeBackend("muxy", server)
            assertEquals("feature", backend.open(backend.row(gitWorktree())))
            assertTrue(root.calls.isEmpty())
            root.project = serverProject("new", "New", parentId = "muxy", isWorktree = true)
            connection.projects += root.project
            assertEquals("new", backend.open(backend.row(gitWorktree("/work/new", null))))
            runCurrent()
            assertEquals(listOf("register:/work/new"), root.calls)
            assertEquals(root.project, server.project("new"))
        }

    @Test
    fun creationUsesHeadOnlyWhenCreatingABranchAndReloadsProjects() =
        runTest {
            val server = toolServer(connection)
            val backend = ServerWorktreeBackend("muxy", server)
            connection.projects += serverProject("new", "New")
            backend.create("", "new", true)
            backend.create("", "existing", false)
            runCurrent()
            assertEquals(listOf("worktree:new:HEAD", "worktree:existing:null"), root.calls)
            assertEquals("New", server.project("new")?.name)
        }

    @Test
    fun removalInspectsTheWorktreesOwnProjectAndReusesItsExpectedState() =
        runTest {
            val backend = ServerWorktreeBackend("muxy", toolServer(connection))
            val pending = backend.prepareRemoval(backend.row(gitWorktree()))
            assertTrue(pending.hasUncommittedChanges)
            assertTrue(pending.endsTerminals)
            assertEquals(
                "“Feature” and its folder will be deleted, and its terminals will end." +
                    " It has uncommitted changes that will be lost.",
                pending.confirmationMessage,
            )
            assertNull(child.removed)
            pending.remove()
            assertEquals(listOf("feature", "feature"), openedRepositories)
            assertEquals(listOf("inspect", "remove"), child.calls)
            assertSame(child.removal, child.removed)
            assertTrue(root.calls.isEmpty())
        }

    @Test
    fun cleanRemovalStillWarnsThatTheWorktreesTerminalsWillEnd() =
        runTest {
            child.removal = child.removal.copy(dirty = false)
            val backend = ServerWorktreeBackend("muxy", toolServer(connection))
            val pending = backend.prepareRemoval(backend.row(gitWorktree()))
            assertTrue(pending.endsTerminals)
            assertFalse(pending.hasUncommittedChanges)
            assertEquals("“Feature” and its folder will be deleted, and its terminals will end.", pending.confirmationMessage)
            assertNull(child.removed)
        }

    @Test
    fun confirmedRemovalCannotRunAfterConnectionChanges() =
        runTest {
            val server = toolServer(connection)
            val backend = ServerWorktreeBackend("muxy", server)
            val pending = backend.prepareRemoval(backend.row(gitWorktree()))
            server.setWantsConnection(false)
            expectFailure<ServerRequestError> { pending.remove() }
            assertNull(child.removed)
        }
}
