import SwiftUI

struct RootView: View {
    let container: AppContainer

    @AppStorage("muxy.hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.scenePhase) private var scenePhase
    @State private var connectionsViewModel: ConnectionsListViewModel
    @State private var tabsViewModel: TabsHomeViewModel
    @State private var path: [AppRoute] = []
    @State private var addRequest: AddConnectionRequest?
    @State private var editingConnection: Connection?
    @State private var isShowingSettings = false

    init(container: AppContainer) {
        self.container = container
        _connectionsViewModel = State(initialValue: container.makeConnectionsListViewModel())
        _tabsViewModel = State(initialValue: container.makeTabsHomeViewModel())
    }

    var body: some View {
        let theme = AppTheme(isDark: colorScheme == .dark, terminalPalette: container.settings.themePalette)
        themedContent
            .environment(\.appTheme, theme)
            .tint(theme.accent)
            .themedWindow(theme)
            .onOpenURL(perform: open)
            .onChange(of: scenePhase) { _, phase in sceneDidChange(phase) }
    }

    private var themedContent: some View {
        Group {
            if hasCompletedOnboarding {
                if isSplit {
                    SplitRootView(path: $path, sidebar: { home }) { route in
                        destination(for: route)
                            .toolbar(.visible, for: .navigationBar)
                    }
                    .onChange(of: path) { _, newPath in pathDidChange(newPath) }
                } else {
                    NavigationStack(path: $path) {
                        home
                            .navigationDestination(for: AppRoute.self) { route in
                                destination(for: route)
                                    .toolbar(.visible, for: .navigationBar)
                            }
                    }
                    .onChange(of: path) { _, newPath in pathDidChange(newPath) }
                }
            } else {
                OnboardingView(
                    onSkip: completeOnboarding,
                    onPairDesktop: completeOnboardingAndPair
                )
            }
        }
        .onAppear { applyDemoMode() }
        .onChange(of: container.settings.demoMode) { _, _ in applyDemoMode() }
        .sheet(item: $addRequest, onDismiss: reloadConnections) { request in
            AddConnectionView(
                viewModel: container.makeAddConnectionViewModel(),
                pairingCode: request.pairingCode,
                onAdded: didAdd
            )
            .formModalPresentation()
        }
        .sheet(item: $editingConnection, onDismiss: reloadConnections) { connection in
            EditConnectionView(viewModel: container.makeEditConnectionViewModel(for: connection)) {
                editingConnection = nil
                reloadConnections()
            }
            .formModalPresentation()
        }
        .sheet(isPresented: $isShowingSettings) {
            SettingsView(settings: container.settings) {
                isShowingSettings = false
            }
            .formModalPresentation()
        }
    }

    private var home: some View {
        TabsHomeView(
            viewModel: tabsViewModel,
            onOpen: show(_:),
            onOpenDevice: navigate(to:),
            onEdit: { editingConnection = $0 },
            onDelete: { connection in
                connectionsViewModel.delete(connection)
                tabsViewModel.load()
            },
            onAddDevice: { addRequest = AddConnectionRequest() },
            onSettings: { isShowingSettings = true }
        )
    }

    private var isSplit: Bool {
        horizontalSizeClass == .regular
    }

    @ViewBuilder
    private func destination(for route: AppRoute) -> some View {
        switch route {
        case let .projects(connection):
            ProjectsView(
                viewModel: container.makeProjectsViewModel(for: connection),
                onSelect: { project in
                    path.append(AppRoute.projectDetail(connection: connection, project: project))
                }
            )
        case let .projectDetail(connection, project, tabID):
            ProjectDetailView(
                viewModel: container.makeProjectDetailViewModel(for: project, connection: connection, initialTabID: tabID),
                requestedTabID: tabID
            )
        case let .serverProjects(connection):
            ServerProjectsView(
                connection: connection,
                controller: serverController(for: connection),
                onSelect: { projectID in
                    path.append(AppRoute.serverProject(connection: connection, projectID: projectID))
                }
            )
        case let .serverProject(connection, projectID, tabID):
            ServerProjectDetailView(
                connection: connection,
                server: serverController(for: connection),
                projectID: projectID,
                initialTabID: tabID,
                settings: container.settings,
                onOpenProject: { openedProjectID in
                    path = path.opening(.serverProject(connection: connection, projectID: openedProjectID))
                }
            )
        case let .sshTerminal(connection):
            SSHTerminalView(viewModel: container.makeSSHTerminalViewModel(for: connection))
        }
    }

    private func serverController(for connection: Connection) -> ServerController? {
        connection.serverRouteID.flatMap(container.directory.controller(for:))
    }

    private func navigate(to connection: Connection) {
        switch connection.kind {
        case .device:
            show(.projects(connection))
        case .server:
            show(.serverProjects(connection))
        case .ssh:
            show(.sshTerminal(connection))
        }
    }

    private func show(_ route: AppRoute) {
        guard isSplit else {
            path.append(route)
            return
        }
        path = [route]
    }

    private func didAdd(_ connection: Connection) {
        Task {
            await container.connectionManager.disconnect()
            addRequest = nil
            if let serverID = connection.serverRouteID {
                container.directory.credentialDidChange(for: serverID)
            }
            path.removeAll()
            reloadConnections()
        }
    }

    private func pathDidChange(_ newPath: [AppRoute]) {
        container.directory.setActiveServer(newPath.lazy.compactMap(\.serverID).first)
        guard newPath.isEmpty else { return }
        Task { await container.connectionManager.disconnect() }
    }

    private func sceneDidChange(_ phase: ScenePhase) {
        switch phase {
        case .active:
            container.directory.setForeground(true)
        case .background:
            container.directory.setForeground(false)
        case .inactive:
            break
        @unknown default:
            break
        }
    }

    private func open(_ url: URL) {
        guard url.scheme?.lowercased() == "muxy" else { return }
        hasCompletedOnboarding = true
        isShowingSettings = false
        addRequest = AddConnectionRequest(pairingCode: url.absoluteString)
    }

    private func completeOnboarding() {
        hasCompletedOnboarding = true
    }

    private func completeOnboardingAndPair() {
        hasCompletedOnboarding = true
        addRequest = AddConnectionRequest()
    }

    private func applyDemoMode() {
        DemoConnection.apply(enabled: container.settings.demoMode, store: container.connectionStore, keychain: container.keychain)
        reloadConnections()
    }

    private func reloadConnections() {
        connectionsViewModel.load()
        tabsViewModel.load()
    }
}

private struct AddConnectionRequest: Identifiable {
    let id = UUID()
    var pairingCode: String?
}
