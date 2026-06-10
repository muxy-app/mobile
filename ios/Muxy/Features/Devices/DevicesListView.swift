import SwiftUI

struct DevicesListView: View {
    let viewModel: DevicesListViewModel
    let onSelect: (Device) -> Void
    let onAddDevice: () -> Void
    let onSettings: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Group {
            if viewModel.devices.isEmpty {
                DevicesEmptyStateView(onAddDevice: onAddDevice)
            } else {
                deviceList
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background)
        .navigationTitle("Devices")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onSettings) {
                    Label("Settings", systemImage: "gearshape")
                }
                .tint(theme.foreground)
            }

            ToolbarItem(placement: .primaryAction) {
                Button(action: onAddDevice) {
                    Label("Add Device", systemImage: "plus")
                }
                .tint(theme.foreground)
            }
        }
        .onAppear { viewModel.load() }
    }

    private var deviceList: some View {
        List {
            ForEach(viewModel.devices) { device in
                Button {
                    onSelect(device)
                } label: {
                    DeviceRowView(device: device)
                }
                .buttonStyle(.plain)
            }
            .onDelete { viewModel.delete(at: $0) }
        }
        .themedSurface()
    }
}
