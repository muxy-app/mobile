import Foundation

nonisolated enum WorkspaceFlattening {
    static func focusedTabArea(in workspace: Workspace) -> TabArea? {
        let areas = tabAreas(in: workspace.root)

        if let focusedAreaID = workspace.focusedAreaID,
           let focused = areas.first(where: { $0.id == focusedAreaID }) {
            return focused
        }

        return areas.first
    }

    static func tabAreas(in workspace: Workspace) -> [TabArea] {
        tabAreas(in: workspace.root)
    }

    static func area(containing tabID: UUID, in workspace: Workspace) -> TabArea? {
        tabAreas(in: workspace.root).first { $0.tabs.contains { $0.id == tabID } }
    }

    static func removingTab(_ tabID: UUID, from workspace: Workspace) -> Workspace? {
        guard let owning = area(containing: tabID, in: workspace) else { return nil }
        let remaining = owning.tabs.filter { $0.id != tabID }
        let nextActive = owning.activeTabID == tabID ? remaining.first?.id : owning.activeTabID
        let root = mapAreas(in: workspace.root) { area in
            guard area.id == owning.id else { return area }
            return TabArea(id: area.id, projectPath: area.projectPath, tabs: remaining, activeTabID: nextActive)
        }
        return Workspace(
            projectID: workspace.projectID,
            worktreeID: workspace.worktreeID,
            focusedAreaID: workspace.focusedAreaID,
            root: root
        )
    }

    static func mapAreas(in node: Node, _ transform: (TabArea) -> TabArea) -> Node {
        switch node {
        case let .tabArea(area):
            return .tabArea(transform(area))
        case let .split(split):
            return .split(WorkspaceSplit(
                id: split.id,
                direction: split.direction,
                ratio: split.ratio,
                first: mapAreas(in: split.first, transform),
                second: mapAreas(in: split.second, transform)
            ))
        }
    }

    static func tabAreas(in node: Node) -> [TabArea] {
        switch node {
        case let .tabArea(area):
            return [area]
        case let .split(split):
            return tabAreas(in: split.first) + tabAreas(in: split.second)
        }
    }
}
