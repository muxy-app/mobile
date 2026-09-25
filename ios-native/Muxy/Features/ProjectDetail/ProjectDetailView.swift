import SwiftUI

struct ProjectDetailView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.appTheme) private var theme
    @State var viewModel: ProjectDetailViewModel
    @State private var isGitPresented = false
    @State private var isWorktreesPresented = false
    @State private var isFilesPresented = false

    var body: some View {
        ProjectTabsScreen(
            title: viewModel.projectName,
            connectionName: viewModel.connection.name,
            tabs: viewModel.tabs,
            selectedTabID: viewModel.selectedTabID,
            status: status,
            onSelect: { viewModel.select($0) },
            onClose: { viewModel.closeTab($0) },
            onCreate: { viewModel.createTab() },
            page: tabView(for:)
        )
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button {
                    isFilesPresented = true
                } label: {
                    Image(systemName: "folder")
                }
                .tint(theme.foreground)
                .accessibilityLabel("Files")

                Button {
                    isWorktreesPresented = true
                } label: {
                    Image(systemName: "square.3.layers.3d")
                }
                .tint(theme.foreground)
                .accessibilityLabel("Worktrees")

                Button {
                    isGitPresented = true
                } label: {
                    Image(systemName: "arrow.triangle.branch")
                }
                .tint(theme.foreground)
                .accessibilityLabel("Git")
            }
        }
        .sheet(isPresented: $isGitPresented) {
            GitSheetView(viewModel: viewModel.makeGitViewModel())
        }
        .sheet(isPresented: $isWorktreesPresented) {
            WorktreesSheetView(viewModel: viewModel.makeGitViewModel())
        }
        .sheet(isPresented: $isFilesPresented) {
            FileSheetView(viewModel: viewModel.makeFileManagerViewModel())
        }
        .task { await viewModel.connect() }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await viewModel.reconnect() }
        }
        .onDisappear {
            Task { await viewModel.disconnect() }
        }
    }

    private var status: ProjectTabsStatus {
        switch viewModel.state {
        case .connecting, .authenticating:
            return .loading
        case .connected where viewModel.hasLoaded:
            return .ready
        case .connected:
            return .loading
        default:
            return .disconnected
        }
    }

    @ViewBuilder
    private func tabView(for tab: Tab) -> some View {
        if tab.kind == .terminal, let session = viewModel.terminalSession(for: tab) {
            TerminalTabView(session: session)
        } else {
            UnsupportedTabView(title: tab.title)
        }
    }
}

struct WorktreesSheetView: View {
    @State var viewModel: GitViewModel

    var body: some View {
        NavigationStack {
            GitWorktreesView(viewModel: viewModel)
        }
    }
}
