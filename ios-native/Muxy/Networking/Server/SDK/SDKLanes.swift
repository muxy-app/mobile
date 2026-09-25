import Foundation
import OSLog

nonisolated final class SDKLanes: Sendable {
    private let requests: DispatchQueue
    private let input: DispatchQueue

    init(label: String) {
        requests = DispatchQueue(label: "\(label).requests", qos: .userInitiated)
        input = DispatchQueue(label: "\(label).input", qos: .userInteractive)
    }

    func request<Value: Sendable>(_ work: @escaping @Sendable () throws -> Value) async throws -> Value {
        try await withCheckedThrowingContinuation { continuation in
            requests.async {
                continuation.resume(with: Result { try work() })
            }
        }
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
}
