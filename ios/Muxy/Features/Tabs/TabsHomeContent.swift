import SwiftUI

struct TabsHomeContent: View {
    let devices: [TabsHomeDevice]
    let isCreating: Bool
    let onOpenTab: (UUID, String, UUID) -> Void
    let onCreateTab: (UUID, String) -> Void
    let onNewTab: () -> Void
    let onAddDevice: () -> Void
    let onSettings: () -> Void
    let onEditDevice: (UUID) -> Void
    let onDeleteDevice: (UUID) -> Void
    let onOpenDevice: (UUID) -> Void
    let onRetryDevice: (UUID) -> Void

    @Environment(\.appTheme) private var theme
    @State private var query = ""
    @State private var collapsedProjects: Set<String> = []
    @State private var revealedDevices: Set<UUID> = []
    @State private var isShowingNotifications = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(filteredDevices) { device in
                    TabsHomeDeviceSection(
                        device: device,
                        isSearching: !query.isEmpty,
                        collapsedProjects: $collapsedProjects,
                        isRevealed: revealedBinding(for: device.id),
                        onOpenTab: { onOpenTab(device.id, $0, $1) },
                        onCreateTab: { onCreateTab(device.id, $0) },
                        onNewTab: onNewTab,
                        onAddDevice: onAddDevice,
                        onEdit: { onEditDevice(device.id) },
                        onDelete: { onDeleteDevice(device.id) },
                        onOpenDevice: { onOpenDevice(device.id) },
                        onRetry: { onRetryDevice(device.id) }
                    )
                }
            }
            .padding(.bottom, 32)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(theme.groupedBackground.ignoresSafeArea())
        .navigationTitle("Tabs")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarVisibility(.hidden, for: .navigationBar)
        .searchable(text: $query, prompt: "Search tabs and projects")
        .searchToolbarBehavior(.minimize)
        .toolbar { bottomBar }
        .overlay { overlay }
        .disabled(isCreating)
        .sheet(isPresented: $isShowingNotifications) {
            TabsHomeNotificationsSheet(items: attention) { item in
                isShowingNotifications = false
                onOpenTab(item.deviceID, item.projectID, item.tab.id)
            }
        }
    }

    @ToolbarContentBuilder
    private var bottomBar: some ToolbarContent {
        ToolbarItemGroup(placement: .bottomBar) {
            Button("Settings", systemImage: "gearshape", action: onSettings)
            Button("Add device", systemImage: "plus", action: onAddDevice)
        }
        ToolbarSpacer(.flexible, placement: .bottomBar)
        DefaultToolbarItem(kind: .search, placement: .bottomBar)
        ToolbarSpacer(.fixed, placement: .bottomBar)
        ToolbarItem(placement: .bottomBar) {
            Button("New tab", systemImage: "square.and.pencil", action: onNewTab)
                .disabled(devices.isEmpty)
        }
        ToolbarItem(placement: .bottomBar) {
            Button {
                isShowingNotifications = true
            } label: {
                Image(systemName: attention.isEmpty ? "bell" : "bell.badge")
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(attention.isEmpty ? Color.primary : Color.orange, Color.primary)
            }
            .accessibilityLabel("Notifications")
            .accessibilityValue(attention.isEmpty ? "None" : "\(attention.count)")
        }
    }

    @ViewBuilder
    private var overlay: some View {
        if devices.isEmpty {
            ContentUnavailableView {
                Label("No devices", systemImage: "desktopcomputer")
            } description: {
                Text("Add a device to see its projects and open tabs here.")
            } actions: {
                Button("Add device", systemImage: "plus", action: onAddDevice)
                    .buttonStyle(.glass)
            }
        } else if !query.isEmpty, filteredDevices.isEmpty {
            ContentUnavailableView.search(text: query)
        }
        if isCreating {
            ProgressView("Creating tab…")
                .padding(24)
                .glassEffect(.regular, in: .rect(cornerRadius: 20))
        }
    }

    private var attention: [TabsHomeAttention] {
        TabsHomeAttention.collect(from: devices)
    }

    private var filteredDevices: [TabsHomeDevice] {
        devices.compactMap { $0.matching(query) }
    }

    private func revealedBinding(for id: UUID) -> Binding<Bool> {
        Binding(
            get: { revealedDevices.contains(id) },
            set: { revealed in
                if revealed {
                    revealedDevices.insert(id)
                } else {
                    revealedDevices.remove(id)
                }
            }
        )
    }
}
