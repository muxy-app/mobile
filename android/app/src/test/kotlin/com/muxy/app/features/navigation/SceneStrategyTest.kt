package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.muxy.app.design.components.SidebarState
import com.muxy.app.design.components.WindowWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SceneStrategyTest {
    private val connection = UUID.randomUUID()
    private val home = entry(AppRoute.Connections, SidebarSceneStrategy.sidebar())
    private val projects = entry(AppRoute.Projects(connection), SidebarSceneStrategy.pane())
    private val terminal = entry(AppRoute.SshTerminal(connection), SidebarSceneStrategy.pane())
    private val settings = entry(AppRoute.Settings, ModalSceneStrategy.form())

    private fun entry(
        route: AppRoute,
        metadata: Map<String, Any>,
    ): NavEntry<AppRoute> = NavEntry(route, route.toString(), metadata) {}

    private fun SceneStrategy<AppRoute>.sceneFor(entries: List<NavEntry<AppRoute>>): Scene<AppRoute>? =
        SceneStrategyScope<AppRoute>().calculateScene(entries)

    private fun sidebar(width: WindowWidth) = SidebarSceneStrategy(width, SidebarState(isVisible = true))

    @Test
    fun compactWindowsKeepSinglePaneNavigation() {
        assertNull(sidebar(WindowWidth.COMPACT).sceneFor(listOf(home, projects)))
        assertNull(ModalSceneStrategy(WindowWidth.COMPACT).sceneFor(listOf(home, settings)))
    }

    @Test
    fun wideWindowsShowHomeBesideTheTopPane() {
        val scene = sidebar(WindowWidth.EXPANDED).sceneFor(listOf(home, projects, terminal))
        assertEquals(listOf(home, terminal), scene?.entries)
        assertEquals(listOf(home, projects), scene?.previousEntries)
    }

    @Test
    fun wideWindowsShowHomeAloneWithAPlaceholderPane() {
        val scene = sidebar(WindowWidth.MEDIUM).sceneFor(listOf(home))
        assertEquals(listOf(home), scene?.entries)
        assertEquals(emptyList<NavEntry<AppRoute>>(), scene?.previousEntries)
    }

    @Test
    fun theSidebarNeedsHomeAtTheBottomAndAPaneOnTop() {
        assertNull(sidebar(WindowWidth.EXPANDED).sceneFor(listOf(projects, terminal)))
        assertNull(sidebar(WindowWidth.EXPANDED).sceneFor(listOf(home, settings)))
    }

    @Test
    fun wideWindowsOverlayModalsOnTheScreenBelow() {
        val scene = ModalSceneStrategy(WindowWidth.MEDIUM).sceneFor(listOf(home, terminal, settings))
        assertTrue(scene is OverlayScene)
        assertEquals(listOf(settings), scene?.entries)
        assertEquals(listOf(home, terminal), (scene as OverlayScene).overlaidEntries)
    }

    @Test
    fun onlyModalEntriesBecomeOverlays() {
        assertNull(ModalSceneStrategy(WindowWidth.EXPANDED).sceneFor(listOf(home, terminal)))
        assertNull(ModalSceneStrategy(WindowWidth.EXPANDED).sceneFor(listOf(settings)))
    }
}
