import MuxyMobile
import Testing
@testable import Muxy

struct ProjectTreeTests {
    private func names(_ rows: [ProjectRow]) -> [String] {
        rows.map { ($0.isNested ? "  " : "") + $0.project.name }
    }

    @Test func homeComesFirstThenProjectsByName() {
        let rows = ProjectTree.rows(from: [
            Fixtures.project(id: "w", name: "web"),
            Fixtures.project(id: "h", name: "Home", isHome: true),
            Fixtures.project(id: "a", name: "api"),
            Fixtures.project(id: "m", name: "Muxy"),
        ])
        #expect(names(rows) == ["Home", "api", "Muxy", "web"])
    }

    @Test func worktreesNestUnderTheirParentSortedByName() {
        let rows = ProjectTree.rows(from: [
            Fixtures.project(id: "m", name: "muxy"),
            Fixtures.project(id: "wt2", name: "sdk", parentId: "m", isWorktree: true),
            Fixtures.project(id: "wt1", name: "docs", parentId: "m", isWorktree: true),
            Fixtures.project(id: "z", name: "zeta"),
        ])
        #expect(names(rows) == ["muxy", "  docs", "  sdk", "zeta"])
    }

    @Test func numbersSortNaturally() {
        let rows = ProjectTree.rows(from: [
            Fixtures.project(id: "b", name: "app10"),
            Fixtures.project(id: "a", name: "app2"),
        ])
        #expect(names(rows) == ["app2", "app10"])
    }

    @Test func worktreesWithoutAKnownParentStayVisible() {
        let rows = ProjectTree.rows(from: [
            Fixtures.project(id: "wt", name: "orphan", parentId: "gone", isWorktree: true),
        ])
        #expect(names(rows) == ["orphan"])
    }

    @Test func childrenOfHomeOrOfWorktreesAreNeverHidden() {
        let rows = ProjectTree.rows(from: [
            Fixtures.project(id: "h", name: "Home", isHome: true),
            Fixtures.project(id: "c", name: "under-home", parentId: "h"),
            Fixtures.project(id: "m", name: "muxy"),
            Fixtures.project(id: "wt", name: "wt", parentId: "m", isWorktree: true),
            Fixtures.project(id: "g", name: "grandchild", parentId: "wt", isWorktree: true),
        ])
        #expect(Set(rows.map(\.project.id)) == ["h", "c", "m", "wt", "g"])
        #expect(names(rows) == ["Home", "grandchild", "muxy", "  wt", "under-home"])
    }
}
