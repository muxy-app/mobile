import Foundation
import MuxyMobile
import Testing
@testable import Muxy

@MainActor
struct PushRegistrarTests {
    @Test func connectingRegistersTheTokenAndKeepsTheKey() async {
        let context = FakePushContext()
        let connection = FakeServerConnection()
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.keys["server-1"] != nil }
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

    @Test func aDisabledServerDropsTheKey() async {
        let context = FakePushContext()
        context.keys["server-1"] = Data(repeating: 1, count: 32)
        let connection = FakeServerConnection()
        connection.setPushRegistration(PushRegistration(status: .disabled, key: nil))
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.statuses["server-1"] == .disabled }
        #expect(context.keys["server-1"] == nil)
    }

    @Test func aRefusedTokenKeepsTheKeyAndAsksForAFreshToken() async {
        let context = FakePushContext()
        let connection = FakeServerConnection()
        connection.setPushRegistration(PushRegistration(status: .refused, key: Data(repeating: 2, count: 32)))
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.freshTokenRequests == 1 }
        #expect(context.keys["server-1"] == Data(repeating: 2, count: 32))
        #expect(context.statuses["server-1"] == .refused)
    }

    @Test func turningNotificationsOffUnregisters() async {
        let context = FakePushContext()
        let connection = FakeServerConnection()
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { context.keys["server-1"] != nil }
        context.wants = false
        controller.syncPushRegistration()
        await PushTestSupport.settle { connection.unregisterCount == 1 }
        #expect(context.keys["server-1"] == nil)
    }

    @Test func turningOffWhileOfflineUnregistersOnTheNextConnect() async {
        let context = FakePushContext()
        context.wants = false
        context.keys["server-1"] = Data(repeating: 1, count: 32)
        let connection = FakeServerConnection()
        let (controller, _) = PushTestSupport.controller(connection, context: context)
        controller.setWantsConnection(true)
        await PushTestSupport.settle { connection.unregisterCount == 1 }
        #expect(connection.registeredTokens.isEmpty)
        #expect(context.keys["server-1"] == nil)
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
        await PushTestSupport.settle { controller.phase == .connected }
        try? await Task.sleep(for: .milliseconds(30))
        #expect(connection.registeredTokens.isEmpty)
        context.deviceToken = "cd"
        controller.syncPushRegistration()
        await PushTestSupport.settle { connection.registeredTokens == ["cd"] }
        #expect(connection.registeredTokens == ["cd"])
    }
}
