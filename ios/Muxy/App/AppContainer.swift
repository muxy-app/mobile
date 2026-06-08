import Foundation

@MainActor
final class AppContainer {
    let deviceStore: DeviceStore
    let keychain: KeychainStore
    let connectionManager: ConnectionManager
    let pairingService: PairingService
    let validator: DeviceInputValidator
    let tokenGenerator: TokenGenerating
    let settings: AppSettings

    private let makeBrowser: @MainActor () -> any BonjourBrowsing

    init(
        deviceStore: DeviceStore = UserDefaultsDeviceStore(),
        keychain: KeychainStore = KeychainTokenStore(),
        pairingService: PairingService = LivePairingService(),
        validator: DeviceInputValidator = DeviceInputValidator(),
        tokenGenerator: TokenGenerating = TokenGenerator(),
        settings: AppSettings? = nil,
        makeBrowser: @escaping @MainActor () -> any BonjourBrowsing = { BonjourBrowser() }
    ) {
        self.deviceStore = deviceStore
        self.keychain = keychain
        self.pairingService = pairingService
        self.validator = validator
        self.tokenGenerator = tokenGenerator
        self.settings = settings ?? AppSettings()
        self.makeBrowser = makeBrowser
        self.connectionManager = ConnectionManager(
            makeTransport: { url in WebSocketTransport(url: url) },
            pairingService: pairingService
        )
    }

    func makeDevicesListViewModel() -> DevicesListViewModel {
        DevicesListViewModel(store: deviceStore, keychain: keychain)
    }

    func makeAddDeviceViewModel() -> AddDeviceViewModel {
        AddDeviceViewModel(
            store: deviceStore,
            keychain: keychain,
            connectionManager: connectionManager,
            validator: validator,
            tokenGenerator: tokenGenerator,
            browser: makeBrowser()
        )
    }

    func makeProjectsViewModel(for device: Device) -> ProjectsViewModel {
        ProjectsViewModel(device: device, keychain: keychain, connectionManager: connectionManager)
    }

    func makeProjectDetailViewModel(for project: Project, device: Device) -> ProjectDetailViewModel {
        let sessionStore = TerminalSessionStore(channel: connectionManager)
        return ProjectDetailViewModel(
            device: device,
            project: project,
            keychain: keychain,
            connectionManager: connectionManager,
            sessionStore: sessionStore
        )
    }
}
