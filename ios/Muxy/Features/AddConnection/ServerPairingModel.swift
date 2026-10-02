import Foundation
import MuxyMobile
import Observation
import OSLog

nonisolated struct PairingTarget: Equatable, Sendable {
    let link: String
    let address: String
    let source: DiscoverySource
}

@MainActor
@Observable
final class ServerPairingModel {
    enum Step: Equatable {
        case entry
        case confirm(PairingTarget)
        case pairing(PairingTarget)
    }

    var deviceName: String
    private(set) var step: Step = .entry
    private(set) var failure: String?

    private let pairing: ServerPairingService
    private let credentials: CredentialStore
    private let store: ConnectionStore

    init(pairing: ServerPairingService, credentials: CredentialStore, store: ConnectionStore, deviceName: String) {
        self.pairing = pairing
        self.credentials = credentials
        self.store = store
        self.deviceName = deviceName
    }

    var target: PairingTarget? {
        switch step {
        case .entry:
            return nil
        case let .confirm(target), let .pairing(target):
            return target
        }
    }

    var canPair: Bool {
        guard case .confirm = step else { return false }
        return !deviceName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var isPairing: Bool {
        guard case .pairing = step else { return false }
        return true
    }

    func accepts(_ link: String) -> Bool {
        (try? pairing.parse(link)) != nil
    }

    func receive(link: String, source: DiscoverySource) {
        guard !isPairing else { return }
        do {
            let parsed = try pairing.parse(link)
            step = .confirm(PairingTarget(link: link, address: Self.address(of: parsed), source: source))
            failure = nil
        } catch {
            step = .entry
            failure = ServerFailure(error).message(context: .pairing, serverName: "the computer")
        }
    }

    func pair() async -> Connection? {
        guard canPair, case let .confirm(target) = step else { return nil }
        step = .pairing(target)
        failure = nil

        let credential: ServerCredential
        do {
            credential = try await pairing.pair(link: target.link, deviceName: deviceName)
        } catch {
            let reason = ServerFailure(error)
            Log.pairing.error("Pairing failed: \(String(describing: reason), privacy: .public)")
            step = .confirm(target)
            failure = reason.message(context: .pairing, serverName: target.address)
            return nil
        }

        do {
            try credentials.save(credential)
        } catch {
            Log.pairing.error("Saving the pairing failed: \(error.localizedDescription, privacy: .public)")
            step = .confirm(target)
            failure = "Paired, but the pairing couldn't be saved securely. Try again with a new code."
            return nil
        }

        Log.pairing.info("Paired with a Muxy server")
        return saveConnection(for: credential, source: target.source)
    }

    private func saveConnection(for credential: ServerCredential, source: DiscoverySource) -> Connection {
        let existing = store.load().first { $0.kind == .server && $0.serverID == credential.serverId }
        let connection = Connection(
            id: existing?.id ?? UUID(),
            name: credential.serverName,
            host: credential.hosts.first ?? "",
            port: Int(credential.port),
            kind: .server,
            pairingState: .paired,
            discoverySource: source,
            serverID: credential.serverId
        )
        store.upsert(connection)
        return connection
    }

    private static func address(of link: PairingLink) -> String {
        "\(link.hosts.first ?? "unknown host"):\(link.port)"
    }
}
