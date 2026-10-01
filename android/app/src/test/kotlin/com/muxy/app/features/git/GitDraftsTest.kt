package com.muxy.app.features.git

import com.muxy.app.models.GitDiffKey
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitDraftsTest {
    @Test
    fun eachFormTracksUnsavedTextAndChangedOptions() {
        val defaults = GitDrafts()
        assertTrue(defaults.stageAll)
        assertTrue(defaults.createsBranch)
        assertFalse(defaults.isDirty(GitRoute.Commit))
        assertTrue(defaults.copy(stageAll = false).isDirty(GitRoute.Commit))
        assertTrue(defaults.copy(message = "message").isDirty(GitRoute.Commit))
        assertTrue(defaults.copy(branch = "feature").isDirty(GitRoute.NewBranch))
        assertTrue(defaults.copy(body = "description").isDirty(GitRoute.NewPullRequest))
        assertTrue(defaults.copy(base = "main").isDirty(GitRoute.NewPullRequest))
        assertTrue(defaults.copy(draft = true).isDirty(GitRoute.NewPullRequest))
        assertTrue(defaults.copy(createsBranch = false).isDirty(GitRoute.NewWorktree))
        assertTrue(defaults.copy(worktreeName = "name").isDirty(GitRoute.NewWorktree))
        assertTrue(defaults.copy(worktreeBranch = "feature").isDirty(GitRoute.NewWorktree))
    }

    @Test
    fun clearingOneFormKeepsOtherDraftsAndRestoresDefaultOptions() {
        val drafts =
            GitDrafts(
                message = "commit",
                stageAll = false,
                branch = "feature",
                title = "PR",
                draft = true,
                worktreeName = "tree",
                createsBranch = false,
            )
        for (route in listOf(GitRoute.Commit, GitRoute.NewBranch, GitRoute.NewPullRequest, GitRoute.NewWorktree)) {
            val cleared = drafts.clearing(route)
            assertFalse(cleared.isDirty(route))
            assertTrue(cleared.isDirty(if (route == GitRoute.Commit) GitRoute.NewBranch else GitRoute.Commit))
        }
        assertEquals(drafts, drafts.clearing(GitRoute.Overview))
    }

    @Test
    fun draftAndDiffRouteRoundTripForSavedNavigationState() {
        val draft = GitDrafts(message = "message", stageAll = false, body = "description", worktreeBranch = "feature")
        assertEquals(draft, ProtocolJson.decodeFromString<GitDrafts>(ProtocolJson.encodeToString(draft)))
        val route: GitRoute = GitRoute.Diff(GitDiffKey("Sources/App.swift", true))
        assertEquals(route, ProtocolJson.decodeFromString<GitRoute>(ProtocolJson.encodeToString(route)))
        assertEquals("App.swift", route.title)
    }
}
