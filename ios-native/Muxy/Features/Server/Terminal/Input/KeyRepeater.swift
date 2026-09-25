import Foundation

final class KeyRepeater {
    private static let initialDelay: Duration = .milliseconds(400)
    private static let interval: Duration = .milliseconds(80)

    private var task: Task<Void, Never>?

    func start(_ action: @escaping @MainActor () -> Void) {
        stop()
        task = Task {
            try? await Task.sleep(for: Self.initialDelay)
            while !Task.isCancelled {
                action()
                try? await Task.sleep(for: Self.interval)
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
    }
}
