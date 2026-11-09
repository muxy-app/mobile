import Foundation
import MuxyMobile
import Testing
@testable import Muxy

@MainActor
struct PushRegistrarTests {
    @Test func connectingRegistersTheTokenAndRecordsTheReply() async {
        let context = FakePushContext()
        let connection = FakeServerConnection()
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.statuses["server-1"] != nil }
        #expect(connection.registeredTokens == [String(repeating: "ab", count: 32)])
        #expect(context.statuses["server-1"] == .active)
        #expect(controller.supportsPushNotifications)
    }

    @Test func everyConnectRegistersAgain() async {
        let context = FakePushContext()
        let first = FakeServerConnection()
        let second = FakeServerConnection()
        let (controller, connector) = PushTestSupport.controller(first, second, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { first.registeredTokens.count == 1 }
        connector.emit(.disconnected)
        await PushTestSupport.settle { second.registeredTokens.count == 1 }
        #expect(second.registeredTokens.count == 1)
    }

    @Test func aPausedServerStaysRegisteredAndIsUnregisteredWhenTurnedOff() async {
        let context = FakePushContext()
        let connection = FakeServerConnection()
        connection.setPushRegistration(PushRegistration(status: .disabled, key: nil))
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.statuses["server-1"] == .disabled }
        #expect(context.isRegistered("server-1"))
        context.intent = .unwanted
        controller.syncPushRegistration()
        await PushTestSupport.settle { connection.unregisterCount == 1 }
        #expect(context.unregistered == ["server-1"])
    }

    @Test func aRefusedTokenAsksForAFreshToken() async {
        let context = FakePushContext()
        let connection = FakeServerConnection()
        connection.setPushRegistration(PushRegistration(status: .refused, key: Data(repeating: 2, count: 32)))
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.freshTokenRequests == 1 }
        #expect(context.freshTokenRequests == 1)
        #expect(context.statuses["server-1"] == .refused)
    }

    @Test func turningNotificationsOffUnregisters() async {
        let context = FakePushContext()
        let connection = FakeServerConnection()
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.isRegistered("server-1") }
        context.intent = .unwanted
        controller.syncPushRegistration()
        await PushTestSupport.settle { connection.unregisterCount == 1 }
        #expect(!context.isRegistered("server-1"))
    }

    @Test func turningOffWhileOfflineUnregistersOnTheNextConnect() async {
        let context = FakePushContext()
        context.intent = .unwanted
        context.registered = ["server-1"]
        let connection = FakeServerConnection()
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { connection.unregisterCount == 1 }
        #expect(connection.registeredTokens.isEmpty)
        #expect(context.unregistered == ["server-1"])
    }

    @Test func anUndecidedPermissionNeitherRegistersNorUnregisters() async {
        let context = FakePushContext()
        context.intent = .undecided
        context.registered = ["server-1"]
        let connection = FakeServerConnection()
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.syncs > 0 }
        #expect(context.syncs > 0)
        #expect(connection.registeredTokens.isEmpty)
        #expect(connection.unregisterCount == 0)
    }

    @Test func serversWithoutPushAreNeverAsked() async {
        let context = FakePushContext()
        let connection = FakeServerConnection()
        connection.setPushSupport(false)
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.unsupported.contains("server-1") }
        #expect(connection.registeredTokens.isEmpty)
        #expect(!controller.supportsPushNotifications)
    }

    @Test func nothingIsSentUntilATokenArrives() async {
        let context = FakePushContext()
        context.deviceToken = nil
        let connection = FakeServerConnection()
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.syncs > 0 }
        #expect(connection.registeredTokens.isEmpty)
        context.deviceToken = "cd"
        controller.syncPushRegistration()
        await PushTestSupport.settle { connection.registeredTokens == ["cd"] }
        #expect(connection.registeredTokens == ["cd"])
    }
}
