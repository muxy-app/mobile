import Foundation

@MainActor
final class AppContainer {
    let connectionStore: ConnectionStore
    let keychain: KeychainStore
    let connectionManager: ConnectionManager
    let pairingService: PairingService
    let validator: ConnectionInputValidator
    let tokenGenerator: TokenGenerating
    let settings: AppSettings
    let themeStore: ThemeStore

    private let makeBrowser: @MainActor () -> any BonjourBrowsing

    init(
        connectionStore: ConnectionStore = UserDefaultsConnectionStore(),
        keychain: KeychainStore = KeychainTokenStore(),
        pairingService: PairingService = LivePairingService(),
        validator: ConnectionInputValidator = ConnectionInputValidator(),
        tokenGenerator: TokenGenerating = TokenGenerator(),
        settings: AppSettings? = nil,
        makeBrowser: @escaping @MainActor () -> any BonjourBrowsing = { BonjourBrowser() }
    ) {
        self.connectionStore = connectionStore
        self.keychain = keychain
        self.pairingService = pairingService
        self.validator = validator
        self.tokenGenerator = tokenGenerator
        self.settings = settings ?? AppSettings()
        self.makeBrowser = makeBrowser
        let connectionManager = ConnectionManager(
            makeTransport: { url in WebSocketTransport(url: url) },
            pairingService: pairingService
        )
        self.connectionManager = connectionManager
        themeStore = ThemeStore(connectionManager: connectionManager)
    }

    func makeConnectionsListViewModel() -> ConnectionsListViewModel {
        ConnectionsListViewModel(store: connectionStore, keychain: keychain)
    }

    func makeAddConnectionViewModel() -> AddConnectionViewModel {
        AddConnectionViewModel(
            store: connectionStore,
            keychain: keychain,
            connectionManager: connectionManager,
            validator: validator,
            tokenGenerator: tokenGenerator,
            browser: makeBrowser()
        )
    }

    func makeProjectsViewModel(for connection: Connection) -> ProjectsViewModel {
        ProjectsViewModel(connection: connection, keychain: keychain, connectionManager: connectionManager)
    }

    func makeProjectDetailViewModel(for project: Project, connection: Connection) -> ProjectDetailViewModel {
        let sessionStore = TerminalSessionStore(channel: connectionManager)
        return ProjectDetailViewModel(
            connection: connection,
            project: project,
            keychain: keychain,
            connectionManager: connectionManager,
            sessionStore: sessionStore
        )
    }

    func makeSSHTerminalViewModel(for connection: Connection) -> SSHTerminalViewModel {
        SSHTerminalViewModel(connection: connection, keychain: keychain)
    }
}
