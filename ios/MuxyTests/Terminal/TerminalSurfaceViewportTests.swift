import Testing
import UIKit
@testable import Muxy

@MainActor
struct TerminalSurfaceViewportTests {
    private let server = ServerController(
        serverID: "server-1",
        serverName: "Studio",
        connector: FakeServerConnector(outcomes: []),
        credentialProvider: { Fixtures.credential() }
    )

    @Test func layoutChangesDoNotResizeTheLockedTerminal() async throws {
        let controller = TerminalController(projectID: "muxy", sessionID: 1, server: server)
        let surface = TerminalSurfaceView(controller: controller, theme: .muxy, useNerdFont: false)
        surface.frame = CGRect(x: 0, y: 0, width: 390, height: 600)
        surface.setNeedsLayout()
        surface.layoutIfNeeded()
        await withCheckedContinuation { continuation in
            DispatchQueue.main.async { continuation.resume() }
        }
        surface.layoutIfNeeded()

        let scrollView = try #require(surface.subviews.compactMap { $0 as? UIScrollView }.first)
        let size = try #require(controller.viewportSize)
        #expect(scrollView.bounds.size == CGSize(width: 390, height: 600))

        surface.frame.size.height = 300
        surface.setNeedsLayout()
        surface.layoutIfNeeded()
        #expect(scrollView.bounds.size == CGSize(width: 390, height: 600))
        #expect(controller.viewportSize == size)

        surface.frame.size.height = 600
        surface.setNeedsLayout()
        surface.layoutIfNeeded()
        #expect(scrollView.bounds.size == CGSize(width: 390, height: 600))
        #expect(controller.viewportSize == size)
    }

    @Test func returningToLivePreparesTheViewportBeforeRefreshing() {
        expectViewportPreparation { $0.returnToLive() }
    }

    @Test func typingPreparesTheViewportBeforeRefreshing() {
        expectViewportPreparation { $0.sendText("hello") }
    }

    @Test func pressingAKeyPreparesTheViewportBeforeRefreshing() {
        expectViewportPreparation { $0.send(TerminalKeyStroke(.enter)) }
    }

    @Test func pastingPreparesTheViewportBeforeRefreshing() {
        expectViewportPreparation { $0.paste("hello") }
    }

    @Test func ordinaryOutputDoesNotRearmViewportFollowing() {
        let controller = TerminalController(projectID: "muxy", sessionID: 1, server: server)
        let display = ViewportDisplayRecorder()
        controller.display = display
        controller.setFollowing(false)
        controller.screenDidChange()
        #expect(display.events == [.refresh])
        #expect(!controller.isFollowing)
    }

    private func expectViewportPreparation(_ action: (TerminalController) -> Void) {
        let controller = TerminalController(projectID: "muxy", sessionID: 1, server: server)
        controller.didAttach(FakeTerminalChannel(sessionId: 1, screen: Fixtures.screen()))
        let display = ViewportDisplayRecorder()
        controller.display = display
        controller.setFollowing(false)
        action(controller)
        #expect(display.events == [.prepare, .refresh])
        #expect(controller.isFollowing)
    }
}

@MainActor
private final class ViewportDisplayRecorder: TerminalDisplay {
    enum Event {
        case prepare
        case refresh
    }

    private(set) var events: [Event] = []

    func prepareForLiveOutput() {
        events.append(.prepare)
    }

    func screenNeedsRefresh() {
        events.append(.refresh)
    }
}
