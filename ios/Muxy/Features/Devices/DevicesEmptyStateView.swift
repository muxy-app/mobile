import SwiftUI

struct DevicesEmptyStateView: View {
    let onAddDevice: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("No Devices", systemImage: "desktopcomputer")
        } description: {
            Text("Add a Mac running Muxy to control it from here.")
        } actions: {
            Button(action: onAddDevice) {
                Text("Add Device")
            }
            .buttonStyle(.borderedProminent)
        }
    }
}
