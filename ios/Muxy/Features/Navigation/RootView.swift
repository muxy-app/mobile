import SwiftUI

struct RootView: View {
    let container: AppContainer

    @AppStorage("muxy.hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @State private var connectionsViewModel: ConnectionsListViewModel
    @State private var themeStore: ThemeStore
    @State private var path = NavigationPath()
    @State private var isAddingConnection = false
    @State private var isShowingSettings = false

    init(container: AppContainer) {
        self.container = container
        _connectionsViewModel = State(initialValue: container.makeConnectionsListViewModel())
        _themeStore = State(initialValue: container.themeStore)
    }

    private var theme: AppTheme { themeStore.theme }

    var body: some View {
        themedContent
            .environment(\.appTheme, theme)
            .preferredColorScheme(theme.isDark ? .dark : .light)
            .tint(theme.accent)
            .themedWindowBackground(theme.background)
            .onAppear { themeStore.start() }
            .onChange(of: theme) { _, newTheme in NavigationBarAppearance.apply(newTheme) }
            .task { NavigationBarAppearance.apply(theme) }
    }

    private var themedContent: some View {
        Group {
            if hasCompletedOnboarding {
                NavigationStack(path: $path) {
                    ConnectionsListView(
                        viewModel: connectionsViewModel,
                        onSelect: navigate(to:),
                        onAddConnection: { isAddingConnection = true },
                        onSettings: { isShowingSettings = true }
                    )
                    .navigationDestination(for: AppRoute.self) { route in
                        destination(for: route)
                    }
                }
                .onChange(of: path.count) { _, newCount in
                    guard newCount == 0 else { return }
                    Task { await container.connectionManager.disconnect() }
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
        .sheet(isPresented: $isAddingConnection, onDismiss: { connectionsViewModel.load() }) {
            AddConnectionView(
                viewModel: container.makeAddConnectionViewModel(),
                onAdded: { connection in
                    isAddingConnection = false
                    navigate(to: connection)
                }
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
        case let .sshTerminal(connection):
            SSHTerminalView(viewModel: container.makeSSHTerminalViewModel(for: connection))
        }
    }

    private func navigate(to connection: Connection) {
        switch connection.kind {
        case .device:
            path.append(AppRoute.projects(connection))
        case .ssh:
            path.append(AppRoute.sshTerminal(connection))
        }
    }

    private func completeOnboarding() {
        hasCompletedOnboarding = true
    }

    private func completeOnboardingAndPair() {
        hasCompletedOnboarding = true
        isAddingConnection = true
    }

    private func applyDemoMode() {
        DemoConnection.apply(enabled: container.settings.demoMode, store: container.connectionStore, keychain: container.keychain)
        connectionsViewModel.load()
    }
}
