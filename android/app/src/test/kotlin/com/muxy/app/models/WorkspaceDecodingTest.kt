package com.muxy.app.models

import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WorkspaceDecodingTest {
    @Test
    fun decodesSingleTabArea() {
        val workspace =
            decode(
                """
                {
                  "projectID": "11111111-1111-1111-1111-111111111111",
                  "worktreeID": "22222222-2222-2222-2222-222222222222",
                  "focusedAreaID": "33333333-3333-3333-3333-333333333333",
                  "root": {
                    "type": "tabArea",
                    "tabArea": {
                      "id": "33333333-3333-3333-3333-333333333333",
                      "projectPath": "/tmp/p",
                      "activeTabID": "44444444-4444-4444-4444-444444444444",
                      "tabs": [
                        { "id": "44444444-4444-4444-4444-444444444444", "kind": "terminal", "title": "zsh", "isPinned": false, "paneID": "55555555-5555-5555-5555-555555555555" }
                      ]
                    }
                  }
                }
                """,
            )
        val area = (workspace.root as WorkspaceNode.Area).area
        assertEquals(1, area.tabs.size)
        assertEquals(TabKind.Terminal, area.tabs[0].kind)
        assertEquals(area.tabs[0].id, area.activeTabId)
        assertEquals(area.id, workspace.focusedAreaId)
        assertEquals(UUID.fromString("55555555-5555-5555-5555-555555555555"), area.tabs[0].paneId)
    }

    @Test
    fun decodesSplitWithTwoTabAreas() {
        val workspace =
            decode(
                """
                {
                  "projectID": "11111111-1111-1111-1111-111111111111",
                  "worktreeID": "22222222-2222-2222-2222-222222222222",
                  "root": {
                    "type": "split",
                    "split": {
                      "id": "99999999-9999-9999-9999-999999999999",
                      "direction": "horizontal",
                      "ratio": 0.5,
                      "first": { "type": "tabArea", "tabArea": { "id": "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA", "projectPath": "/a", "tabs": [], "activeTabID": null } },
                      "second": { "type": "tabArea", "tabArea": { "id": "BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB", "projectPath": "/b", "tabs": [], "activeTabID": null } }
                    }
                  }
                }
                """,
            )
        val split = (workspace.root as WorkspaceNode.Split).split
        assertEquals(SplitDirection.HORIZONTAL, split.direction)
        assertEquals(0.5, split.ratio, 0.0)
        assertTrue(split.first is WorkspaceNode.Area)
        assertTrue(split.second is WorkspaceNode.Area)
        assertNull(workspace.focusedAreaId)
    }

    @Test
    fun unknownTabKindDecodesAsUnsupported() {
        val workspace =
            decode(
                """
                {
                  "projectID": "11111111-1111-1111-1111-111111111111",
                  "worktreeID": "22222222-2222-2222-2222-222222222222",
                  "root": {
                    "type": "tabArea",
                    "tabArea": {
                      "id": "33333333-3333-3333-3333-333333333333",
                      "projectPath": "/tmp/p",
                      "tabs": [
                        { "id": "44444444-4444-4444-4444-444444444444", "kind": "extensionWebView", "title": "package.json", "isPinned": false },
                        { "id": "66666666-6666-6666-6666-666666666666", "kind": "vcs", "title": "Source Control", "isPinned": true }
                      ]
                    }
                  }
                }
                """,
            )
        val tabs = (workspace.root as WorkspaceNode.Area).area.tabs
        assertEquals(TabKind.Unsupported("extensionWebView"), tabs[0].kind)
        assertNull(tabs[0].paneId)
        assertEquals(TabKind.Vcs, tabs[1].kind)
        assertTrue(tabs[1].isPinned)
    }

    @Test
    fun unknownNodeTypeFailsTheWholeWorkspace() {
        assertThrows(SerializationException::class.java) {
            decode(
                """
                { "projectID": "11111111-1111-1111-1111-111111111111", "worktreeID": "22222222-2222-2222-2222-222222222222",
                  "root": { "type": "mystery" } }
                """,
            )
        }
    }

    @Test
    fun roundTripsThroughTheWireFormat() {
        val area = TabArea(UUID.randomUUID(), "/p", listOf(Tab(UUID.randomUUID(), TabKind.Unsupported("custom"), "t", false)), null)
        val workspace = Workspace(UUID.randomUUID(), UUID.randomUUID(), area.id, WorkspaceNode.Area(area))
        assertEquals(workspace, decode(ProtocolJson.encodeToString(Workspace.serializer(), workspace)))
    }

    private fun decode(json: String): Workspace = ProtocolJson.decodeFromString(Workspace.serializer(), json)
}
