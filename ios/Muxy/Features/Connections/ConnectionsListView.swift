import SwiftUI

struct ConnectionsListView: View {
    let viewModel: ConnectionsListViewModel
    let onSelect: (Connection) -> Void
    let onEdit: (Connection) -> Void
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
        .background(theme.groupedBackground)
        .screenTitle("Connections")
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
        ThemedList {
            ForEach(viewModel.connections) { connection in
                Button {
                    onSelect(connection)
                } label: {
                    ConnectionRowView(connection: connection)
                }
                .buttonStyle(.plain)
                .swipeActions(edge: .leading, allowsFullSwipe: false) {
                    if connection.id != DemoConnection.id {
                        Button {
                            onEdit(connection)
                        } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                        .tint(theme.secondaryForeground)
                    }
                }
                .contextMenu {
                    if connection.id != DemoConnection.id {
                        Button {
                            onEdit(connection)
                        } label: {
                            Label("Edit Connection", systemImage: "pencil")
                        }
                    }
                }
            }
            .onDelete { viewModel.delete(at: $0) }
        }
    }
}
