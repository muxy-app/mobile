import Foundation
import MuxyMobile
import OSLog

@MainActor
protocol ServerPushContext: AnyObject {
    var deviceToken: String? { get }
    var environment: PushEnvironment { get }
    func wantsNotifications(from serverID: String) -> Bool
    func hasKey(for serverID: String) -> Bool
    func record(_ registration: PushRegistration, for serverID: String)
    func recordUnregistered(serverID: String)
    func recordUnsupported(serverID: String)
    func requestFreshToken()
    func removeDeliveredNotifications(forSession sessionID: UInt64, serverID: String)
    func forget(serverID: String)
}

@MainActor
final class PushRegistrar {
    weak var server: ServerController?
    weak var context: (any ServerPushContext)?

    private let refresh = CoalescedRefresh()
    private var registeredToken: String?

    func connectionDidOpen() {
        registeredToken = nil
        sync()
    }

    func connectionDidClose() {
        refresh.cancel()
        registeredToken = nil
    }

    func sync() {
        guard server?.connection != nil else { return }
        refresh.request { [weak self] in await self?.performSync() }
    }

    func unregister() async {
        guard let server, let connection = server.connection, connection.supportsPushNotifications,
              context?.hasKey(for: server.serverID) == true else { return }
        await unregister(serverID: server.serverID, on: connection)
    }

    private func performSync() async {
        guard let server, let context, let connection = server.connection else { return }
        let serverID = server.serverID
        guard connection.supportsPushNotifications else {
            context.recordUnsupported(serverID: serverID)
            return
        }
        guard context.wantsNotifications(from: serverID) else {
            guard context.hasKey(for: serverID) else { return }
            await unregister(serverID: serverID, on: connection)
            return
        }
        guard let token = context.deviceToken, token != registeredToken else { return }
        await register(token, serverID: serverID, on: connection)
    }

    private func register(_ token: String, serverID: String, on connection: any ServerConnection) async {
        guard let context else { return }
        do {
            let registration = try await connection.registerPush(token: token, environment: context.environment)
            guard connection === server?.connection else { return }
            registeredToken = token
            context.record(registration, for: serverID)
            Log.push.info("Registered for notifications: \(String(describing: registration.status), privacy: .public)")
            guard registration.status == .refused else { return }
            context.requestFreshToken()
        } catch {
            let failure = ServerFailure(error)
            Log.push.error("Registering for notifications failed: \(String(describing: failure), privacy: .public)")
            guard failure == .unsupported else { return }
            context.recordUnsupported(serverID: serverID)
        }
    }

    private func unregister(serverID: String, on connection: any ServerConnection) async {
        do {
            try await connection.unregisterPush()
            registeredToken = nil
            context?.recordUnregistered(serverID: serverID)
            Log.push.info("Unregistered from notifications")
        } catch {
            Log.push.error("Unregistering from notifications failed: \(String(describing: ServerFailure(error)), privacy: .public)")
        }
    }
}
