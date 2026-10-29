import Foundation
import Testing
@testable import Muxy

struct WorkspaceTabRemovalTests {
    private func tab(_ id: UUID = UUID()) -> Tab {
        Tab(id: id, kind: .terminal, title: "t", isPinned: false, paneID: UUID())
    }

    private func workspace(_ first: TabArea, _ second: TabArea) -> Workspace {
        Workspace(
            projectID: UUID(),
            worktreeID: UUID(),
            focusedAreaID: first.id,
            root: .split(WorkspaceSplit(id: UUID(), direction: .horizontal, ratio: 0.5, first: .tabArea(first), second: .tabArea(second)))
        )
    }

    @Test func removingTheActiveTabActivatesTheNextOne() throws {
        let closing = tab()
        let next = tab()
        let other = TabArea(id: UUID(), projectPath: "/p", tabs: [tab()], activeTabID: nil)
        let owning = TabArea(id: UUID(), projectPath: "/p", tabs: [closing, next], activeTabID: closing.id)

        let updated = try #require(WorkspaceFlattening.removingTab(closing.id, from: workspace(owning, other)))
        let area = try #require(WorkspaceFlattening.tabAreas(in: updated).first { $0.id == owning.id })

        #expect(area.tabs.map(\.id) == [next.id])
        #expect(area.activeTabID == next.id)
        #expect(WorkspaceFlattening.tabAreas(in: updated).first { $0.id == other.id }?.tabs.count == 1)
    }

    @Test func removingAnotherTabKeepsTheActiveOne() throws {
        let active = tab()
        let closing = tab()
        let owning = TabArea(id: UUID(), projectPath: "/p", tabs: [active, closing], activeTabID: active.id)
        let other = TabArea(id: UUID(), projectPath: "/p", tabs: [], activeTabID: nil)

        let updated = try #require(WorkspaceFlattening.removingTab(closing.id, from: workspace(owning, other)))

        #expect(WorkspaceFlattening.tabAreas(in: updated).first?.activeTabID == active.id)
        #expect(WorkspaceFlattening.tabAreas(in: updated).first?.tabs.map(\.id) == [active.id])
    }

    @Test func unknownTabsLeaveNothingToRemove() {
        let owning = TabArea(id: UUID(), projectPath: "/p", tabs: [tab()], activeTabID: nil)
        let other = TabArea(id: UUID(), projectPath: "/p", tabs: [], activeTabID: nil)

        #expect(WorkspaceFlattening.removingTab(UUID(), from: workspace(owning, other)) == nil)
    }
}
