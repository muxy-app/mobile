import Foundation
import MuxyMobile
import MuxyPush
import Testing
import UserNotifications
@testable import Muxy

@MainActor
struct PushRoutingTests {
    private let paired = Connection(
        id: UUID(),
        name: "Studio",
        host: "studio.local",
        port: 7419,
        kind: .server,
        serverID: PushVectors.serverID,
        serverTransport: .paired
    )
    private let overSSH = Connection(
        id: UUID(),
        name: "Studio over SSH",
        host: "studio.local",
        port: 22,
        kind: .server,
        serverID: PushVectors.serverID,
        serverTransport: .ssh
    )

    @Test func aDecryptedNotificationNamesItsTarget() {
        #expect(PushNotificationInfo.target(in: PushVectors.userInfo(), keys: [PushVectors.key]) == PushVectors.target)
    }

    @Test func notificationsThatCannotBeDecryptedHaveNoTarget() {
        #expect(PushNotificationInfo.target(in: PushVectors.userInfo(), keys: []) == nil)
        #expect(PushNotificationInfo.target(in: PushVectors.userInfo(payload: "garbage"), keys: [PushVectors.key]) == nil)
        #expect(PushNotificationInfo.target(in: PushVectors.userInfo(version: 2), keys: [PushVectors.key]) == nil)
    }

    @Test func silentTokenChecksAreNotMuxyNotifications() {
        let silent: [AnyHashable: Any] = ["aps": ["content-available": 1]]
        #expect(!PushNotificationInfo.isMuxy(silent))
        #expect(PushNotificationInfo.payload(in: silent) == nil)
    }

    @Test func aTargetOpensASessionRouteOnItsPairedServer() {
        let route = AppRoute.opening(PushVectors.target, among: [overSSH, paired])
        #expect(route == .serverSession(connection: paired, target: PushVectors.target))
        #expect(route?.serverID == PushVectors.serverID)
    }

    @Test func aTargetFromAnUnpairedServerOpensNothing() {
        #expect(AppRoute.opening(PushVectors.target, among: [overSSH]) == nil)
        #expect(AppRoute.opening(PushVectors.target, among: []) == nil)
    }
}

@MainActor
struct PushCenterTests {
    private func center() -> PushCenter {
        center(client: FakeNotificationClient())
    }

    private func center(client: FakeNotificationClient) -> PushCenter {
        let defaults = UserDefaults(suiteName: "PushCenterTests.\(UUID().uuidString)")!
        let keyring = PushKeyring(keys: InMemoryPushKeyStore(), servers: PushServerDirectory(defaults: defaults))
        try? keyring.keys.save(PushVectors.key)
        keyring.servers.save(PushServer(serverId: PushVectors.serverID, name: "Studio"))
        return PushCenter(keyring: keyring, client: client, environment: .sandbox, defaults: defaults)
    }

    @Test func tappingADecryptedNotificationOpensItsSession() {
        let push = center()
        push.open(PushVectors.userInfo())
        #expect(push.openRequest?.destination == .session(PushVectors.target))
    }

    @Test func tappingAnUnreadableNotificationOpensHome() {
        let push = center()
        push.open(PushVectors.userInfo(payload: "garbage"))
        #expect(push.openRequest?.destination == .home)
    }

    @Test func aServerTurnedOffLocallyNoLongerDecrypts() async {
        let push = center()
        await push.setEnabled(false, for: PushVectors.serverID)
        push.open(PushVectors.userInfo())
        #expect(push.openRequest?.destination == .home)
    }

    @Test func aRequestIsConsumedOnce() throws {
        let push = center()
        push.open(PushVectors.userInfo())
        let request = try #require(push.openRequest)
        push.consume(request)
        #expect(push.openRequest == nil)
    }

    @Test func bannersAreHiddenOnlyWhileConnectedToThatServer() {
        let push = center()
        #expect(push.presentationOptions(for: PushVectors.userInfo()) { $0 == PushVectors.serverID } == [])
        #expect(push.presentationOptions(for: PushVectors.userInfo()) { _ in false } == [.banner, .list, .sound])
        #expect(push.presentationOptions(for: PushVectors.userInfo(payload: "garbage")) { _ in true } == [.banner, .list, .sound])
    }

    @Test func acknowledgingRemovesTheSessionsDeliveredNotification() {
        let client = FakeNotificationClient()
        let push = center(client: client)
        push.removeDeliveredNotifications(forSession: 42, serverID: PushVectors.serverID)
        #expect(client.removedIdentifiers == ["usZ3J1QStv6L6omf"])
    }

    @Test func aPausedServerKeepsItsKeyAndANewKeyReplacesIt() async throws {
        let push = center()
        push.record(PushRegistration(status: .disabled, key: nil), for: PushVectors.serverID)
        #expect(push.hasKey(for: PushVectors.serverID))
        #expect(push.status(for: PushVectors.serverID) == .disabled)
        #expect(push.isRegistered(PushVectors.serverID))
        push.open(PushVectors.userInfo())
        #expect(push.openRequest?.destination == .session(PushVectors.target))

        push.record(PushRegistration(status: .active, key: Data(repeating: 4, count: 32)), for: PushVectors.serverID)
        push.open(PushVectors.userInfo())
        #expect(push.openRequest?.destination == .home)
    }

    @Test func unregisteringDropsTheKeyAndTheRegistration() {
        let push = center()
        push.record(PushRegistration(status: .active, key: PushVectors.keyBytes), for: PushVectors.serverID)
        push.recordUnregistered(serverID: PushVectors.serverID)
        #expect(!push.hasKey(for: PushVectors.serverID))
        #expect(!push.isRegistered(PushVectors.serverID))
    }

    @Test func unsupportedServersAreNotRegistered() {
        let push = center()
        push.recordUnsupported(serverID: PushVectors.serverID)
        #expect(push.status(for: PushVectors.serverID) == .unsupported)
        #expect(!push.isRegistered(PushVectors.serverID))
    }

    @Test func nothingIsDecidedUntilPermissionIsKnown() async {
        let client = FakeNotificationClient()
        client.current = .notDetermined
        let push = center(client: client)
        #expect(push.pushIntent(for: PushVectors.serverID) == .undecided)
        await push.refreshAuthorization()
        #expect(push.pushIntent(for: PushVectors.serverID) == .undecided)
        #expect(await push.requestPermission())
        #expect(push.pushIntent(for: PushVectors.serverID) == .wanted)
        #expect(client.remoteRegistrations == 1)
        await push.setEnabled(false, for: PushVectors.serverID)
        #expect(push.pushIntent(for: PushVectors.serverID) == .unwanted)
    }

    @Test func aDeniedPermissionIsUnwanted() async {
        let client = FakeNotificationClient()
        client.current = .denied
        let push = center(client: client)
        await push.refreshAuthorization()
        #expect(push.pushIntent(for: PushVectors.serverID) == .unwanted)
    }

    @Test func pairedServersAreTrackedAndRemovedServersForgotten() {
        let push = center()
        push.syncServers([Fixtures.credential(id: "laptop", name: "Laptop")])
        #expect(push.servers.map(\.serverId) == ["laptop"])
        #expect(!push.hasKey(for: PushVectors.serverID))
    }

    @Test func tokensAreSentAsLowercaseHex() {
        let push = center()
        push.didRegister(deviceToken: Data([0xAB, 0x01, 0xFF]))
        #expect(push.deviceToken == "ab01ff")
    }
}
