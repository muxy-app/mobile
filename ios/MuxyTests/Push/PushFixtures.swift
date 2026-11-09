import CryptoKit
import Foundation
import MuxyMobile
import MuxyPush
import Synchronization
@testable import Muxy

enum PushVectors {
    static let keyBytes = Data((0..<32).map { UInt8($0) })
    static let nonce = Data((0x20...0x2b).map { UInt8($0) })
    static let serverID = "6b0c7e2a9f1d4c3b8a7e6d5c4b3a2910"
    static let projectID = "0f3a5c7e9b1d2f4a6c8e0b2d4f6a8c1e"
    static let plaintext = #"{"v":1,"server_id":"6b0c7e2a9f1d4c3b8a7e6d5c4b3a2910","project_id":"0f3a5c7e9b1d2f4a6c8e0b2d4f6a8c1e","session_id":42,"event_id":1731067200000123,"kind":"attention","title":"Claude Code · muxy-website","body":"Needs your attention","ts":1731067200}"#
    static let payload = "AWJ9-ET_FPTtICEiIyQlJicoKSorqRjQUlapNixpGTC4pGqrkLRr1r6x4lONW5Nec3Dsb21B7dZmtkQCvyKZW_Fsayyp0BPdvrgQDxG8DRV4L8ZVtLwE9ymm8db3vgneQoI_CAXn8QdlgZi-e_8t8E4ueqi9OjcfM-povICs79duzJiSehSztXaj8o1oJpdpe6ru5goG1UqtCt41hle_klo-Dy2U5OYJXrr2vqgKKtxb_ra_ubCaUg3w0QPlldusnpwIWMfKxtjhaYCgWQ7gLBPGV6bSmo4Bcxb1rSswP5mcjbzPte8f11CjunfsIcZmKTV0KnlIcQD_9AAR3n9Gr-LLzKEm-U6CvwQ4NQPD2UDfWA59eAGjg59TxG-3fQ"
    static let target = PushTarget(serverId: serverID, sessionId: 42, projectId: projectID, eventId: 1_731_067_200_000_123)

    static var key: PushKey {
        PushKey(serverId: serverID, bytes: keyBytes)!
    }

    static func userInfo(payload: String = payload, version: Int = 1) -> [AnyHashable: Any] {
        [
            "aps": ["alert": ["title": "Muxy", "body": "An agent needs your attention"], "mutable-content": 1],
            "muxy": ["v": version, "kind": "attention", "payload": payload],
        ]
    }

    static func seal(_ plaintext: String, keyBytes: Data = keyBytes, serverID: String = serverID, nonce: Data = nonce) -> String {
        let key = PushKey(serverId: serverID, bytes: keyBytes)!
        let header = Data([1]) + key.keyID
        let sealed = try! AES.GCM.seal(
            Data(plaintext.utf8),
            using: SymmetricKey(data: keyBytes),
            nonce: AES.GCM.Nonce(data: nonce),
            authenticating: header + Data(serverID.utf8)
        )
        return base64URL(header + nonce + sealed.ciphertext + sealed.tag)
    }

    static func message(serverID: String = serverID, version: Int = 1, sessionID: UInt64 = 42) -> String {
        #"{"v":\#(version),"server_id":"\#(serverID)","project_id":"\#(projectID)","session_id":\#(sessionID),"event_id":7,"kind":"completed","title":"Codex · muxy","body":"Finished working","ts":1731067200,"extra":"ignored"}"#
    }

    static func base64URL(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

final class InMemoryPushKeyStore: PushKeyStore {
    private let stored = Mutex<[String: PushKey]>([:])

    func keys() throws -> [PushKey] {
        stored.withLock { Array($0.values) }
    }

    func key(serverId: String) throws -> PushKey? {
        stored.withLock { $0[serverId] }
    }

    func save(_ key: PushKey) throws {
        stored.withLock { $0[key.serverId] = key }
    }

    func delete(serverId: String) throws {
        _ = stored.withLock { $0.removeValue(forKey: serverId) }
    }
}

@MainActor
final class FakeNotificationClient: NotificationClient {
    var current: PushAuthorization = .allowed
    var grants = true
    private(set) var remoteRegistrations = 0
    private(set) var removedIdentifiers: [String] = []

    func authorization() async -> PushAuthorization {
        current
    }

    func requestAuthorization() async throws -> Bool {
        current = grants ? .allowed : .denied
        return grants
    }

    func registerForRemoteNotifications() {
        remoteRegistrations += 1
    }

    func removeDeliveredNotifications(withIdentifiers identifiers: [String]) {
        removedIdentifiers += identifiers
    }
}

@MainActor
final class FakePushContext: ServerPushContext {
    var deviceToken: String? = String(repeating: "ab", count: 32)
    var environment: PushEnvironment = .sandbox
    var intent: PushIntent = .wanted
    var registered: Set<String> = []
    private(set) var statuses: [String: PushStatus] = [:]
    private(set) var unsupported: Set<String> = []
    private(set) var unregistered: [String] = []
    private(set) var freshTokenRequests = 0
    private(set) var removedSessions: [UInt64] = []
    private(set) var syncs = 0

    func pushIntent(for serverID: String) -> PushIntent {
        syncs += 1
        return intent
    }

    func isRegistered(_ serverID: String) -> Bool {
        registered.contains(serverID)
    }

    func record(_ registration: PushRegistration, for serverID: String) {
        statuses[serverID] = registration.status
        registered.insert(serverID)
    }

    func recordUnregistered(serverID: String) {
        registered.remove(serverID)
        unregistered.append(serverID)
    }

    func recordUnsupported(serverID: String) {
        unsupported.insert(serverID)
    }

    func requestFreshToken() {
        freshTokenRequests += 1
    }

    func removeDeliveredNotifications(forSession sessionID: UInt64, serverID: String) {
        removedSessions.append(sessionID)
    }

    func forget(serverID: String) {
        registered.remove(serverID)
    }
}

@MainActor
enum PushTestSupport {
    static func controller(
        _ connections: FakeServerConnection...,
        context: (any ServerPushContext)? = nil
    ) -> (ServerController, FakeServerConnector) {
        let connector = FakeServerConnector(outcomes: connections.map { .success($0) })
        let controller = ServerController(
            serverID: "server-1",
            serverName: "Studio",
            connector: connector,
            credentialProvider: { Fixtures.credential() },
            pushContext: context,
            schedule: ReconnectSchedule(initialDelays: [.milliseconds(5)], steadyDelay: .milliseconds(5), afterRestart: .milliseconds(5))
        )
        return (controller, connector)
    }

    static func settle(_ condition: () -> Bool) async {
        for _ in 0..<400 where !condition() {
            try? await Task.sleep(for: .milliseconds(5))
        }
    }
}
