import SwiftUI

struct DevicesListView: View {
    let viewModel: DevicesListViewModel
    let onSelect: (Device) -> Void
    let onAddDevice: () -> Void
    let onSettings: () -> Void

    var body: some View {
        Group {
            if viewModel.devices.isEmpty {
                DevicesEmptyStateView(onAddDevice: onAddDevice)
            } else {
                deviceList
            }
        }
        .navigationTitle("Devices")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onSettings) {
                    Label("Settings", systemImage: "gearshape")
                }
            }

            ToolbarItem(placement: .primaryAction) {
                Button(action: onAddDevice) {
                    Label("Add Device", systemImage: "plus")
                }
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
    }
}
