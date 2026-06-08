import SwiftUI

struct ProjectsView: View {
    @State var viewModel: ProjectsViewModel
    let onSelect: (Project) -> Void

    var body: some View {
        content
            .safeAreaInset(edge: .bottom) {
                ConnectionStatusBar(state: viewModel.state) {
                    Task { await viewModel.reconnect() }
                }
            }
            .navigationTitle(viewModel.device.name)
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.connect() }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.projects.isEmpty {
            emptyState
        } else {
            projectList
        }
    }

    private var projectList: some View {
        List(viewModel.projects) { project in
            Button {
                onSelect(project)
            } label: {
                ProjectRowView(project: project)
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        switch viewModel.state {
        case .connecting, .authenticating:
            ProgressView()
        case .connected:
            ContentUnavailableView {
                Label("No Projects", systemImage: "folder")
            } description: {
                Text("Projects on \(viewModel.device.name) will appear here.")
            }
        default:
            ContentUnavailableView {
                Label("Not Connected", systemImage: "wifi.slash")
            } description: {
                Text("Connect to \(viewModel.device.name) to see its projects.")
            }
        }
    }
}

private struct ConnectionStatusBar: View {
    let state: ConnectionState
    let onReconnect: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            indicator
            Text(label)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Spacer()
            if canReconnect {
                Button("Reconnect", action: onReconnect)
                    .font(.subheadline)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
        .background(.bar)
    }

    @ViewBuilder
    private var indicator: some View {
        switch state {
        case .connecting, .authenticating:
            ProgressView()
                .controlSize(.small)
        case .connected:
            Circle().fill(.green).frame(width: 10, height: 10)
        case .idle, .disconnected:
            Circle().fill(.gray).frame(width: 10, height: 10)
        case .failed:
            Circle().fill(.red).frame(width: 10, height: 10)
        }
    }

    private var label: String {
        switch state {
        case .idle:
            return "Idle"
        case .connecting:
            return "Connecting…"
        case .authenticating:
            return "Authenticating…"
        case .connected:
            return "Connected"
        case .disconnected:
            return "Disconnected"
        case let .failed(error):
            return message(for: error)
        }
    }

    private var canReconnect: Bool {
        switch state {
        case .disconnected, .failed:
            return true
        default:
            return false
        }
    }

    private func message(for error: ConnectionError) -> String {
        switch error {
        case .connectionFailed:
            return "Connection failed"
        case .authenticationFailed:
            return "Authentication failed"
        case .invalidEndpoint:
            return "Invalid address"
        case .missingToken:
            return "Missing credentials"
        case .notConnected:
            return "Not connected"
        }
    }
}
