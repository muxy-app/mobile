import Foundation
import MuxyMobile

nonisolated struct ProjectRow: Identifiable, Hashable, Sendable {
    let project: ServerProject
    let isNested: Bool

    var id: String { project.id }
}

nonisolated enum ProjectTree {
    static func rows(from projects: [ServerProject]) -> [ProjectRow] {
        let parents = Set(projects.filter { !$0.isHome && $0.parentId == nil }.map(\.id))
        let children = Dictionary(grouping: projects.filter { isChild($0, of: parents) }) { $0.parentId ?? "" }
        let home = projects.filter(\.isHome).map { ProjectRow(project: $0, isNested: false) }
        let topLevel = sortedByName(projects.filter { !$0.isHome && !isChild($0, of: parents) })

        return home + topLevel.flatMap { project in
            [ProjectRow(project: project, isNested: false)]
                + sortedByName(children[project.id] ?? []).map { ProjectRow(project: $0, isNested: true) }
        }
    }

    private static func isChild(_ project: ServerProject, of parents: Set<String>) -> Bool {
        guard !project.isHome, let parentId = project.parentId else { return false }
        return parents.contains(parentId)
    }

    private static func sortedByName(_ projects: [ServerProject]) -> [ServerProject] {
        projects.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }
}
