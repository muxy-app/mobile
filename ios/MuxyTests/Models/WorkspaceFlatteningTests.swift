import Foundation
import Testing
@testable import Muxy

struct WorkspaceFlatteningTests {
    private func area(_ id: UUID, path: String = "/p") -> TabArea {
        TabArea(id: id, projectPath: path, tabs: [], activeTabID: nil)
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
}
