import Foundation
import MuxyMobile
import MuxyPush
import Testing
@testable import Muxy

@MainActor
struct SessionOpenerTests {
    private let size = TerminalGridSize(columns: 50, rows: 20)
    private let target = PushTarget(serverId: "server-1", sessionId: 42, projectId: "muxy", eventId: 7)

    private func connected(_ connections: FakeServerConnection..., context: FakePushContext? = nil) async
        -> (ServerController, FakeServerConnector) {
        let connector = FakeServerConnector(outcomes: connections.map { .success($0) })
        let controller = ServerController(
            serverID: "server-1",
            serverName: "Studio",
            connector: connector,
            credentialProvider: { Fixtures.credential() },
            pushContext: context ?? FakePushContext(),
            schedule: ReconnectSchedule(initialDelays: [.milliseconds(5)], steadyDelay: .milliseconds(5), afterRestart: .milliseconds(5))
        )
        controller.setWantsConnection(true)
        return (controller, connector)
    }

    @Test func anOpenTabForTheSessionIsSelected() async throws {
        let connection = FakeServerConnection(sessions: [Fixtures.session(41), Fixtures.session(42)])
        let (controller, _) = await connected(connection)
        let model = controller.projectModel(for: "muxy")
        model.setVisible(true)
        await PushTestSupport.settle { model.tabs.count == 2 }
        let existing = try #require(model.tabs.first { $0.sessionID == 42 })

        let opening = await controller.openSession(target)

        #expect(opening == .opened(projectID: "muxy", tabID: existing.id))
        #expect(model.selectedTabID == existing.id)
        #expect(model.tabs.count == 2)
    }

    @Test func theSessionIsAttachedWithoutStartingAShell() async throws {
        let connection = FakeServerConnection(sessions: [Fixtures.session(42)])
        let (controller, _) = await connected(connection)

        let opening = await controller.openSession(target)

        let model = controller.projectModel(for: "muxy")
        let tab = try #require(model.selectedTab)
        #expect(opening == .opened(projectID: "muxy", tabID: tab.id))
        #expect(tab.sessionID == 42)
        tab.viewportDidChange(size)
        model.setVisible(true)
        await PushTestSupport.settle { tab.isLive }
        #expect(connection.attached == [42])
        #expect(connection.created.isEmpty)
    }

    @Test func aSessionThatMovedOpensInItsCurrentProject() async {
        let connection = FakeServerConnection(sessions: [Fixtures.session(42, project: "home")])
        let (controller, _) = await connected(connection)

        let opening = await controller.openSession(target)

        #expect(opening?.projectID == "home")
        #expect(controller.projectModel(for: "home").selectedTab?.sessionID == 42)
        #expect(controller.projectModel(for: "muxy").tabs.isEmpty)
    }

    @Test func anEndedSessionOpensTheProjectAndIsAcknowledged() async {
        let context = FakePushContext()
        let connection = FakeServerConnection()
        let (controller, _) = await connected(connection, context: context)

        let opening = await controller.openSession(target)

        #expect(opening == .ended(projectID: "muxy"))
        await PushTestSupport.settle { connection.acknowledged == [7] }
        #expect(connection.acknowledged == [7])
        #expect(context.removedSessions == [42])
    }

    @Test func theEventIsAcknowledgedOnceThePaneIsLive() async throws {
        let context = FakePushContext()
        let connection = FakeServerConnection(sessions: [Fixtures.session(42)])
        let (controller, _) = await connected(connection, context: context)

        _ = await controller.openSession(target)
        #expect(connection.acknowledged.isEmpty)

        let model = controller.projectModel(for: "muxy")
        let tab = try #require(model.selectedTab)
        tab.viewportDidChange(size)
        model.setVisible(true)
        await PushTestSupport.settle { connection.acknowledged == [7] }
        #expect(connection.acknowledged == [7])
        #expect(context.removedSessions == [42])
    }

    @Test func aDroppedConnectionIsRetriedOnceReconnected() async {
        let first = FakeServerConnection(sessions: [Fixtures.session(42)])
        first.failNextFind(with: .Disconnected)
        let second = FakeServerConnection(sessions: [Fixtures.session(42)])
        let (controller, connector) = await connected(first, second)

        let opening = Task { await controller.openSession(target) }
        await PushTestSupport.settle { controller.phase == .connected }
        try? await Task.sleep(for: .milliseconds(20))
        connector.emit(.disconnected)

        #expect(await opening.value?.projectID == "muxy")
        #expect(connector.connectCount == 2)
    }

    @Test func openingWaitsForTheServerToConnect() async {
        let connection = FakeServerConnection(sessions: [Fixtures.session(42)])
        let connector = FakeServerConnector(outcomes: [.failure(.Unreachable(reason: "asleep")), .success(connection)])
        let controller = ServerController(
            serverID: "server-1",
            serverName: "Studio",
            connector: connector,
            credentialProvider: { Fixtures.credential() },
            schedule: ReconnectSchedule(initialDelays: [.milliseconds(5)], steadyDelay: .milliseconds(5), afterRestart: .milliseconds(5))
        )
        let opening = Task { await controller.openSession(target) }
        controller.setWantsConnection(true)

        #expect(await opening.value?.projectID == "muxy")
        #expect(connector.connectCount == 2)
    }

    @Test func leavingStopsTheOpen() async {
        let connector = FakeServerConnector(outcomes: [])
        let controller = ServerController(
            serverID: "server-1",
            serverName: "Studio",
            connector: connector,
            credentialProvider: { Fixtures.credential() }
        )
        let opening = Task { await controller.openSession(target) }
        try? await Task.sleep(for: .milliseconds(20))
        opening.cancel()
        #expect(await opening.value == nil)
    }
}
