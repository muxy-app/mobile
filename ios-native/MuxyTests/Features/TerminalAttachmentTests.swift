import Foundation
import MuxyMobile
import Testing
@testable import Muxy

@MainActor
struct TerminalAttachmentTests {
    private let size = TerminalGridSize(columns: 50, rows: 20)

    private func connectedController(_ connections: FakeServerConnection...) async -> (ServerController, FakeServerConnector) {
        let connector = FakeServerConnector(outcomes: connections.map { .success($0) })
        let controller = ServerController(
            serverID: "server-1",
            serverName: "Studio",
            connector: connector,
            credentialProvider: { Fixtures.credential() },
            schedule: ReconnectSchedule(initialDelays: [.milliseconds(5)], steadyDelay: .milliseconds(5), afterRestart: .milliseconds(5))
        )
        controller.setWantsConnection(true)
        await settle { controller.phase == .connected }
        return (controller, connector)
    }

    private func settle(_ condition: () -> Bool) async {
        for _ in 0..<400 where !condition() {
            try? await Task.sleep(for: .milliseconds(5))
        }
    }

    private func visibleTab(in model: ProjectModel, session: UInt64) async -> TerminalController? {
        model.setVisible(true)
        await settle { model.tabs.contains { $0.sessionID == session } }
        guard let tab = model.tabs.first(where: { $0.sessionID == session }) else { return nil }
        model.select(tab)
        tab.viewportDidChange(size)
        return tab
    }

    @Test func onlyTheVisibleTerminalIsAttached() async throws {
        let connection = FakeServerConnection(sessions: [Fixtures.session(1), Fixtures.session(2)])
        let (controller, _) = await connectedController(connection)
        let model = controller.projectModel(for: "muxy")
        model.setVisible(true)
        await settle { model.tabs.count == 2 }
        let first = try #require(model.tabs.first)
        let second = try #require(model.tabs.last)
        second.viewportDidChange(size)
        model.select(second)
        first.viewportDidChange(size)

        await settle { second.isLive }
        #expect(connection.attached == [2])

        model.select(first)
        await settle { first.isLive && connection.channel(for: 2)?.detachCount == 1 }
        #expect(connection.attached == [2, 1])
        #expect(connection.channel(for: 2)?.detachCount == 1)
    }

    @Test func aNewTerminalIsCreatedAtTheViewportSize() async throws {
        let connection = FakeServerConnection()
        let (controller, _) = await connectedController(connection)
        let model = controller.projectModel(for: "muxy")
        model.createTab()
        let tab = try #require(model.selectedTab)
        tab.viewportDidChange(size)
        model.setVisible(true)
        await settle { tab.isLive }
        #expect(connection.created == [size])
        #expect(tab.sessionID == 900)
    }

    @Test func anEndedSessionClosesItsTab() async throws {
        let connection = FakeServerConnection(sessions: [Fixtures.session(1)])
        let (controller, connector) = await connectedController(connection)
        let model = controller.projectModel(for: "muxy")
        let tab = try #require(await visibleTab(in: model, session: 1))
        await settle { tab.isLive }
        connector.emit(.sessionEnded(sessionId: 1))
        await settle { model.tabs.isEmpty }
        #expect(model.tabs.isEmpty)
    }

    @Test func closingEndsTheSessionOnTheComputer() async throws {
        let connection = FakeServerConnection(sessions: [Fixtures.session(1)])
        let (controller, _) = await connectedController(connection)
        let model = controller.projectModel(for: "muxy")
        let tab = try #require(await visibleTab(in: model, session: 1))
        await settle { tab.isLive }
        model.close(tab)
        await settle { connection.ended == [1] }
        #expect(connection.ended == [1])
        #expect(model.tabs.isEmpty)
    }

    @Test func aReconnectAttachesTheVisibleTerminalAgain() async throws {
        let first = FakeServerConnection(sessions: [Fixtures.session(1)])
        let second = FakeServerConnection(sessions: [Fixtures.session(1)])
        let (controller, connector) = await connectedController(first, second)
        let model = controller.projectModel(for: "muxy")
        let tab = try #require(await visibleTab(in: model, session: 1))
        await settle { tab.isLive }
        connector.emit(.disconnected)
        await settle { second.attached == [1] && tab.isLive }
        #expect(second.attached == [1])
        #expect(tab.isLive)
    }

    @Test func aProjectRemovedOnTheComputerClosesItsTabs() async throws {
        let first = FakeServerConnection(sessions: [Fixtures.session(1)])
        let second = FakeServerConnection(projects: [Fixtures.project(id: "home", name: "Home", isHome: true)])
        let (controller, connector) = await connectedController(first, second)
        let model = controller.projectModel(for: "muxy")
        let tab = try #require(await visibleTab(in: model, session: 1))
        await settle { tab.isLive }
        connector.emit(.disconnected)
        await settle { model.tabs.isEmpty }
        #expect(model.tabs.isEmpty)
        #expect(controller.project(for: "muxy") == nil)
    }

    @Test func anAttachInterruptedByADisconnectIsRetriedAfterReconnect() async throws {
        let first = FakeServerConnection(sessions: [Fixtures.session(1)], attachFailures: [1: .Disconnected])
        let second = FakeServerConnection(sessions: [Fixtures.session(1)])
        let (controller, connector) = await connectedController(first, second)
        let model = controller.projectModel(for: "muxy")
        let tab = try #require(await visibleTab(in: model, session: 1))
        await settle { tab.phase == .waiting && first.attached.isEmpty }
        connector.emit(.disconnected)
        await settle { tab.isLive }
        #expect(second.attached == [1])
        #expect(tab.phase == .live)
    }

    @Test func aSessionThatNoLongerExistsIsRemovedAfterAFailedAttach() async throws {
        let connection = FakeServerConnection(sessions: [Fixtures.session(5)], attachFailures: [5: .Server(reason: "session 5 does not exist")])
        let (controller, _) = await connectedController(connection)
        let model = controller.projectModel(for: "muxy")
        model.setVisible(true)
        await settle { model.tabs.count == 1 }
        try await connection.endSession(5)
        let tab = try #require(model.tabs.first)
        tab.viewportDidChange(size)
        await settle { model.tabs.isEmpty }
        #expect(model.tabs.isEmpty)
    }

    @Test func inputIsTranslatedForTheTerminal() async throws {
        let connection = FakeServerConnection(sessions: [Fixtures.session(1)])
        let (controller, _) = await connectedController(connection)
        let model = controller.projectModel(for: "muxy")
        let tab = try #require(await visibleTab(in: model, session: 1))
        await settle { tab.isLive }

        tab.sendText("ls")
        tab.sendText("\n")
        tab.sendText("a\nb")
        tab.setModifierArmed(true)
        tab.sendText("c")
        tab.send(TerminalKeyStroke(.up))

        let channel = try #require(connection.channel(for: 1))
        #expect(channel.sent == [
            "text:ls",
            "key:enter",
            "text:a\rb",
            "key:ctrl+character(text: \"c\")",
            "key:up",
        ])
        #expect(!tab.modifierArmed)
    }

    @Test func pastesOverTheLimitAreRefused() async throws {
        let connection = FakeServerConnection(sessions: [Fixtures.session(1)])
        let (controller, _) = await connectedController(connection)
        let model = controller.projectModel(for: "muxy")
        let tab = try #require(await visibleTab(in: model, session: 1))
        await settle { tab.isLive }

        tab.paste(String(repeating: "x", count: TerminalController.maximumInputBytes))
        tab.paste("echo ok")

        #expect(connection.channel(for: 1)?.pastes == ["echo ok"])
        #expect(tab.notice != nil)
    }

    @Test func theTitleFollowsTheProgram() async throws {
        let connection = FakeServerConnection(sessions: [Fixtures.session(1)])
        let (controller, _) = await connectedController(connection)
        let model = controller.projectModel(for: "muxy")
        let tab = try #require(await visibleTab(in: model, session: 1))
        #expect(tab.title == "Terminal 1")
        await settle { tab.isLive }
        #expect(tab.title == "shell 1")
    }
}
