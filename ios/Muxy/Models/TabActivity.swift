import Foundation

nonisolated struct TabActivity: Equatable, Sendable {
    enum State: Equatable, Sendable {
        case idle
        case working
        case waiting
        case finished
    }

    static let none = TabActivity(state: .idle, provider: nil, since: nil)

    let state: State
    let provider: String?
    let since: Date?

    var isAgent: Bool {
        provider != nil
    }

    var needsAttention: Bool {
        state == .waiting || state == .finished
    }

    var agentProvider: AgentProvider? {
        provider.flatMap(AgentProvider.init(name:))
    }

    var providerName: String? {
        agentProvider?.displayName ?? provider
    }

    var statusLabel: String? {
        switch state {
        case .idle: providerName
        case .working: "Working"
        case .waiting: "Waiting for you"
        case .finished: "Finished"
        }
    }
}
