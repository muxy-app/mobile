import SwiftUI

struct RootView: View {
    let container: AppContainer

    @AppStorage("muxy.hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var connectionsViewModel: ConnectionsListViewModel
    @State private var path: [AppRoute] = []
    @State private var addRequest: AddConnectionRequest?
    @State private var isShowingSettings = false

    init(container: AppContainer) {
        self.container = container
        _connectionsViewModel = State(initialValue: container.makeConnectionsListViewModel())
    }

    private var theme: AppTheme { AppTheme(palette: container.settings.themePalette) }

    var body: some View {
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
                NavigationStack(path: $path) {
                    ConnectionsListView(
                        viewModel: connectionsViewModel,
                        onSelect: navigate(to:),
                        onAddConnection: { addRequest = AddConnectionRequest() },
                        onSettings: { isShowingSettings = true }
                    )
                    .navigationDestination(for: AppRoute.self) { route in
                        destination(for: route)
                    }
                }
                .onChange(of: path) { _, newPath in pathDidChange(newPath) }
            } else {
                OnboardingView(
                    onSkip: completeOnboarding,
                    onPairDesktop: completeOnboardingAndPair
                )
            }
        }
        .onAppear { applyDemoMode() }
        .onChange(of: container.settings.demoMode) { _, _ in applyDemoMode() }
        .sheet(item: $addRequest, onDismiss: { connectionsViewModel.load() }) { request in
            AddConnectionView(
                viewModel: container.makeAddConnectionViewModel(),
                pairingCode: request.pairingCode,
                onAdded: didAdd
            )
        }
        .sheet(isPresented: $isShowingSettings) {
            SettingsView(settings: container.settings) {
                isShowingSettings = false
            }
        }
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
        case let .projectDetail(connection, project):
            ProjectDetailView(viewModel: container.makeProjectDetailViewModel(for: project, connection: connection))
        case let .serverProjects(connection):
            ServerProjectsView(
                connection: connection,
                controller: serverController(for: connection),
                onSelect: { projectID in
                    path.append(AppRoute.serverProject(connection: connection, projectID: projectID))
                }
            )
        case let .serverProject(connection, projectID):
            ServerProjectDetailView(
                connection: connection,
                server: serverController(for: connection),
                projectID: projectID,
                settings: container.settings
            )
        case let .sshTerminal(connection):
            SSHTerminalView(viewModel: container.makeSSHTerminalViewModel(for: connection))
        }
    }

    private func serverController(for connection: Connection) -> ServerController? {
        connection.serverID.flatMap(container.directory.controller(for:))
    }

    private func navigate(to connection: Connection) {
        switch connection.kind {
        case .device:
            path.append(AppRoute.projects(connection))
        case .server:
            path.append(AppRoute.serverProjects(connection))
        case .ssh:
            path.append(AppRoute.sshTerminal(connection))
        }
    }

    private func didAdd(_ connection: Connection) {
        addRequest = nil
        if let serverID = connection.serverID {
            container.directory.credentialDidChange(for: serverID)
        }
        navigate(to: connection)
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
        connectionsViewModel.load()
    }
}

private struct AddConnectionRequest: Identifiable {
    let id = UUID()
    var pairingCode: String?
}
