import Foundation
import OSLog

@MainActor
final class FileWatches {
    private struct Subscriber {
        let projectID: String
        let continuation: AsyncStream<[String]>.Continuation
    }

    weak var server: ServerController?

    private var subscribers: [UUID: Subscriber] = [:]

    func changes(in projectID: String) -> AsyncStream<[String]> {
        let (stream, continuation) = AsyncStream.makeStream(of: [String].self)
        let id = UUID()
        let isFirstSubscriber = !isWatched(projectID)
        subscribers[id] = Subscriber(projectID: projectID, continuation: continuation)
        continuation.onTermination = { [weak self] _ in
            Task { @MainActor [weak self] in self?.unsubscribe(id) }
        }
        if isFirstSubscriber {
            update(projectID, watching: true)
        }
        return stream
    }

    func connectionDidOpen() {
        for projectID in Set(subscribers.values.map(\.projectID)) {
            update(projectID, watching: true)
        }
    }

    func deliver(_ paths: [String], in projectID: String) {
        for subscriber in subscribers.values where subscriber.projectID == projectID {
            subscriber.continuation.yield(paths)
        }
    }

    private func unsubscribe(_ id: UUID) {
        guard let subscriber = subscribers.removeValue(forKey: id), !isWatched(subscriber.projectID) else { return }
        update(subscriber.projectID, watching: false)
    }

    private func isWatched(_ projectID: String) -> Bool {
        subscribers.values.contains { $0.projectID == projectID }
    }

    private func update(_ projectID: String, watching: Bool) {
        guard let connection = server?.connection else { return }
        do {
            let files = try connection.files(projectId: projectID)
            if watching {
                files.watch()
            } else {
                files.unwatch()
            }
        } catch {
            Log.files.error("Updating a file watch failed: \(String(describing: ServerFailure(error)), privacy: .private)")
        }
    }
}
