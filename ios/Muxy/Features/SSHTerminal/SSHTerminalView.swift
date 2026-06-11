import SwiftUI

struct SSHTerminalView: View {
    @Environment(\.appTheme) private var theme
    @State var viewModel: SSHTerminalViewModel

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
        .navigationTitle(viewModel.connectionName)
        .navigationBarTitleDisplayMode(.inline)
        .task { viewModel.start() }
        .onDisappear { viewModel.teardown() }
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
                SSHTerminalTabView(session: viewModel.terminalSession(for: tab))
                    .tag(Optional(tab.id))
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(theme.background.ignoresSafeArea())
    }

    private var emptyState: some View {
        ThemedEmptyState(
            title: "No Tabs",
            systemImage: "terminal",
            message: "Create a tab to open a shell."
        ) {
            Button("New Tab") { viewModel.createTab() }
                .buttonStyle(ThemedProminentButtonStyle())
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
