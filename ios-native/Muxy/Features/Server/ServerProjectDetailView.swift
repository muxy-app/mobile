import MuxyMobile
import SwiftUI

struct ServerProjectDetailView: View {
    let connection: Connection
    let server: ServerController?
    let projectID: String
    let settings: AppSettings
    let onOpenProject: (String) -> Void

    var body: some View {
        if let server {
            ServerProjectTabsView(
                connection: connection,
                server: server,
                model: server.projectModel(for: projectID),
                settings: settings,
                onOpenProject: onOpenProject
            )
        } else {
            ProjectTabsScreen(
                title: connection.name,
                connectionName: connection.name,
                tabs: [TerminalController](),
                selectedTabID: nil,
                status: .disconnected,
                onSelect: { _ in },
                onClose: { _ in },
                onCreate: {},
                page: { _ in EmptyView() }
            )
        }
    }
}

private enum ServerProjectSheet: Identifiable {
    case files(FileManagerViewModel)
    case git(GitViewModel, ServerWorktreesModel)
    case worktrees(ServerWorktreesModel)

    var id: ProjectTool {
        switch self {
        case .files: .files
        case .git: .git
        case .worktrees: .worktrees
        }
    }
}

private struct ServerProjectTabsView: View {
    let connection: Connection
    let server: ServerController
    let model: ProjectModel
    let settings: AppSettings
    let onOpenProject: (String) -> Void

    @State private var sheet: ServerProjectSheet?
    @State private var pendingProjectID: String?

    var body: some View {
        ProjectTabsScreen(
            title: server.project(for: model.projectID)?.name ?? "Project",
            connectionName: connection.name,
            tabs: model.tabs,
            selectedTabID: model.selectedTabID,
            status: status,
            onSelect: { model.select($0) },
            onClose: { model.close($0) },
            onCreate: { model.createTab() },
            page: { tab in
                TerminalScreenView(controller: tab, settings: settings, isDisconnected: server.isConnectionLost)
            }
        )
        .toolbar {
            ProjectToolbar(onSelect: present)
        }
        .sheet(item: $sheet, onDismiss: openPendingProject) { sheet in
            switch sheet {
            case let .files(files):
                FileSheetView(viewModel: files)
            case let .git(git, worktrees):
                GitSheetView(viewModel: git) {
                    ServerWorktreesView(model: worktrees, onOpen: open)
                }
            case let .worktrees(worktrees):
                ServerWorktreesSheetView(model: worktrees, onOpen: open)
            }
        }
        .onAppear { model.setVisible(true) }
        .onDisappear { model.setVisible(false) }
    }

    private var status: ProjectTabsStatus {
        let isRemoved = server.hasLoadedProjects && server.project(for: model.projectID) == nil
        return ServerProjectListing.tabsStatus(
            phase: server.phase,
            hasLoadedSessions: model.hasLoadedSessions || isRemoved
        )
    }

    private var fileLocation: FileLocation {
        guard let project = server.project(for: model.projectID) else {
            return FileLocation(name: "Project", path: "", icon: .symbol("folder"), host: .computer(server.serverName))
        }
        return FileLocation(serverProject: project, serverName: server.serverName)
    }

    private func present(_ tool: ProjectTool) {
        switch tool {
        case .files:
            sheet = .files(FileManagerViewModel(
                location: fileLocation,
                scope: .project,
                backend: ServerFileBackend(projectID: model.projectID, server: server)
            ))
        case .worktrees:
            sheet = .worktrees(ServerWorktreesModel(projectID: model.projectID, server: server))
        case .git:
            sheet = .git(
                GitViewModel(backend: ServerGitBackend(projectID: model.projectID, server: server)),
                ServerWorktreesModel(projectID: model.projectID, server: server)
            )
        }
    }

    private func open(_ projectID: String) {
        pendingProjectID = projectID
        sheet = nil
    }

    private func openPendingProject() {
        guard let projectID = pendingProjectID else { return }
        pendingProjectID = nil
        onOpenProject(projectID)
    }
}
