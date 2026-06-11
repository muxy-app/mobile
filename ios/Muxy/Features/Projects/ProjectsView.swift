import SwiftUI

struct ProjectsView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.appTheme) private var theme
    @State var viewModel: ProjectsViewModel
    let onSelect: (Project) -> Void

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(theme.background)
            .navigationTitle(viewModel.connection.name)
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
        .themedSurface()
    }

    @ViewBuilder
    private var emptyState: some View {
        switch viewModel.state {
        case .connecting, .authenticating:
            ProgressView()
                .tint(theme.accent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(theme.background)
        case .connected where viewModel.loadFailed:
            ThemedEmptyState(
                title: "Couldn't Load Projects",
                systemImage: "exclamationmark.triangle",
                message: "Something went wrong loading projects from \(viewModel.connection.name)."
            ) {
                retryButton
            }
        case .connected:
            ThemedEmptyState(
                title: "No Projects",
                systemImage: "folder",
                message: "Projects on \(viewModel.connection.name) will appear here."
            )
        default:
            ThemedEmptyState(
                title: "Not Connected",
                systemImage: "wifi.slash",
                message: "Connect to \(viewModel.connection.name) to see its projects."
            ) {
                retryButton
            }
        }
    }

    private var retryButton: some View {
        Button("Retry") {
            Task { await viewModel.reconnect() }
        }
        .buttonStyle(ThemedBorderedButtonStyle())
    }
}
