import SwiftUI

struct ConnectionsEmptyStateView: View {
    let onAddConnection: () -> Void

    var body: some View {
        ThemedEmptyState(
            title: "No Connections",
            systemImage: "terminal",
            message: "Add a Mac running Muxy or an SSH server to get started."
        ) {
            Button(action: onAddConnection) {
                Text("Add Connection")
            }
            .buttonStyle(ThemedProminentButtonStyle())
        }
    }
}
