import SwiftUI

struct SplitRootView<Sidebar: View, Destination: View>: View {
    @Binding var path: [AppRoute]
    @ViewBuilder let sidebar: () -> Sidebar
    @ViewBuilder let destination: (AppRoute) -> Destination

    @Environment(\.appTheme) private var theme
    @SceneStorage("muxy.isSidebarHidden") private var isSidebarHidden = false
    @State private var activeTabID: UUID?

    var body: some View {
        NavigationSplitView(columnVisibility: columnVisibility) {
            sidebar()
                .environment(\.activeTabID, path.isEmpty ? nil : activeTabID)
                .environment(\.isSplitSidebar, true)
                .navigationSplitViewColumnWidth(min: 300, ideal: 340, max: 420)
                .toolbar(removing: .sidebarToggle)
        } detail: {
            NavigationStack(path: pushedPath) {
                root
                    .navigationDestination(for: AppRoute.self) { route in
                        destination(route)
                            .toolbar { sidebarToggle }
                    }
            }
            .environment(\.activeTabReporter, activeTabReporter)
        }
        .navigationSplitViewStyle(.balanced)
    }

    @ViewBuilder
    private var root: some View {
        if let route = path.first {
            destination(route)
                .id(route.destination)
                .toolbar { sidebarToggle }
        } else {
            placeholder
                .toolbar { sidebarToggle }
        }
    }

    private var placeholder: some View {
        ThemedEmptyState(
            title: "No Tab Open",
            systemImage: "macwindow",
            message: "Choose a tab from the sidebar, or create a new one."
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .tabPageChrome(theme)
    }

    @ToolbarContentBuilder
    private var sidebarToggle: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button(isSidebarHidden ? "Show sidebar" : "Hide sidebar", systemImage: "sidebar.leading") {
                withAnimation { isSidebarHidden.toggle() }
            }
            .keyboardShortcut("s", modifiers: [.command, .control])
        }
    }

    private var columnVisibility: Binding<NavigationSplitViewVisibility> {
        Binding(
            get: { isSidebarHidden ? .detailOnly : .all },
            set: { isSidebarHidden = $0 == .detailOnly }
        )
    }

    private var pushedPath: Binding<[AppRoute]> {
        Binding(
            get: { Array(path.dropFirst()) },
            set: { path = Array(path.prefix(1)) + $0 }
        )
    }

    private var activeTabReporter: ActiveTabReporter {
        ActiveTabReporter(
            select: { activeTabID = $0 },
            clear: { id in
                guard activeTabID == id else { return }
                activeTabID = nil
            }
        )
    }
}
