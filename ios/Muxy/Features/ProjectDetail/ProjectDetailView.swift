import SwiftUI

struct ProjectDetailView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.appTheme) private var theme
    @State var viewModel: ProjectDetailViewModel
    @State private var isGitPresented = false

    var body: some View {
        VStack(spacing: 0) {
            if !viewModel.tabs.isEmpty {
                TabStripView(
                    tabs: viewModel.tabs,
                    selectedTabID: viewModel.selectedTabID,
                    onSelect: { viewModel.select($0) },
                    onClose: { viewModel.closeTab($0) },
                    onCreate: { viewModel.createTab() }
                )
            }

            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background)
        .navigationTitle(viewModel.projectName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(viewModel.projectName)
                    .font(.headline)
                    .foregroundStyle(theme.foreground)
            }
            ToolbarItem(placement: .primaryAction) {
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
        .task { await viewModel.connect() }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await viewModel.reconnect() }
        }
        .onDisappear {
            Task { await viewModel.disconnect() }
        }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.tabs.isEmpty {
            emptyState
        } else {
            tabContent
        }
    }

    private var tabContent: some View {
        TabView(selection: selectionBinding) {
            ForEach(viewModel.tabs) { tab in
                tabView(for: tab)
                    .tag(Optional(tab.id))
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(theme.background.ignoresSafeArea())
    }

    @ViewBuilder
    private func tabView(for tab: Tab) -> some View {
        if tab.kind == .terminal, let session = viewModel.terminalSession(for: tab) {
            TerminalTabView(session: session)
        } else {
            UnsupportedTabView(title: tab.title)
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        switch viewModel.state {
        case .connecting, .authenticating:
            loadingState
        case .connected where viewModel.hasLoaded:
            ThemedEmptyState(
                title: "No Tabs",
                systemImage: "macwindow",
                message: "Create a tab to get started."
            ) {
                Button("New Tab") { viewModel.createTab() }
                    .buttonStyle(ThemedProminentButtonStyle())
            }
        case .connected:
            loadingState
        default:
            ThemedEmptyState(
                title: "Not Connected",
                systemImage: "wifi.slash",
                message: "Reconnect to \(viewModel.device.name) to see this project."
            )
        }
    }

    private var loadingState: some View {
        ProgressView()
            .tint(theme.accent)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(theme.background)
    }

    private var selectionBinding: Binding<UUID?> {
        Binding(
            get: { viewModel.selectedTabID },
            set: { newValue in
                guard let newValue, let tab = viewModel.tabs.first(where: { $0.id == newValue }) else { return }
                viewModel.select(tab)
            }
        )
    }
}
