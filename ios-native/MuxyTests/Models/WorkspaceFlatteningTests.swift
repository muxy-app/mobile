import Foundation
import Testing
@testable import Muxy

struct WorkspaceFlatteningTests {
    private func area(_ id: UUID, path: String = "/p") -> TabArea {
        TabArea(id: id, projectPath: path, tabs: [], activeTabID: nil)
    }

    private func area(_ id: UUID, tabs: [Tab], active: UUID? = nil) -> TabArea {
        TabArea(id: id, projectPath: "/p", tabs: tabs, activeTabID: active)
    }

    private func tab(_ id: UUID) -> Tab {
        Tab(id: id, kind: .terminal, title: "t", isPinned: false, paneID: UUID())
    }

    private func workspace(focused: UUID?, root: Node) -> Workspace {
        Workspace(projectID: UUID(), worktreeID: UUID(), focusedAreaID: focused, root: root)
    }

    @Test func returnsFocusedAreaWhenMatching() {
        let a = area(UUID())
        let b = area(UUID())
        let root = Node.split(WorkspaceSplit(id: UUID(), direction: .horizontal, ratio: 0.5, first: .tabArea(a), second: .tabArea(b)))

        let result = WorkspaceFlattening.focusedTabArea(in: workspace(focused: b.id, root: root))

        #expect(result?.id == b.id)
    }

    @Test func returnsFirstAreaWhenFocusedIsNil() {
        let a = area(UUID())
        let b = area(UUID())
        let root = Node.split(WorkspaceSplit(id: UUID(), direction: .vertical, ratio: 0.5, first: .tabArea(a), second: .tabArea(b)))

        let result = WorkspaceFlattening.focusedTabArea(in: workspace(focused: nil, root: root))

        #expect(result?.id == a.id)
    }

    @Test func returnsFirstAreaWhenFocusedNotFound() {
        let a = area(UUID())
        let b = area(UUID())
        let root = Node.split(WorkspaceSplit(id: UUID(), direction: .horizontal, ratio: 0.5, first: .tabArea(a), second: .tabArea(b)))

        let result = WorkspaceFlattening.focusedTabArea(in: workspace(focused: UUID(), root: root))

        #expect(result?.id == a.id)
    }

    @Test func traversesNestedSplitsInPreOrder() {
        let a = area(UUID())
        let b = area(UUID())
        let c = area(UUID())
        let inner = Node.split(WorkspaceSplit(id: UUID(), direction: .horizontal, ratio: 0.5, first: .tabArea(b), second: .tabArea(c)))
        let root = Node.split(WorkspaceSplit(id: UUID(), direction: .vertical, ratio: 0.5, first: .tabArea(a), second: inner))

        let result = WorkspaceFlattening.focusedTabArea(in: workspace(focused: nil, root: root))

        #expect(result?.id == a.id)
    }

    @Test func picksDeepFocusedAreaAcrossNestedSplits() {
        let a = area(UUID())
        let b = area(UUID())
        let c = area(UUID())
        let inner = Node.split(WorkspaceSplit(id: UUID(), direction: .horizontal, ratio: 0.5, first: .tabArea(b), second: .tabArea(c)))
        let root = Node.split(WorkspaceSplit(id: UUID(), direction: .vertical, ratio: 0.5, first: .tabArea(a), second: inner))

        let result = WorkspaceFlattening.focusedTabArea(in: workspace(focused: c.id, root: root))

        #expect(result?.id == c.id)
    }

    @Test func returnsAreaForSingleTabAreaRoot() {
        let a = area(UUID())
        let result = WorkspaceFlattening.focusedTabArea(in: workspace(focused: nil, root: .tabArea(a)))
        #expect(result?.id == a.id)
    }

    @Test func flattensTabsAcrossAllSplitsInPreOrder() {
        let t1 = tab(UUID())
        let t2 = tab(UUID())
        let t3 = tab(UUID())
        let a = area(UUID(), tabs: [t1])
        let b = area(UUID(), tabs: [t2])
        let c = area(UUID(), tabs: [t3])
        let inner = Node.split(WorkspaceSplit(id: UUID(), direction: .horizontal, ratio: 0.5, first: .tabArea(b), second: .tabArea(c)))
        let root = Node.split(WorkspaceSplit(id: UUID(), direction: .vertical, ratio: 0.5, first: .tabArea(a), second: inner))

        let tabs = WorkspaceFlattening.tabAreas(in: workspace(focused: nil, root: root)).flatMap(\.tabs)

        #expect(tabs.map(\.id) == [t1.id, t2.id, t3.id])
    }

    @Test func findsAreaContainingTabAcrossSplits() {
        let target = tab(UUID())
        let a = area(UUID(), tabs: [tab(UUID())])
        let b = area(UUID(), tabs: [tab(UUID()), target])
        let root = Node.split(WorkspaceSplit(id: UUID(), direction: .horizontal, ratio: 0.5, first: .tabArea(a), second: .tabArea(b)))

        let result = WorkspaceFlattening.area(containing: target.id, in: workspace(focused: a.id, root: root))

        #expect(result?.id == b.id)
    }

    @Test func returnsNilWhenNoAreaContainsTab() {
        let a = area(UUID(), tabs: [tab(UUID())])
        let result = WorkspaceFlattening.area(containing: UUID(), in: workspace(focused: nil, root: .tabArea(a)))
        #expect(result == nil)
    }

    @Test func mapAreasTransformsOnlyMatchingAreaPreservingStructure() {
        let a = area(UUID(), tabs: [tab(UUID())])
        let b = area(UUID(), tabs: [tab(UUID())])
        let root = Node.split(WorkspaceSplit(id: UUID(), direction: .vertical, ratio: 0.25, first: .tabArea(a), second: .tabArea(b)))

        let replacement = tab(UUID())
        let mapped = WorkspaceFlattening.mapAreas(in: root) { current in
            guard current.id == b.id else { return current }
            return area(current.id, tabs: [replacement])
        }

        let areas = WorkspaceFlattening.tabAreas(in: mapped)
        #expect(areas.first(where: { $0.id == a.id })?.tabs.count == 1)
        #expect(areas.first(where: { $0.id == b.id })?.tabs.map(\.id) == [replacement.id])
        guard case let .split(split) = mapped else {
            Issue.record("expected split root to be preserved")
            return
        }
        #expect(split.ratio == 0.25)
        #expect(split.direction == .vertical)
    }
}
