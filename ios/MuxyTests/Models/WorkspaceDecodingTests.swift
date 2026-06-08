import Foundation
import Testing
@testable import Muxy

struct WorkspaceDecodingTests {
    private func decode(_ json: String) throws -> Workspace {
        try JSONDecoder().decode(Workspace.self, from: Data(json.utf8))
    }

    @Test func decodesSingleTabArea() throws {
        let json = """
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
        """

        let workspace = try decode(json)

        guard case let .tabArea(area) = workspace.root else {
            Issue.record("Expected tabArea root")
            return
        }
        #expect(area.tabs.count == 1)
        #expect(area.tabs[0].kind == .terminal)
        #expect(area.activeTabID == area.tabs[0].id)
        #expect(workspace.focusedAreaID == area.id)
    }

    @Test func decodesSplitWithTwoTabAreas() throws {
        let json = """
        {
          "projectID": "11111111-1111-1111-1111-111111111111",
          "worktreeID": "22222222-2222-2222-2222-222222222222",
          "root": {
            "type": "split",
            "split": {
              "id": "99999999-9999-9999-9999-999999999999",
              "direction": "horizontal",
              "ratio": 0.5,
              "first": {
                "type": "tabArea",
                "tabArea": { "id": "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA", "projectPath": "/a", "tabs": [], "activeTabID": null }
              },
              "second": {
                "type": "tabArea",
                "tabArea": { "id": "BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB", "projectPath": "/b", "tabs": [], "activeTabID": null }
              }
            }
          }
        }
        """

        let workspace = try decode(json)

        guard case let .split(split) = workspace.root else {
            Issue.record("Expected split root")
            return
        }
        #expect(split.direction == .horizontal)
        #expect(split.ratio == 0.5)
        guard case .tabArea = split.first, case .tabArea = split.second else {
            Issue.record("Expected tabArea children")
            return
        }
        #expect(workspace.focusedAreaID == nil)
    }

    @Test func unknownTabKindDecodesAsUnsupported() throws {
        let json = """
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
        """

        let workspace = try decode(json)

        guard case let .tabArea(area) = workspace.root else {
            Issue.record("Expected tabArea root")
            return
        }
        #expect(area.tabs[0].kind == .unsupported(raw: "extensionWebView"))
        #expect(area.tabs[0].paneID == nil)
        #expect(area.tabs[1].kind == .vcs)
        #expect(area.tabs[1].isPinned)
    }

    @Test func unknownNodeTypeThrows() {
        let json = """
        {
          "projectID": "11111111-1111-1111-1111-111111111111",
          "worktreeID": "22222222-2222-2222-2222-222222222222",
          "root": { "type": "mystery" }
        }
        """

        #expect(throws: (any Error).self) {
            try decode(json)
        }
    }
}
