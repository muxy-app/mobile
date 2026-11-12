import Foundation
import Testing
@testable import Muxy

@MainActor
struct TerminalFitTests {
    private let phone = TerminalGridSize(columns: 50, rows: 20)
    private let rotated = TerminalGridSize(columns: 60, rows: 20)

    private let server = ServerController(
        serverID: "server-1",
        serverName: "Studio",
        connector: FakeServerConnector(outcomes: []),
        credentialProvider: { Fixtures.credential() }
    )

    private func desktopSized() -> FakeTerminalChannel {
        FakeTerminalChannel(sessionId: 1, screen: Fixtures.screen(columns: 120, rows: 40))
    }

    private func attached(_ channel: FakeTerminalChannel) -> TerminalController {
        let controller = TerminalController(projectID: "muxy", sessionID: 1, server: server)
        controller.viewportDidChange(phone)
        controller.didAttach(channel, size: phone)
        return controller
    }

    private func settle(_ condition: () -> Bool) async {
        for _ in 0..<400 where !condition() {
            try? await Task.sleep(for: .milliseconds(5))
        }
    }

    private func pastDebounce() async {
        try? await Task.sleep(for: .milliseconds(250))
    }

    @Test func attachingToADesktopSizedSessionKeepsItsSize() async {
        let channel = desktopSized()
        let controller = attached(channel)
        controller.viewportDidChange(rotated)
        await pastDebounce()
        #expect(channel.resizes.isEmpty)
        #expect(!controller.isFitted)
        #expect(controller.canFitToPhone)
    }

    @Test func attachingAtThePhoneSizeCountsAsFitted() {
        let channel = FakeTerminalChannel(sessionId: 1, screen: Fixtures.screen(columns: 50, rows: 20))
        let controller = attached(channel)
        #expect(controller.isFitted)
        #expect(!controller.canFitToPhone)
    }

    @Test func fittingToThePhoneResizesAndFollowsTheViewport() async {
        let channel = desktopSized()
        let controller = attached(channel)
        controller.fitToPhone()
        await settle { controller.isFitted }
        #expect(channel.resizes == [phone])
        #expect(!controller.canFitToPhone)

        controller.viewportDidChange(rotated)
        await settle { channel.resizes.count == 2 }
        #expect(channel.resizes == [phone, rotated])
    }

    @Test func aFailedFitShowsANoticeAndCanBeTriedAgain() async {
        let channel = desktopSized()
        channel.failNextResize()
        let controller = attached(channel)
        controller.fitToPhone()
        await settle { controller.notice != nil }
        #expect(controller.notice == TerminalController.fitFailedNotice)
        #expect(!controller.isFitted)
        #expect(controller.canFitToPhone)

        controller.fitToPhone()
        await settle { controller.isFitted }
        #expect(channel.resizes == [phone, phone])
    }

    @Test func theDesktopTakingTheSizeBackKeepsTheFitButtonHidden() async {
        let channel = desktopSized()
        let controller = attached(channel)
        controller.fitToPhone()
        await settle { controller.isFitted }
        channel.update(screen: Fixtures.screen(columns: 120, rows: 40))
        _ = controller.currentScreen()
        #expect(!controller.canFitToPhone)
        #expect(channel.resizes == [phone])
    }

    @Test func aFittedTerminalIsFittedAgainWhenReattached() async {
        let controller = attached(desktopSized())
        controller.fitToPhone()
        await settle { controller.isFitted }
        _ = controller.releaseChannel()

        let second = desktopSized()
        controller.didAttach(second, size: phone)
        await settle { !second.resizes.isEmpty }
        #expect(second.resizes == [phone])
        #expect(!controller.canFitToPhone)
    }

    @Test func aReleasedTerminalCannotBeFitted() {
        let controller = attached(desktopSized())
        _ = controller.releaseChannel()
        #expect(!controller.canFitToPhone)
    }
}
