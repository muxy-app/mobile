import SwiftUI

struct ProjectDetailView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State var viewModel: ProjectDetailViewModel
    @State private var tool: ProjectTool?

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
            ProjectToolbar { tool = $0 }
        }
        .sheet(item: $tool) { tool in
            switch tool {
            case .files:
                FileSheetView(viewModel: viewModel.makeFileManagerViewModel())
            case .worktrees:
                WorktreesSheetView(viewModel: viewModel.makeWorktreesViewModel())
            case .git:
                GitSheetView(viewModel: viewModel.makeGitViewModel()) {
                    GitWorktreesView(viewModel: viewModel.makeWorktreesViewModel())
                }
            }
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
