import Foundation
import MuxyMobile
import MuxyPush
import Observation
import OSLog
import UserNotifications

nonisolated struct PushOpenRequest: Identifiable, Equatable, Sendable {
    enum Destination: Equatable, Sendable {
        case home
        case session(PushTarget)
    }

    let id = UUID()
    let destination: Destination
}

@MainActor
@Observable
final class PushCenter {
    private static let promptDismissedKey = "muxy.push.promptDismissed"

    let environment: PushEnvironment
    private(set) var authorization: PushAuthorization = .notDetermined
    private(set) var deviceToken: String?
    private(set) var servers: [PushServer] = []
    private(set) var openRequest: PushOpenRequest?
    private(set) var isPromptDismissed: Bool

    @ObservationIgnored var contextDidChange: () -> Void = {}
    @ObservationIgnored private let keyring: PushKeyring
    @ObservationIgnored private let client: NotificationClient
    @ObservationIgnored private let defaults: UserDefaults

    init(
        keyring: PushKeyring,
        client: NotificationClient,
        environment: PushEnvironment,
        defaults: UserDefaults = .standard
    ) {
        self.keyring = keyring
        self.client = client
        self.environment = environment
        self.defaults = defaults
        isPromptDismissed = defaults.bool(forKey: Self.promptDismissedKey)
        servers = keyring.servers.servers()
    }

    static func live() -> PushCenter {
        PushCenter(keyring: .shared(), client: SystemNotificationClient(), environment: .current())
    }

    func isEnabled(_ serverID: String) -> Bool {
        servers.first { $0.serverId == serverID }?.isEnabled ?? true
    }

    func status(for serverID: String) -> PushServerStatus? {
        servers.first { $0.serverId == serverID }?.status.flatMap(PushServerStatus.init(rawValue:))
    }

    func refreshAuthorization() async {
        let current = await client.authorization()
        let changed = current != authorization
        authorization = current
        if current == .allowed {
            client.registerForRemoteNotifications()
        }
        guard changed else { return }
        contextDidChange()
    }

    @discardableResult
    func requestPermission() async -> Bool {
        do {
            _ = try await client.requestAuthorization()
        } catch {
            Log.push.error("Requesting notification permission failed: \(error.localizedDescription, privacy: .public)")
        }
        await refreshAuthorization()
        return authorization == .allowed
    }

    func requestPermissionIfUndetermined() async {
        guard authorization == .notDetermined else { return }
        await requestPermission()
    }

    func dismissPrompt() {
        isPromptDismissed = true
        defaults.set(true, forKey: Self.promptDismissedKey)
    }

    func didRegister(deviceToken data: Data) {
        let token = data.map { String(format: "%02x", $0) }.joined()
        guard token != deviceToken else { return }
        deviceToken = token
        contextDidChange()
    }

    func didFailToRegister(_ error: any Error) {
        Log.push.error("Registering with APNs failed: \(error.localizedDescription, privacy: .public)")
    }

    func syncServers(_ credentials: [ServerCredential]) {
        let paired = Dictionary(credentials.map { ($0.serverId, $0.serverName) }, uniquingKeysWith: { first, _ in first })
        for server in keyring.servers.servers() where paired[server.serverId] == nil {
            deleteKey(for: server.serverId)
            keyring.servers.remove(server.serverId)
        }
        for (serverID, name) in paired {
            guard var server = keyring.servers.server(serverID) else {
                keyring.servers.save(PushServer(serverId: serverID, name: name))
                continue
            }
            guard server.name != name else { continue }
            server.name = name
            keyring.servers.save(server)
        }
        reloadServers()
    }

    func setEnabled(_ enabled: Bool, for serverID: String) async {
        guard var server = keyring.servers.server(serverID) else { return }
        server.isEnabled = enabled
        keyring.servers.save(server)
        reloadServers()
        if enabled {
            await requestPermissionIfUndetermined()
        }
        contextDidChange()
    }

    func open(_ userInfo: [AnyHashable: Any]) {
        guard PushNotificationInfo.isMuxy(userInfo) else { return }
        let target = PushNotificationInfo.target(in: userInfo, keys: activeKeys())
        openRequest = PushOpenRequest(destination: target.map { .session($0) } ?? .home)
    }

    func consume(_ request: PushOpenRequest) {
        guard openRequest?.id == request.id else { return }
        openRequest = nil
    }

    func presentationOptions(
        for userInfo: [AnyHashable: Any],
        isConnected: (String) -> Bool
    ) -> UNNotificationPresentationOptions {
        guard PushNotificationInfo.isMuxy(userInfo) else { return [] }
        let shown: UNNotificationPresentationOptions = [.banner, .list, .sound]
        guard let target = PushNotificationInfo.target(in: userInfo, keys: activeKeys()) else { return shown }
        return isConnected(target.serverId) ? [] : shown
    }

    private func activeKeys() -> [PushKey] {
        do {
            return try keyring.activeKeys()
        } catch {
            Log.push.error("Reading push keys failed: \(String(describing: error), privacy: .public)")
            return []
        }
    }

    private func reloadServers() {
        servers = keyring.servers.servers()
    }

    private func update(_ serverID: String, status: PushServerStatus?) {
        guard var server = keyring.servers.server(serverID) else { return }
        server.status = status?.rawValue
        keyring.servers.save(server)
        reloadServers()
    }

    private func deleteKey(for serverID: String) {
        do {
            try keyring.keys.delete(serverId: serverID)
        } catch {
            Log.push.error("Deleting a push key failed: \(String(describing: error), privacy: .public)")
        }
    }
}

extension PushCenter: ServerPushContext {
    func wantsNotifications(from serverID: String) -> Bool {
        authorization == .allowed && isEnabled(serverID)
    }

    func hasKey(for serverID: String) -> Bool {
        (try? keyring.keys.key(serverId: serverID)) != nil
    }

    func record(_ registration: PushRegistration, for serverID: String) {
        guard keyring.servers.server(serverID) != nil else { return }
        update(serverID, status: PushServerStatus(registration.status))
        guard registration.status != .disabled, let bytes = registration.key,
              let key = PushKey(serverId: serverID, bytes: bytes) else {
            deleteKey(for: serverID)
            return
        }
        do {
            try keyring.keys.save(key)
        } catch {
            Log.push.error("Saving a push key failed: \(String(describing: error), privacy: .public)")
        }
    }

    func recordUnregistered(serverID: String) {
        deleteKey(for: serverID)
        update(serverID, status: nil)
    }

    func recordUnsupported(serverID: String) {
        guard status(for: serverID) != .unsupported else { return }
        update(serverID, status: .unsupported)
    }

    func requestFreshToken() {
        client.registerForRemoteNotifications()
    }

    func removeDeliveredNotifications(forSession sessionID: UInt64, serverID: String) {
        guard let key = try? keyring.keys.key(serverId: serverID) else { return }
        client.removeDeliveredNotifications(withIdentifiers: [key.collapseID(sessionId: sessionID)])
    }

    func forget(serverID: String) {
        deleteKey(for: serverID)
        keyring.servers.remove(serverID)
        reloadServers()
    }
}

extension PushEnvironment {
    static func current(bundle: Bundle = .main) -> PushEnvironment {
        bundle.object(forInfoDictionaryKey: "MuxyAPSEnvironment") as? String == "production" ? .production : .sandbox
    }
}
