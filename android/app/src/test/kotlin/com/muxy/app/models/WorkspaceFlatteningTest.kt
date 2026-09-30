package com.muxy.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class WorkspaceFlatteningTest {
    @Test
    fun returnsFocusedAreaWhenMatching() {
        val a = area()
        val b = area()
        assertEquals(b.id, WorkspaceFlattening.focusedTabArea(workspace(b.id, split(a, b)))?.id)
    }

    @Test
    fun returnsFirstAreaWhenFocusedIsMissing() {
        val a = area()
        val b = area()
        assertEquals(a.id, WorkspaceFlattening.focusedTabArea(workspace(null, split(a, b, SplitDirection.VERTICAL)))?.id)
    }

    @Test
    fun returnsFirstAreaWhenFocusedNotFound() {
        val a = area()
        val b = area()
        assertEquals(a.id, WorkspaceFlattening.focusedTabArea(workspace(UUID.randomUUID(), split(a, b)))?.id)
    }

    @Test
    fun traversesNestedSplitsInPreOrder() {
        val a = area()
        val b = area()
        val c = area()
        val root = WorkspaceNode.Split(WorkspaceSplit(UUID.randomUUID(), SplitDirection.VERTICAL, 0.5, WorkspaceNode.Area(a), split(b, c)))
        assertEquals(a.id, WorkspaceFlattening.focusedTabArea(workspace(null, root))?.id)
    }

    @Test
    fun picksDeepFocusedAreaAcrossNestedSplits() {
        val a = area()
        val b = area()
        val c = area()
        val root = WorkspaceNode.Split(WorkspaceSplit(UUID.randomUUID(), SplitDirection.VERTICAL, 0.5, WorkspaceNode.Area(a), split(b, c)))
        assertEquals(c.id, WorkspaceFlattening.focusedTabArea(workspace(c.id, root))?.id)
    }

    @Test
    fun returnsAreaForSingleTabAreaRoot() {
        val a = area()
        assertEquals(a.id, WorkspaceFlattening.focusedTabArea(workspace(null, WorkspaceNode.Area(a)))?.id)
    }

    @Test
    fun flattensTabsAcrossAllSplitsInPreOrder() {
        val tabs = List(3) { tab() }
        val root =
            WorkspaceNode.Split(
                WorkspaceSplit(
                    UUID.randomUUID(),
                    SplitDirection.VERTICAL,
                    0.5,
                    WorkspaceNode.Area(area(tabs[0])),
                    split(area(tabs[1]), area(tabs[2])),
                ),
            )
        val flattened = WorkspaceFlattening.tabAreas(workspace(null, root)).flatMap(TabArea::tabs)
        assertEquals(tabs.map(Tab::id), flattened.map(Tab::id))
    }

    @Test
    fun findsAreaContainingTabAcrossSplits() {
        val target = tab()
        val a = area(tab())
        val b = area(tab(), target)
        assertEquals(b.id, WorkspaceFlattening.areaContaining(target.id, workspace(a.id, split(a, b)))?.id)
    }

    @Test
    fun returnsNothingWhenNoAreaContainsTab() {
        assertNull(WorkspaceFlattening.areaContaining(UUID.randomUUID(), workspace(null, WorkspaceNode.Area(area(tab())))))
    }

    @Test
    fun mapAreasTransformsOnlyMatchingAreaPreservingStructure() {
        val a = area(tab())
        val b = area(tab())
        val replacement = tab()
        val root =
            WorkspaceNode.Split(
                WorkspaceSplit(UUID.randomUUID(), SplitDirection.VERTICAL, 0.25, WorkspaceNode.Area(a), WorkspaceNode.Area(b)),
            )
        val mapped = WorkspaceFlattening.mapAreas(root) { if (it.id == b.id) it.copy(tabs = listOf(replacement)) else it }
        val areas = WorkspaceFlattening.tabAreas(mapped)
        assertEquals(1, areas.first { it.id == a.id }.tabs.size)
        assertEquals(listOf(replacement.id), areas.first { it.id == b.id }.tabs.map(Tab::id))
        val split = (mapped as WorkspaceNode.Split).split
        assertEquals(0.25, split.ratio, 0.0)
        assertEquals(SplitDirection.VERTICAL, split.direction)
    }

    private fun area(vararg tabs: Tab) = TabArea(UUID.randomUUID(), "/p", tabs.toList(), null)

    private fun tab() = Tab(UUID.randomUUID(), TabKind.Terminal, "t", false, UUID.randomUUID())

    private fun split(
        first: TabArea,
        second: TabArea,
        direction: SplitDirection = SplitDirection.HORIZONTAL,
    ) = WorkspaceNode.Split(WorkspaceSplit(UUID.randomUUID(), direction, 0.5, WorkspaceNode.Area(first), WorkspaceNode.Area(second)))

    private fun workspace(
        focused: UUID?,
        root: WorkspaceNode,
    ) = Workspace(UUID.randomUUID(), UUID.randomUUID(), focused, root)
}
