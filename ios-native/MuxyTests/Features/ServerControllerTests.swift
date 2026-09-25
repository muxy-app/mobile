import Foundation
import MuxyMobile
import Testing
@testable import Muxy

@MainActor
struct ServerControllerTests {
    private let projects = [
        Fixtures.project(id: "home", name: "Home", isHome: true),
        Fixtures.project(id: "muxy", name: "muxy"),
    ]

    private func makeController(_ connector: FakeServerConnector) -> ServerController {
        ServerController(
            serverID: "server-1",
            serverName: "Studio",
            connector: connector,
            credentialProvider: { Fixtures.credential() },
            schedule: ReconnectSchedule(initialDelays: [.milliseconds(5)], steadyDelay: .milliseconds(5), afterRestart: .milliseconds(5))
        )
    }

    private func settle(_ condition: () -> Bool) async {
        for _ in 0..<400 where !condition() {
            try? await Task.sleep(for: .milliseconds(5))
        }
    }

    @Test func connectsAndLoadsTheCatalog() async {
        let connector = FakeServerConnector(outcomes: [.success(FakeServerConnection(projects: projects))])
        let controller = makeController(connector)
        controller.setWantsConnection(true)
        await settle { controller.phase == .connected && controller.hasLoadedProjects }
        #expect(controller.phase == .connected)
        #expect(controller.projects.map(\.id) == ["home", "muxy"])
    }

    @Test func failuresOnlyTheUserCanFixStopRetrying() async {
        let connector = FakeServerConnector(outcomes: [.failure(.Unauthorized), .success(FakeServerConnection())])
        let controller = makeController(connector)
        controller.setWantsConnection(true)
        await settle { controller.phase == .failed(.unauthorized) }
        try? await Task.sleep(for: .milliseconds(50))
        #expect(controller.phase == .failed(.unauthorized))
        #expect(connector.connectCount == 1)
    }

    @Test func unreachableComputersAreRetried() async {
        let connector = FakeServerConnector(outcomes: [.failure(.Unreachable(reason: "asleep")), .success(FakeServerConnection())])
        let controller = makeController(connector)
        controller.setWantsConnection(true)
        await settle { controller.phase == .connected }
        #expect(controller.phase == .connected)
        #expect(connector.connectCount == 2)
    }

    @Test func anUnexpectedDisconnectReconnects() async {
        let connector = FakeServerConnector(outcomes: [.success(FakeServerConnection()), .success(FakeServerConnection())])
        let controller = makeController(connector)
        controller.setWantsConnection(true)
        await settle { controller.phase == .connected }
        connector.emit(.disconnected)
        await settle { controller.phase == .connected && connector.connectCount == 2 }
        #expect(connector.connectCount == 2)
        #expect(controller.phase == .connected)
    }

    @Test func aServerRestartShowsReconnectingThenReconnects() async {
        let connector = FakeServerConnector(outcomes: [.success(FakeServerConnection()), .success(FakeServerConnection())])
        let controller = makeController(connector)
        controller.setWantsConnection(true)
        await settle { controller.phase == .connected }
        connector.emit(.serverRestarting)
        await settle { controller.phase == .reconnecting(.serverRestarting) }
        #expect(controller.phase == .reconnecting(.serverRestarting))
        connector.emit(.disconnected)
        await settle { controller.phase == .connected && connector.connectCount == 2 }
        #expect(connector.connectCount == 2)
    }

    @Test func eventsFromAnOldConnectionAreIgnored() async {
        let connector = FakeServerConnector(outcomes: [.success(FakeServerConnection()), .success(FakeServerConnection())])
        let controller = makeController(connector)
        controller.setWantsConnection(true)
        await settle { controller.phase == .connected }
        controller.setWantsConnection(false)
        controller.setWantsConnection(true)
        await settle { controller.phase == .connected && connector.connectCount == 2 }
        connector.emit(.disconnected, fromAttempt: 1)
        try? await Task.sleep(for: .milliseconds(50))
        #expect(controller.phase == .connected)
        #expect(connector.connectCount == 2)
    }

    @Test func leavingDisconnectsWithoutRetrying() async {
        let connection = FakeServerConnection()
        let connector = FakeServerConnector(outcomes: [.success(connection)])
        let controller = makeController(connector)
        controller.setWantsConnection(true)
        await settle { controller.phase == .connected }
        controller.setWantsConnection(false)
        try? await Task.sleep(for: .milliseconds(50))
        #expect(controller.phase == .idle)
        #expect(connection.isDisconnected)
        #expect(connector.connectCount == 1)
    }

    @Test func retryingClearsAFatalFailure() async {
        let connector = FakeServerConnector(outcomes: [.failure(.IdentityMismatch), .success(FakeServerConnection())])
        let controller = makeController(connector)
        controller.setWantsConnection(true)
        await settle { controller.phase == .failed(.identityMismatch) }
        controller.retryNow()
        await settle { controller.phase == .connected }
        #expect(controller.phase == .connected)
    }

    @Test func pairingAgainReconnectsWithTheNewCredential() async {
        let first = FakeServerConnection()
        let connector = FakeServerConnector(outcomes: [.success(first), .success(FakeServerConnection())])
        let controller = makeController(connector)
        controller.setWantsConnection(true)
        await settle { controller.phase == .connected }
        controller.credentialDidChange()
        await settle { controller.phase == .connected && connector.connectCount == 2 }
        #expect(first.isDisconnected)
        #expect(connector.connectCount == 2)
    }
}
