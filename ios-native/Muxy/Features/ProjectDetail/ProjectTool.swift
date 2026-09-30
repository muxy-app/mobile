import SwiftUI

nonisolated enum ProjectTool: String, Identifiable, CaseIterable, Sendable {
    case files
    case worktrees
    case git

    var id: Self { self }

    var title: String {
        switch self {
        case .files: "Files"
        case .worktrees: "Worktrees"
        case .git: "Git"
        }
    }

    var systemImage: String {
        switch self {
        case .files: "folder"
        case .worktrees: "square.3.layers.3d"
        case .git: "arrow.triangle.branch"
        }
    }
}

struct ProjectToolbar: ToolbarContent {
    let onSelect: (ProjectTool) -> Void

    @Environment(\.appTheme) private var theme

    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .primaryAction) {
            ForEach(ProjectTool.allCases) { tool in
                Button {
                    onSelect(tool)
                } label: {
                    Image(systemName: tool.systemImage)
                }
                .tint(theme.foreground)
                .accessibilityLabel(tool.title)
            }
        }
    }
}
