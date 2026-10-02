import SwiftUI

struct ServerProjectsView: View {
    let connection: Connection
    let controller: ServerController?
    let onSelect: (String) -> Void

    var body: some View {
        ProjectListScreen(
            title: connection.name,
            connectionName: connection.name,
            items: items,
            hasProjects: !(controller?.projects.isEmpty ?? true),
            workspaces: [],
            selectedWorkspaceID: .constant(nil),
            status: status,
            onSelect: { onSelect($0.id) },
            onRetry: retry
        )
    }

    private var items: [ProjectListItem] {
        controller?.projectRows.map(ServerProjectListing.item(for:)) ?? []
    }

    private var status: ProjectListStatus {
        guard let controller else { return .disconnected(message: ServerProjectListing.missingPairingMessage) }
        return ServerProjectListing.status(
            phase: controller.phase,
            hasLoadedProjects: controller.hasLoadedProjects,
            projectsLoadFailed: controller.projectsLoadFailed,
            serverName: connection.name
        )
    }

    private func retry() {
        guard let controller else { return }
        guard controller.phase == .connected else {
            controller.retryNow()
            return
        }
        controller.reloadProjects()
    }
}
