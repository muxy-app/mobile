import SwiftUI

nonisolated enum ProjectTabsStatus: Equatable, Sendable {
    case loading
    case ready
    case disconnected
}

struct ProjectTabsScreen<Item: TabStripItem, Page: View>: View {
    let title: String
    let connectionName: String
    let tabs: [Item]
    let selectedTabID: Item.ID?
    let status: ProjectTabsStatus
    let onSelect: (Item) -> Void
    let onClose: (Item) -> Void
    let onCreate: () -> Void
    @ViewBuilder let page: (Item) -> Page

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            if !tabs.isEmpty {
                TabStripView(
                    tabs: tabs,
                    selectedTabID: selectedTabID,
                    onSelect: onSelect,
                    onClose: onClose,
                    onCreate: onCreate
                )
            }

            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background)
        .ignoresSafeArea(.keyboard)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(theme.foreground)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if tabs.isEmpty {
            emptyState
        } else {
            tabContent
        }
    }

    private var tabContent: some View {
        TabView(selection: selectionBinding) {
            ForEach(tabs) { tab in
                page(tab)
                    .tag(Optional(tab.id))
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(theme.background.ignoresSafeArea())
    }

    @ViewBuilder
    private var emptyState: some View {
        switch status {
        case .loading:
            loadingState
        case .ready:
            ThemedEmptyState(
                title: "No Tabs",
                systemImage: "macwindow",
                message: "Create a tab to get started."
            ) {
                Button("New Tab", action: onCreate)
                    .buttonStyle(ThemedProminentButtonStyle())
            }
        case .disconnected:
            ThemedEmptyState(
                title: "Not Connected",
                systemImage: "wifi.slash",
                message: "Reconnect to \(connectionName) to see this project."
            )
        }
    }

    private var loadingState: some View {
        ProgressView()
            .tint(theme.accent)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(theme.background)
    }

    private var selectionBinding: Binding<Item.ID?> {
        Binding(
            get: { selectedTabID },
            set: { newValue in
                guard let newValue, let tab = tabs.first(where: { $0.id == newValue }) else { return }
                onSelect(tab)
            }
        )
    }
}
