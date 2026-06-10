import SwiftUI

struct DevicesEmptyStateView: View {
    let onAddDevice: () -> Void

    var body: some View {
        ThemedEmptyState(
            title: "No Devices",
            systemImage: "desktopcomputer",
            message: "Add a Mac running Muxy to control it from here."
        ) {
            Button(action: onAddDevice) {
                Text("Add Device")
            }
            .buttonStyle(ThemedProminentButtonStyle())
        }
    }
}
