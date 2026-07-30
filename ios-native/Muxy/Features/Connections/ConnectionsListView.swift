import SwiftUI

struct ConnectionsListView: View {
    let viewModel: ConnectionsListViewModel
    let onSelect: (Connection) -> Void
    let onAddConnection: () -> Void
    let onSettings: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Group {
            if viewModel.connections.isEmpty {
                ConnectionsEmptyStateView(onAddConnection: onAddConnection)
            } else {
                connectionList
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background)
        .navigationTitle("Connections")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onSettings) {
                    Label("Settings", systemImage: "gearshape")
                }
                .tint(theme.foreground)
            }

            ToolbarItem(placement: .primaryAction) {
                Button(action: onAddConnection) {
                    Label("Add Connection", systemImage: "plus")
                }
                .tint(theme.foreground)
            }
        }
        .onAppear { viewModel.load() }
    }

    private var connectionList: some View {
        List {
            ForEach(viewModel.connections) { connection in
                Button {
                    onSelect(connection)
                } label: {
                    ConnectionRowView(connection: connection)
                }
                .buttonStyle(.plain)
            }
            .onDelete { viewModel.delete(at: $0) }
        }
        .themedSurface()
    }
}
