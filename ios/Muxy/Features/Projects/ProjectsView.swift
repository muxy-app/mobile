import SwiftUI

struct ProjectsView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State var viewModel: ProjectsViewModel
    let onSelect: (Project) -> Void

    var body: some View {
        ProjectListScreen(
            title: viewModel.connection.name,
            connectionName: viewModel.connection.name,
            items: viewModel.filteredProjects.map(listItem(for:)),
            hasProjects: !viewModel.projects.isEmpty,
            workspaces: viewModel.workspaces,
            selectedWorkspaceID: $viewModel.selectedWorkspaceID,
            status: status,
            onSelect: select,
            onRetry: { Task { await viewModel.reconnect() } }
        )
        .task { await viewModel.connect() }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await viewModel.reconnect() }
        }
    }

    private var status: ProjectListStatus {
        switch viewModel.state {
        case .connecting, .authenticating:
            return .loading
        case .connected where viewModel.loadFailed:
            return .loadFailed
        case .connected:
            return .empty
        default:
            return .disconnected(message: nil)
        }
    }

    private func listItem(for project: Project) -> ProjectListItem {
        ProjectListItem(
            id: project.id.uuidString,
            name: project.name,
            path: project.path,
            icon: .symbol(project.icon ?? "folder"),
            iconColor: project.iconColor,
            logo: viewModel.logoData(for: project),
            isNested: false
        )
    }

    private func select(_ item: ProjectListItem) {
        guard let project = viewModel.projects.first(where: { $0.id.uuidString == item.id }) else { return }
        onSelect(project)
    }
}
