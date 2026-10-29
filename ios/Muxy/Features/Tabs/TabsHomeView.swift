import SwiftUI

struct TabsHomeView: View {
    let viewModel: TabsHomeViewModel
    let onOpen: (AppRoute) -> Void
    let onOpenDevice: (Connection) -> Void
    let onEdit: (Connection) -> Void
    let onDelete: (Connection) -> Void
    let onAddDevice: () -> Void
    let onSettings: () -> Void

    @Environment(\.scenePhase) private var scenePhase
    @State private var isChoosingProject = false
    @State private var pendingRoute: AppRoute?
    @State private var pendingAddDevice = false
    @State private var removingDevice: Connection?

    var body: some View {
        TabsHomeContent(
            devices: viewModel.devices,
            isCreating: viewModel.isCreating,
            onOpenTab: openTab,
            onCreateTab: createTab,
            onNewTab: { isChoosingProject = true },
            onAddDevice: onAddDevice,
            onSettings: onSettings,
            onEditDevice: { id in viewModel.connection(id).map(onEdit) },
            onDeleteDevice: { removingDevice = viewModel.connection($0) },
            onOpenDevice: { id in viewModel.connection(id).map(onOpenDevice) },
            onRetryDevice: viewModel.retry
        )
        .sheet(isPresented: $isChoosingProject, onDismiss: openPendingDestination) {
            NavigationStack {
                NewTabPickerContent(
                    devices: viewModel.devices,
                    isCreating: viewModel.isCreating,
                    onCreate: createTab,
                    onOpenSSH: { id in
                        guard let connection = viewModel.connection(id) else { return }
                        pendingRoute = .sshTerminal(connection)
                        isChoosingProject = false
                    },
                    onRetry: viewModel.retry,
                    onAddDevice: {
                        pendingAddDevice = true
                        isChoosingProject = false
                    }
                )
                .screenTitle("New tab")
                .toolbar {
                    DoneToolbarItem(isDisabled: viewModel.isCreating) { isChoosingProject = false }
                }
            }
            .interactiveDismissDisabled(viewModel.isCreating)
        }
        .confirmationDialog(
            "Remove \(removingDevice?.name ?? "device")?",
            isPresented: Binding(get: { removingDevice != nil }, set: { if !$0 { removingDevice = nil } }),
            titleVisibility: .visible,
            presenting: removingDevice
        ) { connection in
            Button("Remove device", role: .destructive) { onDelete(connection) }
        } message: { _ in
            Text("This removes the saved connection from this app. It doesn't close tabs on the device.")
        }
        .alert("Tab unavailable", isPresented: Binding(get: { viewModel.notice != nil }, set: { if !$0 { viewModel.notice = nil } })) {
            Button("OK") { viewModel.notice = nil }
        } message: {
            Text(viewModel.notice ?? "")
        }
        .onAppear {
            viewModel.setForeground(scenePhase != .background)
            viewModel.setVisible(true)
        }
        .onDisappear { viewModel.setVisible(false) }
        .onChange(of: scenePhase) { _, phase in
            guard phase != .inactive else { return }
            viewModel.setForeground(phase == .active)
        }
    }

    private func openTab(deviceID: UUID, projectID: String, tabID: UUID) {
        guard let route = viewModel.route(deviceID: deviceID, projectID: projectID, tabID: tabID) else { return }
        onOpen(route)
    }

    private func createTab(deviceID: UUID, projectID: String) {
        Task {
            guard let route = await viewModel.createTab(deviceID: deviceID, projectID: projectID) else { return }
            if isChoosingProject {
                pendingRoute = route
                isChoosingProject = false
                return
            }
            onOpen(route)
        }
    }

    private func openPendingDestination() {
        if let route = pendingRoute {
            pendingRoute = nil
            onOpen(route)
        }
        guard pendingAddDevice else { return }
        pendingAddDevice = false
        onAddDevice()
    }
}
