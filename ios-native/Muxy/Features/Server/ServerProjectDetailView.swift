import MuxyMobile
import SwiftUI

struct ServerProjectDetailView: View {
    let connection: Connection
    let server: ServerController?
    let projectID: String
    let settings: AppSettings

    var body: some View {
        if let server {
            ServerProjectTabsView(
                connection: connection,
                server: server,
                model: server.projectModel(for: projectID),
                settings: settings
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

private struct ServerProjectTabsView: View {
    let connection: Connection
    let server: ServerController
    let model: ProjectModel
    let settings: AppSettings

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
}
