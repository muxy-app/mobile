import Foundation
import OSLog

nonisolated final class SDKLanes: Sendable {
    private let requests: DispatchQueue
    private let input: DispatchQueue
    private let files: DispatchQueue
    private let git: DispatchQueue

    init(label: String) {
        requests = DispatchQueue(label: "\(label).requests", qos: .userInitiated)
        input = DispatchQueue(label: "\(label).input", qos: .userInteractive)
        files = DispatchQueue(label: "\(label).files", qos: .userInitiated)
        git = DispatchQueue(label: "\(label).git", qos: .userInitiated)
    }

    func request<Value: Sendable>(_ work: @escaping @Sendable () throws -> Value) async throws -> Value {
        try await perform(work, on: requests)
    }

    func fileRequest<Value: Sendable>(_ work: @escaping @Sendable () throws -> Value) async throws -> Value {
        try await perform(work, on: files)
    }

    func gitRequest<Value: Sendable>(_ work: @escaping @Sendable () throws -> Value) async throws -> Value {
        try await perform(work, on: git)
    }

    func send(_ work: @escaping @Sendable () throws -> Void) {
        input.async {
            do {
                try work()
            } catch {
                Log.terminal.error("Terminal input failed: \(String(describing: ServerFailure(error)), privacy: .public)")
            }
        }
    }

    func enqueueFileWork(_ work: @escaping @Sendable () throws -> Void) {
        files.async {
            do {
                try work()
            } catch {
                Log.files.error("File watch update failed: \(String(describing: ServerFailure(error)), privacy: .private)")
            }
        }
    }

    private func perform<Value: Sendable>(
        _ work: @escaping @Sendable () throws -> Value,
        on queue: DispatchQueue
    ) async throws -> Value {
        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                continuation.resume(with: Result { try work() })
            }
        }
    }
}
