import SwiftUI

struct ProjectListScreen: View {
    let title: String
    let connectionName: String
    let items: [ProjectListItem]
    let hasProjects: Bool
    let workspaces: [ProjectWorkspace]
    @Binding var selectedWorkspaceID: UUID?
    let status: ProjectListStatus
    let onSelect: (ProjectListItem) -> Void
    let onRetry: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(theme.background)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private var content: some View {
        if !hasProjects {
            emptyState
        } else {
            VStack(spacing: 0) {
                if workspaces.count > 1 {
                    WorkspaceFilterBar(
                        workspaces: workspaces,
                        selectedWorkspaceID: $selectedWorkspaceID
                    )
                }

                projectList
            }
        }
    }

    @ViewBuilder
    private var projectList: some View {
        if items.isEmpty {
            ThemedEmptyState(
                title: "No Projects",
                systemImage: "folder",
                message: "This workspace has no projects."
            )
        } else {
            List(items) { item in
                Button {
                    onSelect(item)
                } label: {
                    ProjectRowView(item: item)
                }
                .buttonStyle(.plain)
            }
            .themedSurface()
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        switch status {
        case .loading:
            ProgressView()
                .tint(theme.accent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(theme.background)
        case .loadFailed:
            ThemedEmptyState(
                title: "Couldn't Load Projects",
                systemImage: "exclamationmark.triangle",
                message: "Something went wrong loading projects from \(connectionName)."
            ) {
                retryButton
            }
        case .empty:
            ThemedEmptyState(
                title: "No Projects",
                systemImage: "folder",
                message: "Projects on \(connectionName) will appear here."
            )
        case let .disconnected(message):
            ThemedEmptyState(
                title: "Not Connected",
                systemImage: "wifi.slash",
                message: message ?? "Connect to \(connectionName) to see its projects."
            ) {
                retryButton
            }
        }
    }

    private var retryButton: some View {
        Button("Retry", action: onRetry)
            .buttonStyle(ThemedBorderedButtonStyle())
    }
}
