import SwiftUI

struct ProjectsView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State var viewModel: ProjectsViewModel
    let onSelect: (Project) -> Void

    var body: some View {
        content
            .navigationTitle(viewModel.device.name)
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.connect() }
            .onChange(of: scenePhase) { _, phase in
                guard phase == .active else { return }
                Task { await viewModel.reconnect() }
            }
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
                ProjectRowView(project: project, logoData: viewModel.logoData(for: project))
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        switch viewModel.state {
        case .connecting, .authenticating:
            ProgressView()
        case .connected where viewModel.loadFailed:
            ContentUnavailableView {
                Label("Couldn't Load Projects", systemImage: "exclamationmark.triangle")
            } description: {
                Text("Something went wrong loading projects from \(viewModel.device.name).")
            } actions: {
                Button("Retry") {
                    Task { await viewModel.reconnect() }
                }
            }
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
            } actions: {
                Button("Retry") {
                    Task { await viewModel.reconnect() }
                }
            }
        }
    }
}
