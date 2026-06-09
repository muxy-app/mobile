import SwiftUI

struct ProjectDetailView: View {
    @Environment(\.scenePhase) private var scenePhase
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
        .navigationTitle(viewModel.projectName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(viewModel.projectName)
                    .font(.headline)
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    isGitPresented = true
                } label: {
                    Image(systemName: "arrow.triangle.branch")
                }
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
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .connected where viewModel.hasLoaded:
            ContentUnavailableView {
                Label("No Tabs", systemImage: "macwindow")
            } description: {
                Text("Create a tab to get started.")
            } actions: {
                Button("New Tab") { viewModel.createTab() }
            }
        case .connected:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        default:
            ContentUnavailableView {
                Label("Not Connected", systemImage: "wifi.slash")
            } description: {
                Text("Reconnect to \(viewModel.device.name) to see this project.")
            }
        }
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
