import Foundation
import MuxyMobile
import MuxyPush
import OSLog

nonisolated enum SessionOpening: Equatable, Sendable {
    case opened(projectID: String, tabID: UUID)
    case ended(projectID: String)
    case unavailable(projectID: String)

    var projectID: String {
        switch self {
        case let .opened(projectID, _), let .ended(projectID), let .unavailable(projectID):
            projectID
        }
    }

    var tabID: UUID? {
        guard case let .opened(_, tabID) = self else { return nil }
        return tabID
    }
}

@MainActor
final class SessionOpener {
    weak var server: ServerController?

    func open(_ target: PushTarget) async -> SessionOpening? {
        guard var retries = server?.requestRetrySchedule else { return nil }
        var stale: (any ServerConnection)?
        while !Task.isCancelled {
            guard let connection = await nextConnection(after: stale) else { return nil }
            do {
                let session = try await connection.findSession(target.sessionId)
                guard !Task.isCancelled else { return nil }
                guard let server, server.connection === connection else {
                    stale = connection
                    continue
                }
                return show(session, for: target, on: server)
            } catch {
                let failure = ServerFailure(error)
                Log.push.error("Finding a notification's session failed: \(String(describing: failure), privacy: .public)")
                guard Self.isTransient(failure) else { return .unavailable(projectID: target.projectId) }
                guard failure != .disconnected else {
                    stale = connection
                    continue
                }
                try? await Task.sleep(for: retries.nextDelay())
            }
        }
        return nil
    }

    private func show(_ session: Session?, for target: PushTarget, on server: ServerController) -> SessionOpening? {
        guard let session else {
            server.acknowledgePushEvent(target.eventId, sessionID: target.sessionId)
            return .ended(projectID: target.projectId)
        }
        guard let tab = server.projectModel(for: session.projectId).openTab(for: session) else { return nil }
        server.acknowledgeWhenShown(eventID: target.eventId, sessionID: session.id)
        return .opened(projectID: session.projectId, tabID: tab.id)
    }

    private func nextConnection(after stale: (any ServerConnection)?) async -> (any ServerConnection)? {
        guard let states = server?.connectionStates() else { return nil }
        for await isConnected in states {
            guard isConnected, let connection = server?.connection, connection !== stale else { continue }
            return connection
        }
        return nil
    }

    private static func isTransient(_ failure: ServerFailure) -> Bool {
        switch failure {
        case .disconnected, .timeout, .unreachable:
            true
        default:
            false
        }
    }
}
