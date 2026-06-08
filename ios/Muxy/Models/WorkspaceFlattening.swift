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

    private static func tabAreas(in node: Node) -> [TabArea] {
        switch node {
        case let .tabArea(area):
            return [area]
        case let .split(split):
            return tabAreas(in: split.first) + tabAreas(in: split.second)
        }
    }
}
