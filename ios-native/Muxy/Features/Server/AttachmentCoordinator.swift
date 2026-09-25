import Foundation
import MuxyMobile
import OSLog

@MainActor
final class AttachmentCoordinator {
    weak var server: ServerController?

    private weak var desired: TerminalController?
    private var attached: TerminalController?
    private var isBusy = false
    private var isInterrupted = false

    func show(_ terminal: TerminalController?) {
        desired = terminal
        pump()
    }

    func connectionDidOpen() {
        pump()
    }

    func connectionDidClose() {
        attached = nil
        isInterrupted = false
    }

    func viewportDidChange(of terminal: TerminalController) {
        guard terminal === desired else { return }
        pump()
    }

    func retire(_ terminal: TerminalController) {
        if desired === terminal {
            desired = nil
        }
        pump()
    }

    private func pump() {
        guard !isBusy, !isInterrupted, let connection = server?.connection else { return }
        if let attached, attached !== desired {
            isBusy = true
            Task {
                await detach(attached)
                finish()
            }
            return
        }
        guard let desired, desired !== attached, desired.canAttach, let size = desired.viewportSize else { return }
        isBusy = true
        Task {
            await attach(desired, size: size, connection: connection)
            finish()
        }
    }

    private func finish() {
        isBusy = false
        pump()
    }

    private func detach(_ terminal: TerminalController) async {
        attached = nil
        let isEnded = terminal.phase == .ended
        guard let channel = terminal.releaseChannel(), !isEnded else { return }
        do {
            try await channel.detach()
        } catch {
            Log.terminal.error("Detach failed: \(String(describing: ServerFailure(error)), privacy: .public)")
        }
    }

    private func attach(_ terminal: TerminalController, size: TerminalGridSize, connection: any ServerConnection) async {
        do {
            let sessionID = try await sessionID(for: terminal, size: size, connection: connection)
            guard connection === server?.connection, !terminal.isRetired else {
                terminal.attachWasInterrupted()
                return
            }
            terminal.markAttaching()
            let channel = try await connection.attach(sessionId: sessionID, size: size)
            guard connection === server?.connection else {
                terminal.attachWasInterrupted()
                return
            }
            terminal.didAttach(channel)
            attached = terminal
        } catch {
            guard connection === server?.connection else {
                terminal.attachWasInterrupted()
                return
            }
            handleAttachFailure(ServerFailure(error), for: terminal)
        }
    }

    private func handleAttachFailure(_ failure: ServerFailure, for terminal: TerminalController) {
        Log.terminal.error("Attach failed: \(String(describing: failure), privacy: .public)")
        guard failure != .disconnected else {
            isInterrupted = true
            terminal.attachWasInterrupted()
            return
        }
        terminal.attachDidFail(failure)
        server?.attachDidFail(for: terminal)
    }

    private func sessionID(
        for terminal: TerminalController,
        size: TerminalGridSize,
        connection: any ServerConnection
    ) async throws -> UInt64 {
        if let sessionID = terminal.sessionID { return sessionID }
        terminal.markCreating()
        let session = try await connection.createSession(projectId: terminal.projectID, size: size)
        guard !terminal.isRetired else {
            await endAbandoned(session.id, on: connection)
            return session.id
        }
        server?.register(terminal, sessionID: session.id)
        return session.id
    }

    private func endAbandoned(_ sessionID: UInt64, on connection: any ServerConnection) async {
        do {
            try await connection.endSession(sessionID)
        } catch {
            Log.terminal.error("Ending an abandoned session failed: \(String(describing: ServerFailure(error)), privacy: .public)")
        }
    }
}
