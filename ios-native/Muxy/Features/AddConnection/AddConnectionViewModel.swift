import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class AddConnectionViewModel {
    enum Status: Equatable {
        case idle
        case connecting
        case authenticating
        case awaitingApproval
        case succeeded
        case failed(String)
    }

    var kind: ConnectionKind = .device
    var name: String = ""
    var host: String = ""
    var portText: String = String(Endpoint.defaultPort)
    var username: String = ""
    var authMethod: SSHAuthMethod = .password
    var password: String = ""
    var privateKey: String = ""
    var passphrase: String = ""
    var isShowingScanner = false

    private(set) var status: Status = .idle
    private(set) var discoverySource: DiscoverySource = .manual
    private var serviceName: String?

    let browser: any BonjourBrowsing
    let serverPairing: ServerPairingModel

    private let store: ConnectionStore
    private let keychain: KeychainStore
    private let connectionManager: ConnectionManager
    private let validator: ConnectionInputValidator
    private let tokenGenerator: TokenGenerating

    init(
        store: ConnectionStore,
        keychain: KeychainStore,
        connectionManager: ConnectionManager,
        validator: ConnectionInputValidator,
        tokenGenerator: TokenGenerating,
        browser: any BonjourBrowsing,
        serverPairing: ServerPairingModel
    ) {
        self.store = store
        self.keychain = keychain
        self.connectionManager = connectionManager
        self.validator = validator
        self.tokenGenerator = tokenGenerator
        self.browser = browser
        self.serverPairing = serverPairing
    }

    var isWorking: Bool {
        if serverPairing.isPairing { return true }
        switch status {
        case .connecting, .authenticating, .awaitingApproval:
            return true
        default:
            return false
        }
    }

    var sshDefaultsApplied = false

    var canSubmit: Bool {
        guard !isWorking else { return false }
        switch kind {
        case .device:
            return (try? validator.validate(name: name, host: host, portText: portText).get()) != nil
        case .server:
            return serverPairing.canPair
        case .ssh:
            return (try? validatedSSH().get()) != nil
        }
    }

    var displayedStatus: Status {
        guard kind == .server else { return status }
        if serverPairing.isPairing { return .connecting }
        guard let failure = serverPairing.failure else { return .idle }
        return .failed(failure)
    }

    var discoveredServices: [DiscoveredService] {
        browser.services
    }

    func startDiscovery() {
        browser.start()
    }

    func stopDiscovery() {
        browser.stop()
    }

    func selectKind(_ kind: ConnectionKind) {
        guard self.kind != kind else { return }
        self.kind = kind
        status = .idle
        if kind == .ssh, !sshDefaultsApplied {
            portText = "22"
            sshDefaultsApplied = true
        }
    }

    func applyScan(_ uri: PairingURI) {
        host = uri.host
        portText = String(uri.port)
        if let label = uri.label { name = label }
        serviceName = uri.serviceName
        discoverySource = .qr
        isShowingScanner = false
    }

    func applyPairingCode(_ code: String, source: DiscoverySource) -> Bool {
        isShowingScanner = false
        if serverPairing.accepts(code) {
            selectKind(.server)
            serverPairing.receive(link: code, source: source)
            return true
        }
        guard let uri = try? PairingURI.parse(code) else { return false }
        selectKind(.device)
        applyScan(uri)
        return true
    }

    func applyDiscovered(_ service: DiscoveredService) {
        name = service.name
        host = service.host
        portText = String(service.port)
        serviceName = service.name
        discoverySource = .bonjour
    }

    func submit(onAdded: @escaping (Connection) -> Void) async {
        switch kind {
        case .device:
            await pairDevice(onAdded: onAdded)
        case .server:
            guard let connection = await serverPairing.pair() else { return }
            onAdded(connection)
        case .ssh:
            await addSSH(onAdded: onAdded)
        }
    }

    private func pairDevice(onAdded: @escaping (Connection) -> Void) async {
        guard case let .success(input) = validator.validate(name: name, host: host, portText: portText) else { return }
        let connection = Connection(
            id: UUID(),
            name: input.name,
            host: input.host,
            port: input.port,
            kind: .device,
            pairingState: .notPaired,
            serviceName: serviceName,
            discoverySource: discoverySource
        )
        let token: String
        do {
            token = try tokenGenerator.generate()
            try keychain.setToken(token, for: connection.id)
        } catch {
            Log.pairing.error("Failed to prepare pairing token: \(error.localizedDescription, privacy: .public)")
            status = .failed("Couldn't prepare the pairing token.")
            return
        }

        let result = await connectionManager.beginPairing(connection: connection, token: token) { [weak self] pairingStatus in
            Task { @MainActor in self?.applyPairing(pairingStatus) }
        }

        guard case .paired = result else { return }
        var paired = connection
        paired.pairingState = .paired
        store.upsert(paired)
        onAdded(paired)
    }

    private func addSSH(onAdded: @escaping (Connection) -> Void) async {
        guard case let .success(input) = validatedSSH() else { return }
        let connection = Connection(
            id: UUID(),
            name: input.name,
            host: input.host,
            port: input.port,
            kind: .ssh,
            sshConfig: SSHConfig(username: input.username, authMethod: input.authMethod)
        )

        do {
            let secretKind: KeychainSecret = input.authMethod == .password ? .sshPassword : .sshPrivateKey
            try keychain.setSecret(input.secret, secretKind, for: connection.id)
            if let passphrase = input.passphrase {
                try keychain.setSecret(passphrase, .sshPassphrase, for: connection.id)
            }
        } catch {
            Log.ssh.error("Failed to store SSH secrets: \(error.localizedDescription, privacy: .public)")
            status = .failed("Couldn't securely store the credentials.")
            return
        }

        status = .connecting
        let result = await SSHConnectionTester.test(connection: connection, keychain: keychain)
        switch result {
        case .success:
            status = .succeeded
            store.upsert(connection)
            onAdded(connection)
        case let .failure(error):
            try? keychain.deleteSecrets(for: connection.id)
            status = .failed(error.message)
        }
    }

    private func applyPairing(_ pairingStatus: PairingStatus) {
        switch pairingStatus {
        case .idle:
            status = .idle
        case .connecting:
            status = .connecting
        case .authenticating:
            status = .authenticating
        case .awaitingApproval:
            status = .awaitingApproval
        case .paired:
            status = .succeeded
        case let .failed(error):
            status = .failed(message(for: error))
        }
    }

    private func validatedSSH() -> Result<ValidatedSSHInput, ConnectionInputError> {
        let secret = authMethod == .password ? password : privateKey
        return validator.validateSSH(
            name: name,
            host: host,
            portText: portText,
            username: username,
            authMethod: authMethod,
            secret: secret,
            passphrase: passphrase
        )
    }

    private func message(for error: PairingError) -> String {
        switch error {
        case .connectionFailed:
            return "Couldn't connect. Check the host and port."
        case .approvalDenied:
            return "The Mac denied this device."
        case .approvalTimedOut:
            return "Approval timed out. Try again."
        case .wrongToken:
            return "This device's credentials are invalid. Remove it and add it again."
        case .invalidResponse:
            return "The Mac sent an unexpected response."
        case let .server(code, serverMessage):
            return "Server error \(code): \(serverMessage)"
        }
    }
}
