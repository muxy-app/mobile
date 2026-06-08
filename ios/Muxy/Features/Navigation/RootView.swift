import SwiftUI

struct RootView: View {
    let container: AppContainer

    @AppStorage("muxy.hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @State private var devicesViewModel: DevicesListViewModel
    @State private var path = NavigationPath()
    @State private var isAddingDevice = false
    @State private var isShowingSettings = false

    init(container: AppContainer) {
        self.container = container
        _devicesViewModel = State(initialValue: container.makeDevicesListViewModel())
    }

    var body: some View {
        Group {
            if hasCompletedOnboarding {
                NavigationStack(path: $path) {
                    DevicesListView(
                        viewModel: devicesViewModel,
                        onSelect: { device in path.append(AppRoute.projects(device)) },
                        onAddDevice: { isAddingDevice = true },
                        onSettings: { isShowingSettings = true }
                    )
                    .navigationDestination(for: AppRoute.self) { route in
                        switch route {
                        case let .projects(device):
                            ProjectsView(
                                viewModel: container.makeProjectsViewModel(for: device),
                                onSelect: { project in
                                    path.append(AppRoute.projectDetail(device: device, project: project))
                                }
                            )
                        case let .projectDetail(device, project):
                            ProjectDetailView(viewModel: container.makeProjectDetailViewModel(for: project, device: device))
                        }
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
        .sheet(isPresented: $isAddingDevice, onDismiss: { devicesViewModel.load() }) {
            AddDeviceView(
                viewModel: container.makeAddDeviceViewModel(),
                onPaired: { device in
                    isAddingDevice = false
                    path.append(AppRoute.projects(device))
                }
            )
        }
        .sheet(isPresented: $isShowingSettings) {
            SettingsView(settings: container.settings) {
                isShowingSettings = false
            }
        }
    }

    private func completeOnboarding() {
        hasCompletedOnboarding = true
    }

    private func completeOnboardingAndPair() {
        hasCompletedOnboarding = true
        isAddingDevice = true
    }

    private func applyDemoMode() {
        DemoDevice.apply(enabled: container.settings.demoMode, store: container.deviceStore, keychain: container.keychain)
        devicesViewModel.load()
    }
}
