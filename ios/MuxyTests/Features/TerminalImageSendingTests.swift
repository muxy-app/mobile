import Foundation
import MuxyMobile
import Testing
import UIKit
@testable import Muxy

@MainActor
struct TerminalImageSendingTests {
    private let size = TerminalGridSize(columns: 50, rows: 20)

    private let png: TerminalImage = {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 4, height: 4)).image { context in
            context.fill(CGRect(x: 0, y: 0, width: 4, height: 4))
        }
        return TerminalImage(data: image.pngData()!)!
    }()

    private func settle(_ condition: () -> Bool) async {
        for _ in 0..<400 where !condition() {
            try? await Task.sleep(for: .milliseconds(5))
        }
    }

    private struct LiveTerminal {
        let server: ServerController
        let terminal: TerminalController
        let channel: FakeTerminalChannel
    }

    private func liveTerminal() async throws -> LiveTerminal {
        let connection = FakeServerConnection(sessions: [Fixtures.session(1)])
        let server = ServerController(
            serverID: "server-1",
            serverName: "Studio",
            connector: FakeServerConnector(outcomes: [.success(connection)]),
            credentialProvider: { Fixtures.credential() },
            schedule: ReconnectSchedule(initialDelays: [.milliseconds(5)], steadyDelay: .milliseconds(5), afterRestart: .milliseconds(5))
        )
        server.setWantsConnection(true)
        await settle { server.phase == .connected }
        let model = server.projectModel(for: "muxy")
        model.setVisible(true)
        await settle { !model.tabs.isEmpty }
        let tab = try #require(model.tabs.first)
        model.select(tab)
        tab.viewportDidChange(size)
        await settle { tab.isLive }
        return LiveTerminal(server: server, terminal: tab, channel: try #require(connection.channel(for: 1)))
    }

    @Test func everyImageIsUploadedThenAllPathsArePastedOnce() async throws {
        let live = try await liveTerminal()
        let photo = try #require(UIImage(systemName: "photo"))
        let jpeg = try #require(TerminalImage(image: photo))
        live.terminal.send(images: [png, jpeg])
        #expect(live.terminal.sendingImagesNotice == "Sending 2 images…")
        await settle { !live.channel.pastedPaths.isEmpty }
        #expect(live.channel.uploads == ["pasted-image.png", "pasted-image.jpg"])
        #expect(live.channel.pastedPaths == [["/tmp/muxy-uploads/1/1-pasted-image.png", "/tmp/muxy-uploads/1/2-pasted-image.jpg"]])
        #expect(live.channel.pastes.isEmpty)
        #expect(live.terminal.sendingImagesNotice == nil)
    }

    @Test func aFailedUploadExplainsWhyAndPastesNothing() async throws {
        let live = try await liveTerminal()
        live.channel.failUploads(with: .Unsupported)
        live.terminal.send(images: [png])
        await settle { live.terminal.notice != nil }
        #expect(live.terminal.notice == "Update Muxy on your phone and computer to use this.")
        #expect(live.channel.pastedPaths.isEmpty)
        #expect(live.terminal.sendingImagesNotice == nil)
    }

    @Test func unreadableImagesAreNotSent() async throws {
        let live = try await liveTerminal()
        live.terminal.send(images: [])
        #expect(live.terminal.notice == "That image can't be sent.")
        #expect(live.channel.uploads.isEmpty)
    }

    @Test func imagesOverTheLimitAreNotSent() async throws {
        let live = try await liveTerminal()
        let oversized = try #require(TerminalImage(data: png.data + Data(count: TerminalImage.maximumBytes)))
        live.terminal.send(images: [png, oversized])
        #expect(live.terminal.notice == "That image is too large to send. The limit is 100 MB.")
        #expect(live.terminal.sendingImagesNotice == nil)
        #expect(live.channel.uploads.isEmpty)
    }

    @Test func aTerminalThatIsNotLiveSendsNothing() async throws {
        let live = try await liveTerminal()
        _ = live.terminal.releaseChannel()
        live.terminal.send(images: [png])
        #expect(live.terminal.sendingImagesNotice == nil)
        #expect(live.channel.uploads.isEmpty)
    }
}
