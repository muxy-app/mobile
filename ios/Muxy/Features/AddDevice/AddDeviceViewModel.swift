import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class AddDeviceViewModel {
    var name: String = ""
    var host: String = ""
    var portText: String = String(Endpoint.defaultPort)
    var isShowingScanner = false

    private(set) var status: PairingStatus = .idle
    private(set) var discoverySource: DiscoverySource = .manual
    private var serviceName: String?

    let browser: any BonjourBrowsing

    private let store: DeviceStore
    private let keychain: KeychainStore
    private let connectionManager: ConnectionManager
    private let validator: DeviceInputValidator
    private let tokenGenerator: TokenGenerating

    init(
        store: DeviceStore,
        keychain: KeychainStore,
        connectionManager: ConnectionManager,
        validator: DeviceInputValidator,
        tokenGenerator: TokenGenerating,
        browser: any BonjourBrowsing
    ) {
        self.store = store
        self.keychain = keychain
        self.connectionManager = connectionManager
        self.validator = validator
        self.tokenGenerator = tokenGenerator
        self.browser = browser
    }

    var isPairing: Bool {
        switch status {
        case .connecting, .authenticating, .awaitingApproval:
            return true
        default:
            return false
        }
    }

    var canSubmit: Bool {
        guard !isPairing else { return false }
        return (try? validator.validate(name: name, host: host, portText: portText).get()) != nil
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

    func applyScan(_ uri: PairingURI) {
        host = uri.host
        portText = String(uri.port)
        if let label = uri.label { name = label }
        serviceName = uri.serviceName
        discoverySource = .qr
        isShowingScanner = false
    }

    func applyDiscovered(_ service: DiscoveredService) {
        name = service.name
        host = service.host
        portText = String(service.port)
        serviceName = service.name
        discoverySource = .bonjour
    }

    func submit(onPaired: @escaping (Device) -> Void) async {
        guard case let .success(input) = validator.validate(name: name, host: host, portText: portText) else { return }
        await pair(input: input, onPaired: onPaired)
    }

    private func pair(input: ValidatedDeviceInput, onPaired: @escaping (Device) -> Void) async {
        let device = Device(
            id: UUID(),
            name: input.name,
            host: input.host,
            port: input.port,
            pairingState: .notPaired,
            serviceName: serviceName,
            discoverySource: discoverySource
        )
        let token: String
        do {
            token = try tokenGenerator.generate()
            try keychain.setToken(token, for: device.id)
        } catch {
            Log.pairing.error("Failed to prepare pairing token: \(error.localizedDescription, privacy: .public)")
            status = .failed(.connectionFailed)
            return
        }

        let result = await connectionManager.beginPairing(device: device, token: token) { [weak self] status in
            Task { @MainActor in self?.status = status }
        }

        guard case .paired = result else { return }
        var pairedDevice = device
        pairedDevice.pairingState = .paired
        store.upsert(pairedDevice)
        onPaired(pairedDevice)
    }
}
