package com.muxy.app.features.server

import com.muxy.app.features.projectdetail.ProjectTabsStatus
import com.muxy.app.features.projects.ProjectIcon
import com.muxy.app.features.projects.ProjectListStatus
import com.muxy.app.features.projects.ProjectLogo
import com.muxy.app.networking.server.ServerFailure
import com.muxy.app.testing.serverProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProjectListingTest {
    private val catalog = ProjectCatalog(hasLoaded = true)

    @Test
    fun anEmojiIconIsShownAsIs() {
        assertEquals(ProjectIcon.Emoji("🚀"), ServerProjectListing.icon(serverProject("a", "api", icon = " 🚀 ")))
    }

    @Test
    fun aKnownSymbolIsShown() {
        assertEquals(ProjectIcon.Symbol("terminal"), ServerProjectListing.icon(serverProject("a", "api", icon = "sf:terminal")))
    }

    @Test
    fun anUnknownSymbolFallsBackByKind() {
        assertEquals(ProjectIcon.Symbol("folder"), ServerProjectListing.icon(serverProject("a", "api", icon = "sf:not.a.symbol")))
    }

    @Test
    fun withoutAnIconHomeWorktreesAndFoldersHaveTheirOwnSymbol() {
        assertEquals(ProjectIcon.Symbol("house"), ServerProjectListing.icon(serverProject("h", "Home", isHome = true)))
        assertEquals(ProjectIcon.Symbol("arrow.triangle.branch"), ServerProjectListing.icon(serverProject("w", "wt", isWorktree = true)))
        assertEquals(ProjectIcon.Symbol("folder"), ServerProjectListing.icon(serverProject("a", "api", icon = "  ")))
    }

    @Test
    fun anItemCarriesTheLogoColorPathAndNesting() {
        val logo = byteArrayOf(1, 2, 3)
        val item = ServerProjectListing.item(ServerProjectRow(serverProject("a", "api", logo = logo), isNested = true))
        assertEquals("/Users/demo/api", item.path)
        assertEquals("#C370D3", item.iconColor)
        assertEquals(ProjectLogo(byteArrayOf(1, 2, 3)), item.logo)
        assertTrue(item.isNested)
    }

    @Test
    fun connectingAndRestartingShowLoading() {
        assertEquals(ProjectListStatus.Loading, ServerProjectListing.status(ServerPhase.Idle, catalog, "Studio"))
        assertEquals(ProjectListStatus.Loading, ServerProjectListing.status(ServerPhase.Connecting, catalog, "Studio"))
        val restarting = ServerPhase.Reconnecting(ReconnectReason.ServerRestarting)
        assertEquals(ProjectListStatus.Loading, ServerProjectListing.status(restarting, catalog, "Studio"))
    }

    @Test
    fun aLostConnectionExplainsWhy() {
        val lost = ServerPhase.Reconnecting(ReconnectReason.Lost(ServerFailure.Unreachable, attempt = 1))
        val status = ServerProjectListing.status(lost, catalog, "Studio") as ProjectListStatus.Disconnected
        assertTrue(status.message!!.startsWith("Can't reach Studio."))
    }

    @Test
    fun aRevokedPhoneSaysSo() {
        val failed = ServerPhase.Failed(ServerFailure.Unauthorized)
        assertEquals(
            ProjectListStatus.Disconnected("This phone isn't paired with Studio anymore."),
            ServerProjectListing.status(failed, catalog, "Studio"),
        )
    }

    @Test
    fun aConnectedServerShowsItsCatalogState() {
        assertEquals(ProjectListStatus.Loading, ServerProjectListing.status(ServerPhase.Connected, ProjectCatalog(), "Studio"))
        assertEquals(ProjectListStatus.Empty, ServerProjectListing.status(ServerPhase.Connected, catalog, "Studio"))
        val failed = ProjectCatalog(loadFailed = true)
        assertEquals(ProjectListStatus.LoadFailed, ServerProjectListing.status(ServerPhase.Connected, failed, "Studio"))
    }

    @Test
    fun tabsLoadUntilTheSessionsArrive() {
        assertEquals(ProjectTabsStatus.LOADING, ServerProjectListing.tabsStatus(ServerPhase.Connected, hasLoadedSessions = false))
        assertEquals(ProjectTabsStatus.READY, ServerProjectListing.tabsStatus(ServerPhase.Connected, hasLoadedSessions = true))
    }

    @Test
    fun tabsShowDisconnectedOnlyWhenTheConnectionIsLost() {
        val restarting = ServerPhase.Reconnecting(ReconnectReason.ServerRestarting)
        val lost = ServerPhase.Reconnecting(ReconnectReason.Lost(ServerFailure.Disconnected, attempt = 1))
        assertEquals(ProjectTabsStatus.LOADING, ServerProjectListing.tabsStatus(restarting, hasLoadedSessions = true))
        assertEquals(ProjectTabsStatus.DISCONNECTED, ServerProjectListing.tabsStatus(lost, hasLoadedSessions = true))
        assertEquals(
            ProjectTabsStatus.DISCONNECTED,
            ServerProjectListing.tabsStatus(ServerPhase.Failed(ServerFailure.Unauthorized), hasLoadedSessions = true),
        )
    }
}
