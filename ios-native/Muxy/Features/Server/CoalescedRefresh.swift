import Foundation

@MainActor
final class CoalescedRefresh {
    private var task: Task<Void, Never>?
    private var pendingAction: (@MainActor () async -> Void)?
    private var token = 0

    func request(_ action: @escaping @MainActor () async -> Void) {
        pendingAction = action
        guard task == nil else { return }
        token += 1
        let current = token
        task = Task { [weak self] in
            await self?.drain()
            self?.finish(current)
        }
    }

    func cancel() {
        task?.cancel()
        task = nil
        pendingAction = nil
        token += 1
    }

    private func drain() async {
        while let action = pendingAction, !Task.isCancelled {
            pendingAction = nil
            await action()
        }
    }

    private func finish(_ finished: Int) {
        guard finished == token else { return }
        task = nil
    }
}
